package com.tcc.saga.event;

import io.eventuate.tram.events.common.DomainEvent;

/**
 * Contrato comum a todos os eventos da saga de reserva de viagem.
 *
 * O {@code solicitacaoId} é o identificador de correlação da instância de saga:
 * sem ele não é possível religar os passos T1..T7 nem calcular métricas por
 * instância de saga (ver seção 5 do plano de execução).
 */
public interface EventoSaga extends DomainEvent {

    Long getSolicitacaoId();
}
