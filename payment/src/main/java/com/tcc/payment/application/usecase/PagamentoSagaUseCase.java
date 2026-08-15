package com.tcc.payment.application.usecase;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tcc.payment.domain.entity.Pagamento;
import com.tcc.payment.domain.repository.PagamentoRepository;
import com.tcc.saga.event.CanaisSaga;
import com.tcc.saga.event.CompensacaoPagamentoConcluida;
import com.tcc.saga.event.EtapaSaga;
import com.tcc.saga.event.HoldHotelCriado;
import com.tcc.saga.event.HoldVooCriado;
import com.tcc.saga.event.PagamentoHotelCancelado;
import com.tcc.saga.event.PagamentoVooCancelado;
import com.tcc.saga.event.PagamentoVooConfirmado;
import com.tcc.saga.event.SagaAuditEvento;
import com.tcc.saga.event.SagaFalhou;
import com.tcc.saga.experimento.AuditoriaPublisher;
import com.tcc.saga.recuperacao.MotorRecuperacao;
import com.tcc.saga.recuperacao.PayloadEtapaMapper;

import io.eventuate.tram.events.publisher.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Participação do payment na saga (T5, T6 e suas compensações).
 *
 * O payment é o primeiro elo da cadeia backward: reage diretamente a
 * {@link SagaFalhou}, compensa T6 e T5 nessa ordem e passa a cadeia ao booking.
 * Ele repassa a cadeia mesmo quando não há pagamento algum a estornar — é o que
 * permite que uma falha em T2 ou T4 percorra sempre o mesmo caminho de volta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PagamentoSagaUseCase {

    private final PagamentoRepository pagamentoRepository;
    private final PagamentoUseCase pagamentoUseCase;
    private final DomainEventPublisher eventPublisher;
    private final AuditoriaPublisher auditoria;
    private final MotorRecuperacao motorRecuperacao;
    private final PayloadEtapaMapper payloadMapper;

    /**
     * T5 — pagamento do voo, primeira etapa depois do ponto de pivô.
     * Não é ponto de injeção de falha, então executa direto no handler.
     */
    @Transactional
    public void aoHoldVooCriado(HoldVooCriado evento) {
        Long solicitacaoId = evento.getSolicitacaoId();

        if (pagamentoRepository.obterPorSolicitacaoIdETipo(solicitacaoId, Pagamento.TipoPagamento.VOO).isPresent()) {
            return; // reentrega
        }

        auditoria.registrar(solicitacaoId, EtapaSaga.T5, SagaAuditEvento.AcaoAuditoria.INICIO);

        Pagamento pagamento = pagamentoUseCase.criarEConfirmar(solicitacaoId, Pagamento.TipoPagamento.VOO,
                evento.getReferencia(), evento.getValor());

        eventPublisher.publish(CanaisSaga.PAGAMENTO, solicitacaoId, List.of(
                PagamentoVooConfirmado.builder()
                        .solicitacaoId(solicitacaoId)
                        .pagamentoId(pagamento.getId())
                        .valor(pagamento.getValor())
                        .build()));

        auditoria.registrar(solicitacaoId, EtapaSaga.T5, SagaAuditEvento.AcaoAuditoria.SUCESSO);
    }

    /**
     * T6 — pagamento do hotel, a posição tardia de falha do experimento:
     * vai pelo motor de recuperação, onde a falha pode ser injetada.
     */
    @Transactional
    public void aoHoldHotelCriado(HoldHotelCriado evento) {
        motorRecuperacao.enfileirar(evento.getSolicitacaoId(), EtapaSaga.T6,
                payloadMapper.serializar(evento), 0L);
    }

    /**
     * Primeiro elo da cadeia backward: compensa T6 e T5, nesta ordem.
     */
    @Transactional
    public void aoSagaFalhou(SagaFalhou evento) {
        Long solicitacaoId = evento.getSolicitacaoId();

        cancelar(solicitacaoId, Pagamento.TipoPagamento.HOTEL, EtapaSaga.T6).ifPresent(pagamento ->
                eventPublisher.publish(CanaisSaga.PAGAMENTO, solicitacaoId, List.of(
                        PagamentoHotelCancelado.builder()
                                .solicitacaoId(solicitacaoId)
                                .pagamentoId(pagamento.getId())
                                .build())));

        cancelar(solicitacaoId, Pagamento.TipoPagamento.VOO, EtapaSaga.T5).ifPresent(pagamento ->
                eventPublisher.publish(CanaisSaga.PAGAMENTO, solicitacaoId, List.of(
                        PagamentoVooCancelado.builder()
                                .solicitacaoId(solicitacaoId)
                                .pagamentoId(pagamento.getId())
                                .build())));

        eventPublisher.publish(CanaisSaga.SAGA, solicitacaoId, List.of(
                CompensacaoPagamentoConcluida.builder()
                        .solicitacaoId(solicitacaoId)
                        .etapaOrigemFalha(evento.getEtapa())
                        .build()));
    }

    private Optional<Pagamento> cancelar(Long solicitacaoId, Pagamento.TipoPagamento tipo, EtapaSaga etapa) {
        return pagamentoRepository.obterPorSolicitacaoIdETipo(solicitacaoId, tipo)
                .filter(Pagamento::cancelar)
                .map(pagamento -> {
                    Pagamento salvo = pagamentoRepository.salvar(pagamento);
                    auditoria.registrar(solicitacaoId, etapa, SagaAuditEvento.AcaoAuditoria.COMPENSACAO);
                    return salvo;
                });
    }
}
