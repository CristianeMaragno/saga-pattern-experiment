package com.tcc.payment.infrastructure.messaging;

import com.tcc.payment.application.usecase.PagamentoSagaUseCase;
import com.tcc.saga.event.CanaisSaga;
import com.tcc.saga.event.HoldHotelCriado;
import com.tcc.saga.event.HoldVooCriado;
import com.tcc.saga.event.SagaFalhou;

import io.eventuate.tram.events.subscriber.DomainEventEnvelope;
import io.eventuate.tram.events.subscriber.DomainEventHandlers;
import io.eventuate.tram.events.subscriber.DomainEventHandlersBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Reações do payment aos eventos da saga.
 *
 * O payment é o primeiro elo da cadeia backward, então é o único que observa
 * {@code SagaFalhou} diretamente — inclusive o que ele mesmo publica quando a
 * falha acontece em T6.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PagamentoEventHandlers {

    private final PagamentoSagaUseCase pagamentoSagaUseCase;

    public DomainEventHandlers domainEventHandlers() {
        return DomainEventHandlersBuilder
                .forAggregateType(CanaisSaga.HOLD)
                .onEvent(HoldVooCriado.class, this::aoHoldVooCriado)
                .onEvent(HoldHotelCriado.class, this::aoHoldHotelCriado)

                .andForAggregateType(CanaisSaga.SAGA)
                .onEvent(SagaFalhou.class, this::aoSagaFalhou)
                .build();
    }

    private void aoHoldVooCriado(DomainEventEnvelope<HoldVooCriado> envelope) {
        log.debug("Pagando voo (T5) da saga {}", envelope.getEvent().getSolicitacaoId());
        pagamentoSagaUseCase.aoHoldVooCriado(envelope.getEvent());
    }

    private void aoHoldHotelCriado(DomainEventEnvelope<HoldHotelCriado> envelope) {
        log.debug("Agendando pagamento de hotel (T6) da saga {}", envelope.getEvent().getSolicitacaoId());
        pagamentoSagaUseCase.aoHoldHotelCriado(envelope.getEvent());
    }

    private void aoSagaFalhou(DomainEventEnvelope<SagaFalhou> envelope) {
        log.info("Iniciando cadeia de compensação da saga {} a partir de {}",
                envelope.getEvent().getSolicitacaoId(), envelope.getEvent().getEtapa());
        pagamentoSagaUseCase.aoSagaFalhou(envelope.getEvent());
    }
}
