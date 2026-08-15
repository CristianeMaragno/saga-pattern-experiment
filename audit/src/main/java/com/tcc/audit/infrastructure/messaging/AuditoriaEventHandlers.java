package com.tcc.audit.infrastructure.messaging;

import com.tcc.audit.application.usecase.AuditoriaUseCase;
import com.tcc.saga.event.CanaisSaga;
import com.tcc.saga.event.SagaAuditEvento;

import io.eventuate.tram.events.subscriber.DomainEventEnvelope;
import io.eventuate.tram.events.subscriber.DomainEventHandlers;
import io.eventuate.tram.events.subscriber.DomainEventHandlersBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Consome o canal único de auditoria alimentado por todos os serviços.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditoriaEventHandlers {

    private final AuditoriaUseCase auditoriaUseCase;

    public DomainEventHandlers domainEventHandlers() {
        return DomainEventHandlersBuilder
                .forAggregateType(CanaisSaga.AUDITORIA)
                .onEvent(SagaAuditEvento.class, this::aoEventoAuditoria)
                .build();
    }

    private void aoEventoAuditoria(DomainEventEnvelope<SagaAuditEvento> envelope) {
        auditoriaUseCase.registrar(envelope.getEvent());
    }
}
