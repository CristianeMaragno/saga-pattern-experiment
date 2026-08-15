package com.tcc.payment;

import com.tcc.saga.event.AprovacaoAprovada;
import com.tcc.saga.event.AprovacaoCancelada;
import com.tcc.saga.event.AprovacaoRejeitada;
import com.tcc.saga.event.AprovacaoSolicitada;
import com.tcc.saga.event.CanaisSaga;
import com.tcc.saga.event.CompensacaoBookingConcluida;
import com.tcc.saga.event.CompensacaoPagamentoConcluida;
import com.tcc.saga.event.HoldHotelCriado;
import com.tcc.saga.event.HoldHotelLiberado;
import com.tcc.saga.event.HoldVooCriado;
import com.tcc.saga.event.HoldVooLiberado;
import com.tcc.saga.event.PagamentoHotelCancelado;
import com.tcc.saga.event.PagamentoHotelConfirmado;
import com.tcc.saga.event.PagamentoVooCancelado;
import com.tcc.saga.event.PagamentoVooConfirmado;
import com.tcc.saga.event.SagaAbortada;
import com.tcc.saga.event.SagaAuditEvento;
import com.tcc.saga.event.SagaFalhou;
import com.tcc.saga.event.SolicitacaoCriada;

import io.eventuate.tram.events.common.DomainEvent;
import io.eventuate.tram.events.subscriber.DomainEventDispatcher;
import io.eventuate.tram.events.subscriber.DomainEventDispatcherFactory;
import io.eventuate.tram.events.subscriber.DomainEventEnvelope;
import io.eventuate.tram.events.subscriber.DomainEventHandlers;
import io.eventuate.tram.events.subscriber.DomainEventHandlersBuilder;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Assina todos os canais da saga e guarda o que passou por eles, para os testes
 * poderem afirmar sobre os eventos que o serviço realmente publicou — e não
 * apenas sobre o estado que ele gravou.
 */
@Configuration
@Profile("test")
public class ColetorEventos {

    private final List<DomainEvent> recebidos = new CopyOnWriteArrayList<>();

    @Bean
    public DomainEventDispatcher coletorDispatcher(DomainEventDispatcherFactory factory) {
        return factory.make("coletorDeTeste", handlers());
    }

    public void limpar() {
        recebidos.clear();
    }

    /** Todos os eventos de um tipo, na ordem em que chegaram. */
    public <T extends DomainEvent> List<T> doTipo(Class<T> tipo) {
        return recebidos.stream().filter(tipo::isInstance).map(tipo::cast).toList();
    }

    /** O primeiro evento de um tipo, ou {@code null} se nenhum chegou. */
    public <T extends DomainEvent> T primeiro(Class<T> tipo) {
        return doTipo(tipo).stream().findFirst().orElse(null);
    }

    public <T extends DomainEvent> boolean recebeu(Class<T> tipo) {
        return !doTipo(tipo).isEmpty();
    }

    public List<SagaAuditEvento> auditoria() {
        return doTipo(SagaAuditEvento.class);
    }

    /** Quantas vezes uma ação de auditoria foi registrada. */
    public long contarAuditoria(SagaAuditEvento.AcaoAuditoria acao) {
        return auditoria().stream().filter(e -> e.getAcao() == acao).count();
    }

    private DomainEventHandlers handlers() {
        return DomainEventHandlersBuilder
                .forAggregateType(CanaisSaga.SOLICITACAO)
                .onEvent(SolicitacaoCriada.class, this::coletar)

                .andForAggregateType(CanaisSaga.APROVACAO)
                .onEvent(AprovacaoSolicitada.class, this::coletar)
                .onEvent(AprovacaoAprovada.class, this::coletar)
                .onEvent(AprovacaoRejeitada.class, this::coletar)
                .onEvent(AprovacaoCancelada.class, this::coletar)

                .andForAggregateType(CanaisSaga.HOLD)
                .onEvent(HoldVooCriado.class, this::coletar)
                .onEvent(HoldHotelCriado.class, this::coletar)
                .onEvent(HoldVooLiberado.class, this::coletar)
                .onEvent(HoldHotelLiberado.class, this::coletar)

                .andForAggregateType(CanaisSaga.PAGAMENTO)
                .onEvent(PagamentoVooConfirmado.class, this::coletar)
                .onEvent(PagamentoHotelConfirmado.class, this::coletar)
                .onEvent(PagamentoVooCancelado.class, this::coletar)
                .onEvent(PagamentoHotelCancelado.class, this::coletar)

                .andForAggregateType(CanaisSaga.SAGA)
                .onEvent(SagaFalhou.class, this::coletar)
                .onEvent(SagaAbortada.class, this::coletar)
                .onEvent(CompensacaoPagamentoConcluida.class, this::coletar)
                .onEvent(CompensacaoBookingConcluida.class, this::coletar)

                .andForAggregateType(CanaisSaga.AUDITORIA)
                .onEvent(SagaAuditEvento.class, this::coletar)
                .build();
    }

    private void coletar(DomainEventEnvelope<? extends DomainEvent> envelope) {
        recebidos.add(envelope.getEvent());
    }
}
