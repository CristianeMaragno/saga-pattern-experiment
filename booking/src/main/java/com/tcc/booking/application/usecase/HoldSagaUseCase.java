package com.tcc.booking.application.usecase;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tcc.booking.domain.entity.Hold;
import com.tcc.booking.domain.repository.HoldRepository;
import com.tcc.saga.event.AprovacaoAprovada;
import com.tcc.saga.event.CanaisSaga;
import com.tcc.saga.event.CompensacaoBookingConcluida;
import com.tcc.saga.event.CompensacaoPagamentoConcluida;
import com.tcc.saga.event.EtapaSaga;
import com.tcc.saga.event.HoldHotelLiberado;
import com.tcc.saga.event.HoldVooCriado;
import com.tcc.saga.event.HoldVooLiberado;
import com.tcc.saga.event.SagaAuditEvento;
import com.tcc.saga.experimento.AuditoriaPublisher;
import com.tcc.saga.recuperacao.MotorRecuperacao;
import com.tcc.saga.recuperacao.PayloadEtapaMapper;

import io.eventuate.tram.events.publisher.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Participação do booking na saga (T3, T4 e suas compensações).
 *
 * A aprovação dispara os dois ramos paralelos: o hold de voo (T3) executa
 * direto no handler, porque não é ponto de injeção de falha, e o hold de hotel
 * (T4) passa pelo motor de recuperação, que é onde a falha pode ser injetada e
 * recuperada.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoldSagaUseCase {

    private final HoldRepository holdRepository;
    private final HoldUseCase holdUseCase;
    private final DomainEventPublisher eventPublisher;
    private final AuditoriaPublisher auditoria;
    private final MotorRecuperacao motorRecuperacao;
    private final PayloadEtapaMapper payloadMapper;

    /**
     * T3 — cria o hold de voo e abre o ramo do hotel (T4).
     */
    @Transactional
    public void aoAprovacaoAprovada(AprovacaoAprovada evento) {
        Long solicitacaoId = evento.getSolicitacaoId();

        if (holdRepository.obterPorSolicitacaoIdETipo(solicitacaoId, Hold.HoldType.FLIGHT).isEmpty()) {
            auditoria.registrar(solicitacaoId, EtapaSaga.T3, SagaAuditEvento.AcaoAuditoria.INICIO);

            Hold voo = holdUseCase.criarHold(solicitacaoId, Hold.HoldType.FLIGHT,
                    referencia(solicitacaoId), HoldUseCase.FLIGHT_MIN_HOURS);

            eventPublisher.publish(CanaisSaga.HOLD, solicitacaoId, List.of(
                    HoldVooCriado.builder()
                            .solicitacaoId(solicitacaoId)
                            .holdId(voo.getId())
                            .referencia(voo.getReference())
                            .expiraEmMillis(voo.getExpiresAt().toEpochMilli())
                            .valor(evento.getValorVoo())
                            .build()));

            auditoria.registrar(solicitacaoId, EtapaSaga.T3, SagaAuditEvento.AcaoAuditoria.SUCESSO);
        }

        // T4 é a posição intermediária de falha do experimento: vai pelo motor.
        motorRecuperacao.enfileirar(solicitacaoId, EtapaSaga.T4, payloadMapper.serializar(evento), 0L);
    }

    /**
     * Compensação de T4 e T3, nesta ordem (inversa à da saga).
     * Terceiro elo da cadeia backward, entre o payment e o approval.
     */
    @Transactional
    public void aoCompensacaoPagamentoConcluida(CompensacaoPagamentoConcluida evento) {
        Long solicitacaoId = evento.getSolicitacaoId();

        liberar(solicitacaoId, Hold.HoldType.HOTEL, EtapaSaga.T4).ifPresent(hold ->
                eventPublisher.publish(CanaisSaga.HOLD, solicitacaoId, List.of(
                        HoldHotelLiberado.builder()
                                .solicitacaoId(solicitacaoId)
                                .holdId(hold.getId())
                                .build())));

        liberar(solicitacaoId, Hold.HoldType.FLIGHT, EtapaSaga.T3).ifPresent(hold ->
                eventPublisher.publish(CanaisSaga.HOLD, solicitacaoId, List.of(
                        HoldVooLiberado.builder()
                                .solicitacaoId(solicitacaoId)
                                .holdId(hold.getId())
                                .build())));

        // A cadeia segue para o approval mesmo quando não havia nada a liberar:
        // um elo que não tem o que compensar ainda precisa repassar a cadeia.
        eventPublisher.publish(CanaisSaga.SAGA, solicitacaoId, List.of(
                CompensacaoBookingConcluida.builder()
                        .solicitacaoId(solicitacaoId)
                        .etapaOrigemFalha(evento.getEtapaOrigemFalha())
                        .build()));
    }

    private Optional<Hold> liberar(Long solicitacaoId, Hold.HoldType tipo, EtapaSaga etapa) {
        return holdRepository.obterPorSolicitacaoIdETipo(solicitacaoId, tipo)
                .filter(Hold::liberar)
                .map(hold -> {
                    Hold salvo = holdRepository.salvar(hold);
                    auditoria.registrar(solicitacaoId, etapa, SagaAuditEvento.AcaoAuditoria.COMPENSACAO);
                    return salvo;
                });
    }

    static String referencia(Long solicitacaoId) {
        return "solicitacao:" + solicitacaoId;
    }
}
