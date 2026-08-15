package com.tcc.approval.infrastructure.messaging;

import com.tcc.approval.application.usecase.AprovacaoSagaUseCase;
import com.tcc.saga.event.CanaisSaga;
import com.tcc.saga.event.CompensacaoBookingConcluida;
import com.tcc.saga.event.SolicitacaoCriada;

import io.eventuate.tram.events.subscriber.DomainEventEnvelope;
import io.eventuate.tram.events.subscriber.DomainEventHandlers;
import io.eventuate.tram.events.subscriber.DomainEventHandlersBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Reações do approval aos eventos da saga.
 *
 * O approval é o penúltimo elo da cadeia backward: só compensa T2 depois que o
 * booking avisa ter compensado T4 e T3, preservando a ordem inversa da saga.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AprovacaoEventHandlers {

    private final AprovacaoSagaUseCase aprovacaoSagaUseCase;

    public DomainEventHandlers domainEventHandlers() {
        return DomainEventHandlersBuilder
                .forAggregateType(CanaisSaga.SOLICITACAO)
                .onEvent(SolicitacaoCriada.class, this::aoSolicitacaoCriada)

                .andForAggregateType(CanaisSaga.SAGA)
                .onEvent(CompensacaoBookingConcluida.class, this::aoCompensacaoBookingConcluida)
                .build();
    }

    private void aoSolicitacaoCriada(DomainEventEnvelope<SolicitacaoCriada> envelope) {
        log.debug("Abrindo aprovação (T2) para a saga {}", envelope.getEvent().getSolicitacaoId());
        aprovacaoSagaUseCase.aoSolicitacaoCriada(envelope.getEvent());
    }

    private void aoCompensacaoBookingConcluida(DomainEventEnvelope<CompensacaoBookingConcluida> envelope) {
        log.info("Compensando T2 da saga {}", envelope.getEvent().getSolicitacaoId());
        aprovacaoSagaUseCase.aoCompensacaoBookingConcluida(envelope.getEvent());
    }
}
