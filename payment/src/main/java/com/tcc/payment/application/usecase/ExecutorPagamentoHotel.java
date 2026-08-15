package com.tcc.payment.application.usecase;

import java.util.List;

import org.springframework.stereotype.Component;

import com.tcc.payment.domain.entity.Pagamento;
import com.tcc.payment.domain.repository.PagamentoRepository;
import com.tcc.saga.event.CanaisSaga;
import com.tcc.saga.event.EtapaSaga;
import com.tcc.saga.event.HoldHotelCriado;
import com.tcc.saga.event.PagamentoHotelConfirmado;
import com.tcc.saga.event.SagaAbortada;
import com.tcc.saga.event.SagaFalhou;
import com.tcc.saga.experimento.InjetorFalha;
import com.tcc.saga.recuperacao.EtapaPendenteJpaEntity;
import com.tcc.saga.recuperacao.ExecutorEtapa;
import com.tcc.saga.recuperacao.PayloadEtapaMapper;

import io.eventuate.tram.events.publisher.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * T6 — pagamento do hotel, a posição tardia de falha do experimento.
 *
 * É o caso mais caro de compensar: T5 já foi pago, então a cadeia backward
 * precisa estornar um pagamento real. É aqui que a diferença entre desfazer
 * tudo (Backward) e insistir (Forward) tem o maior impacto prático.
 *
 * <p>Quando a falha é permanente, o pagamento é registrado com status FALHOU
 * antes de encerrar — é o caminho que produz um pagamento efetivamente falho,
 * usando o {@code falhar()} que a entidade já modelava.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutorPagamentoHotel implements ExecutorEtapa {

    private final PagamentoRepository pagamentoRepository;
    private final PagamentoUseCase pagamentoUseCase;
    private final DomainEventPublisher eventPublisher;
    private final InjetorFalha injetorFalha;
    private final PayloadEtapaMapper payloadMapper;

    @Override
    public EtapaSaga etapa() {
        return EtapaSaga.T6;
    }

    @Override
    public void executar(EtapaPendenteJpaEntity pendente, int tentativa) {
        Long solicitacaoId = pendente.getSolicitacaoId();
        HoldHotelCriado origem = payloadMapper.desserializar(pendente.getPayload(), HoldHotelCriado.class);

        // Antes de qualquer efeito colateral, para que uma falha não deixe
        // um pagamento pela metade que a retentativa teria de limpar.
        injetorFalha.verificar(EtapaSaga.T6, solicitacaoId, tentativa);

        Pagamento pagamento = pagamentoRepository
                .obterPorSolicitacaoIdETipo(solicitacaoId, Pagamento.TipoPagamento.HOTEL)
                .orElseGet(() -> pagamentoUseCase.criarEConfirmar(solicitacaoId,
                        Pagamento.TipoPagamento.HOTEL, origem.getReferencia(), origem.getValor()));

        eventPublisher.publish(CanaisSaga.PAGAMENTO, solicitacaoId, List.of(
                PagamentoHotelConfirmado.builder()
                        .solicitacaoId(solicitacaoId)
                        .pagamentoId(pagamento.getId())
                        .valor(pagamento.getValor())
                        .build()));
    }

    @Override
    public void aoFalharDefinitivamente(EtapaPendenteJpaEntity pendente,
                                        int tentativa,
                                        boolean permanente,
                                        String motivo,
                                        DecisaoFalha decisao) {

        Long solicitacaoId = pendente.getSolicitacaoId();

        if (permanente) {
            registrarPagamentoFalho(solicitacaoId, pendente);
        }

        if (decisao == DecisaoFalha.ABORTAR) {
            eventPublisher.publish(CanaisSaga.SAGA, solicitacaoId, List.of(
                    SagaAbortada.builder()
                            .solicitacaoId(solicitacaoId)
                            .etapa(EtapaSaga.T6)
                            .servico("payment")
                            .motivo(motivo)
                            .tentativas(tentativa)
                            .build()));
            return;
        }

        eventPublisher.publish(CanaisSaga.SAGA, solicitacaoId, List.of(
                SagaFalhou.builder()
                        .solicitacaoId(solicitacaoId)
                        .etapa(EtapaSaga.T6)
                        .servico("payment")
                        .motivo(motivo)
                        .permanente(permanente)
                        .tentativas(tentativa)
                        .build()));
    }

    /**
     * Deixa registrado o pagamento recusado (status FALHOU) antes de encerrar,
     * para que a recusa apareça no banco e não só na auditoria.
     */
    private void registrarPagamentoFalho(Long solicitacaoId, EtapaPendenteJpaEntity pendente) {
        if (pagamentoRepository.obterPorSolicitacaoIdETipo(solicitacaoId, Pagamento.TipoPagamento.HOTEL).isPresent()) {
            return;
        }
        HoldHotelCriado origem = payloadMapper.desserializar(pendente.getPayload(), HoldHotelCriado.class);

        pagamentoUseCase.registrarFalha(solicitacaoId, Pagamento.TipoPagamento.HOTEL,
                origem.getReferencia(), origem.getValor());
    }
}
