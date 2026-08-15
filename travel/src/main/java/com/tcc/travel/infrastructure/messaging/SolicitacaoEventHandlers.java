package com.tcc.travel.infrastructure.messaging;

import com.tcc.saga.event.AprovacaoAprovada;
import com.tcc.saga.event.AprovacaoCancelada;
import com.tcc.saga.event.AprovacaoRejeitada;
import com.tcc.saga.event.AprovacaoSolicitada;
import com.tcc.saga.event.CanaisSaga;
import com.tcc.saga.event.PagamentoHotelConfirmado;
import com.tcc.saga.event.PagamentoVooConfirmado;
import com.tcc.saga.event.SagaAbortada;
import com.tcc.travel.application.usecase.SolicitacaoUseCase;

import io.eventuate.tram.events.subscriber.DomainEventEnvelope;
import io.eventuate.tram.events.subscriber.DomainEventHandlers;
import io.eventuate.tram.events.subscriber.DomainEventHandlersBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Reações do travel aos eventos dos outros participantes da saga.
 *
 * O travel não observa {@code SagaFalhou} diretamente: a cadeia de compensação
 * chega até ele pelo elo anterior ({@code AprovacaoCancelada}), garantindo que
 * T1 só seja compensada depois de T2..T6.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SolicitacaoEventHandlers {

    private final SolicitacaoUseCase solicitacaoUseCase;

    public DomainEventHandlers domainEventHandlers() {
        return DomainEventHandlersBuilder
                .forAggregateType(CanaisSaga.APROVACAO)
                .onEvent(AprovacaoSolicitada.class, this::aoAprovacaoSolicitada)
                .onEvent(AprovacaoAprovada.class, this::aoAprovacaoAprovada)
                .onEvent(AprovacaoRejeitada.class, this::aoAprovacaoRejeitada)
                .onEvent(AprovacaoCancelada.class, this::aoAprovacaoCancelada)

                .andForAggregateType(CanaisSaga.PAGAMENTO)
                .onEvent(PagamentoVooConfirmado.class, this::aoPagamentoVooConfirmado)
                .onEvent(PagamentoHotelConfirmado.class, this::aoPagamentoHotelConfirmado)

                .andForAggregateType(CanaisSaga.SAGA)
                .onEvent(SagaAbortada.class, this::aoSagaAbortada)
                .build();
    }

    private void aoAprovacaoSolicitada(DomainEventEnvelope<AprovacaoSolicitada> envelope) {
        log.debug("Aprovação solicitada para a saga {}", envelope.getEvent().getSolicitacaoId());
        solicitacaoUseCase.aoAprovacaoSolicitada(envelope.getEvent());
    }

    private void aoAprovacaoAprovada(DomainEventEnvelope<AprovacaoAprovada> envelope) {
        log.debug("Aprovação aprovada para a saga {}", envelope.getEvent().getSolicitacaoId());
        solicitacaoUseCase.aoAprovacaoAprovada(envelope.getEvent());
    }

    private void aoAprovacaoRejeitada(DomainEventEnvelope<AprovacaoRejeitada> envelope) {
        log.info("Aprovação rejeitada para a saga {}", envelope.getEvent().getSolicitacaoId());
        solicitacaoUseCase.aoAprovacaoRejeitada(envelope.getEvent());
    }

    private void aoAprovacaoCancelada(DomainEventEnvelope<AprovacaoCancelada> envelope) {
        log.info("Compensação de T1 para a saga {}", envelope.getEvent().getSolicitacaoId());
        solicitacaoUseCase.aoAprovacaoCancelada(envelope.getEvent());
    }

    private void aoPagamentoVooConfirmado(DomainEventEnvelope<PagamentoVooConfirmado> envelope) {
        log.debug("Pagamento de voo confirmado para a saga {}", envelope.getEvent().getSolicitacaoId());
        solicitacaoUseCase.aoPagamentoVooConfirmado(envelope.getEvent());
    }

    private void aoPagamentoHotelConfirmado(DomainEventEnvelope<PagamentoHotelConfirmado> envelope) {
        log.debug("Pagamento de hotel confirmado para a saga {}", envelope.getEvent().getSolicitacaoId());
        solicitacaoUseCase.aoPagamentoHotelConfirmado(envelope.getEvent());
    }

    private void aoSagaAbortada(DomainEventEnvelope<SagaAbortada> envelope) {
        log.info("Saga {} abortada sem compensação", envelope.getEvent().getSolicitacaoId());
        solicitacaoUseCase.aoSagaAbortada(envelope.getEvent());
    }
}
