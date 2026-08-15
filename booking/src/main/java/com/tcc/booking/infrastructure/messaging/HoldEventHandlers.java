package com.tcc.booking.infrastructure.messaging;

import com.tcc.booking.application.usecase.HoldSagaUseCase;
import com.tcc.saga.event.AprovacaoAprovada;
import com.tcc.saga.event.CanaisSaga;
import com.tcc.saga.event.CompensacaoPagamentoConcluida;

import io.eventuate.tram.events.subscriber.DomainEventEnvelope;
import io.eventuate.tram.events.subscriber.DomainEventHandlers;
import io.eventuate.tram.events.subscriber.DomainEventHandlersBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Reações do booking aos eventos da saga.
 *
 * O booking é o terceiro elo da cadeia backward: só libera os holds depois que
 * o payment avisa ter compensado T6 e T5, preservando a ordem inversa da saga.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoldEventHandlers {

    private final HoldSagaUseCase holdSagaUseCase;

    public DomainEventHandlers domainEventHandlers() {
        return DomainEventHandlersBuilder
                .forAggregateType(CanaisSaga.APROVACAO)
                .onEvent(AprovacaoAprovada.class, this::aoAprovacaoAprovada)

                .andForAggregateType(CanaisSaga.SAGA)
                .onEvent(CompensacaoPagamentoConcluida.class, this::aoCompensacaoPagamentoConcluida)
                .build();
    }

    private void aoAprovacaoAprovada(DomainEventEnvelope<AprovacaoAprovada> envelope) {
        log.debug("Abrindo reservas (T3/T4) para a saga {}", envelope.getEvent().getSolicitacaoId());
        holdSagaUseCase.aoAprovacaoAprovada(envelope.getEvent());
    }

    private void aoCompensacaoPagamentoConcluida(DomainEventEnvelope<CompensacaoPagamentoConcluida> envelope) {
        log.info("Compensando T4/T3 da saga {}", envelope.getEvent().getSolicitacaoId());
        holdSagaUseCase.aoCompensacaoPagamentoConcluida(envelope.getEvent());
    }
}
