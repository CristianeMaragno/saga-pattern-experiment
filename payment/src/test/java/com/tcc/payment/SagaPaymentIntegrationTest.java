package com.tcc.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.tcc.payment.domain.entity.Pagamento;
import com.tcc.payment.domain.repository.PagamentoRepository;
import com.tcc.saga.event.CanaisSaga;
import com.tcc.saga.event.CompensacaoPagamentoConcluida;
import com.tcc.saga.event.EtapaSaga;
import com.tcc.saga.event.HoldHotelCriado;
import com.tcc.saga.event.HoldVooCriado;
import com.tcc.saga.event.PagamentoHotelCancelado;
import com.tcc.saga.event.PagamentoHotelConfirmado;
import com.tcc.saga.event.PagamentoVooCancelado;
import com.tcc.saga.event.PagamentoVooConfirmado;
import com.tcc.saga.event.SagaAbortada;
import com.tcc.saga.event.SagaAuditEvento;
import com.tcc.saga.event.SagaFalhou;
import com.tcc.saga.experimento.ExperimentoProperties;

import io.eventuate.tram.events.common.DomainEvent;
import io.eventuate.tram.events.publisher.DomainEventPublisher;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Participação do payment na saga: T5, T6 e o primeiro elo da cadeia backward.
 *
 * É o serviço onde a compensação passa a ter custo — depois do ponto de pivô,
 * desfazer significa estornar um pagamento já efetuado.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Saga — payment (T5, T6 e início da compensação)")
class SagaPaymentIntegrationTest {

    private static final AtomicLong PROXIMA_SAGA = new AtomicLong(3000);

    @Autowired
    private PagamentoRepository pagamentoRepository;

    @Autowired
    private DomainEventPublisher eventPublisher;

    @Autowired
    private ColetorEventos coletor;

    @Autowired
    private ExperimentoProperties experimento;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void prepararRodada() {
        coletor.limpar();
        experimento.setEstrategia(ExperimentoProperties.EstrategiaRecuperacao.BACKWARD);
        experimento.setCenarioFalha(ExperimentoProperties.CenarioFalha.NONE);
        experimento.setPosicaoFalha(EtapaSaga.T6);
        experimento.setLimiteTentativas(3);
        experimento.setFalhasTemporarias(2);
    }

    private void publicar(String canal, Long solicitacaoId, DomainEvent evento) {
        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publish(canal, solicitacaoId, List.of(evento)));
    }

    /** Simula os dois holds do booking, que é o que dispara T5 e T6. */
    private Long reservar() {
        Long saga = PROXIMA_SAGA.incrementAndGet();
        long expira = Instant.now().plusSeconds(3600).toEpochMilli();

        publicar(CanaisSaga.HOLD, saga, HoldVooCriado.builder()
                .solicitacaoId(saga).holdId(1L).referencia("solicitacao:" + saga)
                .expiraEmMillis(expira).valor(new BigDecimal("1500.00")).build());

        publicar(CanaisSaga.HOLD, saga, HoldHotelCriado.builder()
                .solicitacaoId(saga).holdId(2L).referencia("solicitacao:" + saga)
                .expiraEmMillis(expira).valor(new BigDecimal("800.00")).build());

        return saga;
    }

    private Optional<Pagamento> pagamento(Long saga, Pagamento.TipoPagamento tipo) {
        return pagamentoRepository.obterPorSolicitacaoIdETipo(saga, tipo);
    }

    @Test
    @DisplayName("caminho feliz: T5 e T6 confirmam os dois pagamentos")
    void t5Et6ConfirmamOsPagamentos() {
        Long saga = reservar();

        await().atMost(Duration.ofSeconds(10)).until(() ->
                coletor.recebeu(PagamentoVooConfirmado.class) && coletor.recebeu(PagamentoHotelConfirmado.class));

        assertThat(coletor.primeiro(PagamentoVooConfirmado.class).getValor()).isEqualByComparingTo("1500.00");
        assertThat(coletor.primeiro(PagamentoHotelConfirmado.class).getValor()).isEqualByComparingTo("800.00");

        assertThat(pagamento(saga, Pagamento.TipoPagamento.VOO)).get()
                .extracting(Pagamento::getStatus).isEqualTo(Pagamento.StatusPagamento.CONFIRMADO);
        assertThat(pagamento(saga, Pagamento.TipoPagamento.HOTEL)).get()
                .extracting(Pagamento::getStatus).isEqualTo(Pagamento.StatusPagamento.CONFIRMADO);
    }

    @Test
    @DisplayName("reentrega de hold.voo.criado não cobra o voo duas vezes")
    void consumoDeT5EIdempotente() {
        Long saga = PROXIMA_SAGA.incrementAndGet();
        HoldVooCriado evento = HoldVooCriado.builder()
                .solicitacaoId(saga).holdId(1L).referencia("solicitacao:" + saga)
                .expiraEmMillis(Instant.now().plusSeconds(3600).toEpochMilli())
                .valor(new BigDecimal("1500.00")).build();

        publicar(CanaisSaga.HOLD, saga, evento);
        await().atMost(Duration.ofSeconds(10)).until(() -> coletor.recebeu(PagamentoVooConfirmado.class));

        publicar(CanaisSaga.HOLD, saga, evento);
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5))
                .until(() -> coletor.doTipo(PagamentoVooConfirmado.class).size() == 1);

        assertThat(coletor.doTipo(PagamentoVooConfirmado.class)).hasSize(1);
    }

    @Test
    @DisplayName("Forward em T6: recupera por retry sem estornar o pagamento do voo")
    void forwardEmT6RecuperaSemEstornar() {
        experimento.setEstrategia(ExperimentoProperties.EstrategiaRecuperacao.FORWARD);
        experimento.setCenarioFalha(ExperimentoProperties.CenarioFalha.TEMPORARY);

        Long saga = reservar();

        await().atMost(Duration.ofSeconds(15)).until(() -> coletor.recebeu(PagamentoHotelConfirmado.class));

        // O ponto central do Forward depois do pivô: T5 permanece confirmada.
        assertThat(pagamento(saga, Pagamento.TipoPagamento.VOO)).get()
                .extracting(Pagamento::getStatus).isEqualTo(Pagamento.StatusPagamento.CONFIRMADO);
        assertThat(coletor.contarAuditoria(SagaAuditEvento.AcaoAuditoria.RETRY)).isEqualTo(2);
        assertThat(coletor.recebeu(PagamentoVooCancelado.class)).isFalse();
    }

    @Test
    @DisplayName("falha permanente em T6 registra o pagamento como FALHOU e dispara a compensação")
    void falhaPermanenteEmT6RegistraPagamentoFalho() {
        experimento.setEstrategia(ExperimentoProperties.EstrategiaRecuperacao.BACKWARD);
        experimento.setCenarioFalha(ExperimentoProperties.CenarioFalha.PERMANENT);

        Long saga = reservar();

        await().atMost(Duration.ofSeconds(15)).until(() -> coletor.recebeu(CompensacaoPagamentoConcluida.class));

        // O caminho que produz um pagamento com status FALHOU.
        assertThat(pagamento(saga, Pagamento.TipoPagamento.HOTEL)).get()
                .extracting(Pagamento::getStatus).isEqualTo(Pagamento.StatusPagamento.FALHOU);

        // A compensação estorna T5, que já havia sido cobrada.
        assertThat(coletor.recebeu(PagamentoVooCancelado.class)).isTrue();
        assertThat(pagamento(saga, Pagamento.TipoPagamento.VOO)).get()
                .extracting(Pagamento::getStatus).isEqualTo(Pagamento.StatusPagamento.CANCELADO);
    }

    @Test
    @DisplayName("Forward esgotado em T6: aborta e deixa o pagamento do voo em pé")
    void forwardEsgotadoEmT6NaoEstorna() {
        experimento.setEstrategia(ExperimentoProperties.EstrategiaRecuperacao.FORWARD);
        experimento.setCenarioFalha(ExperimentoProperties.CenarioFalha.TEMPORARY);
        experimento.setFalhasTemporarias(99);
        experimento.setLimiteTentativas(2);

        Long saga = reservar();

        await().atMost(Duration.ofSeconds(15)).until(() -> coletor.recebeu(SagaAbortada.class));

        assertThat(coletor.primeiro(SagaAbortada.class).getEtapa()).isEqualTo(EtapaSaga.T6);
        // Forward puro não desfaz: o voo continua pago.
        assertThat(pagamento(saga, Pagamento.TipoPagamento.VOO)).get()
                .extracting(Pagamento::getStatus).isEqualTo(Pagamento.StatusPagamento.CONFIRMADO);
        assertThat(coletor.recebeu(PagamentoVooCancelado.class)).isFalse();
        assertThat(coletor.recebeu(SagaFalhou.class)).isFalse();
    }

    @Test
    @DisplayName("saga.falhou estorna T6 e T5 e repassa a cadeia ao booking")
    void compensacaoEstornaAmbosOsPagamentos() {
        Long saga = reservar();
        await().atMost(Duration.ofSeconds(10)).until(() -> coletor.recebeu(PagamentoHotelConfirmado.class));
        coletor.limpar();

        publicar(CanaisSaga.SAGA, saga, SagaFalhou.builder()
                .solicitacaoId(saga).etapa(EtapaSaga.T6).servico("payment")
                .motivo("falha injetada").permanente(false).tentativas(1).build());

        await().atMost(Duration.ofSeconds(10)).until(() -> coletor.recebeu(CompensacaoPagamentoConcluida.class));

        assertThat(coletor.recebeu(PagamentoHotelCancelado.class)).isTrue();
        assertThat(coletor.recebeu(PagamentoVooCancelado.class)).isTrue();
        assertThat(pagamento(saga, Pagamento.TipoPagamento.HOTEL)).get()
                .extracting(Pagamento::getStatus).isEqualTo(Pagamento.StatusPagamento.CANCELADO);
        assertThat(pagamento(saga, Pagamento.TipoPagamento.VOO)).get()
                .extracting(Pagamento::getStatus).isEqualTo(Pagamento.StatusPagamento.CANCELADO);
        assertThat(coletor.contarAuditoria(SagaAuditEvento.AcaoAuditoria.COMPENSACAO)).isEqualTo(2);
    }

    @Test
    @DisplayName("falha antes do pivô: sem pagamentos a estornar, a cadeia segue mesmo assim")
    void eloVazioNaoInterrompeACadeia() {
        Long saga = PROXIMA_SAGA.incrementAndGet();

        publicar(CanaisSaga.SAGA, saga, SagaFalhou.builder()
                .solicitacaoId(saga).etapa(EtapaSaga.T4).servico("booking")
                .motivo("falha injetada").permanente(false).tentativas(1).build());

        await().atMost(Duration.ofSeconds(10)).until(() -> coletor.recebeu(CompensacaoPagamentoConcluida.class));

        assertThat(coletor.recebeu(PagamentoVooCancelado.class)).isFalse();
        assertThat(coletor.recebeu(PagamentoHotelCancelado.class)).isFalse();
        assertThat(coletor.primeiro(CompensacaoPagamentoConcluida.class).getEtapaOrigemFalha())
                .isEqualTo(EtapaSaga.T4);
    }
}
