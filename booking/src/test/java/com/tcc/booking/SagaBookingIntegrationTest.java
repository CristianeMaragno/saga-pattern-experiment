package com.tcc.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.tcc.booking.domain.entity.Hold;
import com.tcc.booking.domain.repository.HoldRepository;
import com.tcc.saga.event.AprovacaoAprovada;
import com.tcc.saga.event.CanaisSaga;
import com.tcc.saga.event.CompensacaoBookingConcluida;
import com.tcc.saga.event.CompensacaoPagamentoConcluida;
import com.tcc.saga.event.EtapaSaga;
import com.tcc.saga.event.HoldHotelCriado;
import com.tcc.saga.event.HoldHotelLiberado;
import com.tcc.saga.event.HoldVooCriado;
import com.tcc.saga.event.HoldVooLiberado;
import com.tcc.saga.event.SagaAbortada;
import com.tcc.saga.event.SagaAuditEvento;
import com.tcc.saga.event.SagaFalhou;
import com.tcc.saga.experimento.ExperimentoProperties;

import io.eventuate.tram.events.common.DomainEvent;
import io.eventuate.tram.events.publisher.DomainEventPublisher;
import java.math.BigDecimal;
import java.time.Duration;
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
 * Participação do booking na saga: os dois ramos paralelos T3/T4, a falha na
 * posição intermediária e a liberação dos holds na cadeia de compensação.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Saga — booking (T3, T4 e compensação dos holds)")
class SagaBookingIntegrationTest {

    private static final AtomicLong PROXIMA_SAGA = new AtomicLong(2000);

    @Autowired
    private HoldRepository holdRepository;

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
        experimento.setPosicaoFalha(EtapaSaga.T4);
        experimento.setLimiteTentativas(3);
        experimento.setFalhasTemporarias(2);
    }

    private void publicar(String canal, Long solicitacaoId, DomainEvent evento) {
        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publish(canal, solicitacaoId, List.of(evento)));
    }

    private Long aprovar() {
        Long solicitacaoId = PROXIMA_SAGA.incrementAndGet();
        publicar(CanaisSaga.APROVACAO, solicitacaoId, AprovacaoAprovada.builder()
                .solicitacaoId(solicitacaoId)
                .aprovacaoId(1L)
                .valorVoo(new BigDecimal("1500.00"))
                .valorHotel(new BigDecimal("800.00"))
                .build());
        return solicitacaoId;
    }

    private Optional<Hold> hold(Long saga, Hold.HoldType tipo) {
        return holdRepository.obterPorSolicitacaoIdETipo(saga, tipo);
    }

    @Test
    @DisplayName("caminho feliz: a aprovação abre os dois ramos, voo e hotel")
    void aprovacaoAbreOsDoisRamos() {
        Long saga = aprovar();

        await().atMost(Duration.ofSeconds(10)).until(() ->
                coletor.recebeu(HoldVooCriado.class) && coletor.recebeu(HoldHotelCriado.class));

        HoldVooCriado voo = coletor.primeiro(HoldVooCriado.class);
        assertThat(voo.getSolicitacaoId()).isEqualTo(saga);
        assertThat(voo.getValor()).isEqualByComparingTo("1500.00");
        assertThat(voo.getExpiraEmMillis()).isNotNull();

        HoldHotelCriado hotel = coletor.primeiro(HoldHotelCriado.class);
        assertThat(hotel.getValor()).isEqualByComparingTo("800.00");

        assertThat(hold(saga, Hold.HoldType.FLIGHT)).get()
                .extracting(Hold::getStatus).isEqualTo(Hold.HoldStatus.ATIVO);
        assertThat(hold(saga, Hold.HoldType.HOTEL)).get()
                .extracting(Hold::getStatus).isEqualTo(Hold.HoldStatus.ATIVO);
    }

    @Test
    @DisplayName("reentrega de aprovacao.aprovada não duplica os holds")
    void consumoDaAprovacaoEIdempotente() {
        Long saga = PROXIMA_SAGA.incrementAndGet();
        AprovacaoAprovada evento = AprovacaoAprovada.builder()
                .solicitacaoId(saga).aprovacaoId(1L)
                .valorVoo(new BigDecimal("1500.00")).valorHotel(new BigDecimal("800.00"))
                .build();

        publicar(CanaisSaga.APROVACAO, saga, evento);
        await().atMost(Duration.ofSeconds(10)).until(() -> coletor.recebeu(HoldHotelCriado.class));

        publicar(CanaisSaga.APROVACAO, saga, evento);
        // Tempo suficiente para o motor rodar de novo, se fosse duplicar.
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5))
                .until(() -> coletor.doTipo(HoldVooCriado.class).size() == 1);

        assertThat(coletor.doTipo(HoldVooCriado.class)).hasSize(1);
        assertThat(coletor.doTipo(HoldHotelCriado.class)).hasSize(1);
    }

    @Test
    @DisplayName("Backward em T4: compensa sem tentar de novo, com T3 já concluída")
    void backwardEmT4CompensaImediatamente() {
        experimento.setEstrategia(ExperimentoProperties.EstrategiaRecuperacao.BACKWARD);
        experimento.setCenarioFalha(ExperimentoProperties.CenarioFalha.TEMPORARY);

        Long saga = aprovar();

        await().atMost(Duration.ofSeconds(10)).until(() -> coletor.recebeu(SagaFalhou.class));

        SagaFalhou falha = coletor.primeiro(SagaFalhou.class);
        assertThat(falha.getEtapa()).isEqualTo(EtapaSaga.T4);
        assertThat(falha.getServico()).isEqualTo("booking");

        // T3 concluiu; T4 nunca chegou a criar o hold de hotel.
        assertThat(coletor.recebeu(HoldVooCriado.class)).isTrue();
        assertThat(coletor.recebeu(HoldHotelCriado.class)).isFalse();
        assertThat(coletor.contarAuditoria(SagaAuditEvento.AcaoAuditoria.RETRY)).isZero();
    }

    @Test
    @DisplayName("Forward em T4: recupera por retry e o ramo do hotel segue")
    void forwardEmT4RecuperaPorRetry() {
        experimento.setEstrategia(ExperimentoProperties.EstrategiaRecuperacao.FORWARD);
        experimento.setCenarioFalha(ExperimentoProperties.CenarioFalha.TEMPORARY);

        Long saga = aprovar();

        await().atMost(Duration.ofSeconds(15)).until(() -> coletor.recebeu(HoldHotelCriado.class));

        assertThat(hold(saga, Hold.HoldType.HOTEL)).get()
                .extracting(Hold::getStatus).isEqualTo(Hold.HoldStatus.ATIVO);
        assertThat(coletor.contarAuditoria(SagaAuditEvento.AcaoAuditoria.RETRY)).isEqualTo(2);
        assertThat(coletor.recebeu(SagaFalhou.class)).isFalse();
        assertThat(coletor.recebeu(SagaAbortada.class)).isFalse();
    }

    @Test
    @DisplayName("compensação libera hotel e voo e repassa a cadeia ao approval")
    void compensacaoLiberaHoldsEmOrdemInversa() {
        Long saga = aprovar();
        await().atMost(Duration.ofSeconds(10)).until(() -> coletor.recebeu(HoldHotelCriado.class));
        coletor.limpar();

        publicar(CanaisSaga.SAGA, saga, CompensacaoPagamentoConcluida.builder()
                .solicitacaoId(saga).etapaOrigemFalha(EtapaSaga.T6).build());

        await().atMost(Duration.ofSeconds(10)).until(() -> coletor.recebeu(CompensacaoBookingConcluida.class));

        assertThat(coletor.recebeu(HoldHotelLiberado.class)).isTrue();
        assertThat(coletor.recebeu(HoldVooLiberado.class)).isTrue();

        assertThat(hold(saga, Hold.HoldType.HOTEL)).get()
                .extracting(Hold::getStatus).isEqualTo(Hold.HoldStatus.LIBERADO);
        assertThat(hold(saga, Hold.HoldType.FLIGHT)).get()
                .extracting(Hold::getStatus).isEqualTo(Hold.HoldStatus.LIBERADO);

        assertThat(coletor.primeiro(CompensacaoBookingConcluida.class).getEtapaOrigemFalha())
                .isEqualTo(EtapaSaga.T6);
        assertThat(coletor.contarAuditoria(SagaAuditEvento.AcaoAuditoria.COMPENSACAO)).isEqualTo(2);
    }

    @Test
    @DisplayName("elo sem nada a compensar ainda repassa a cadeia backward adiante")
    void eloVazioNaoInterrompeACadeia() {
        // Falha em T2: o booking nunca criou hold algum para esta saga.
        Long saga = PROXIMA_SAGA.incrementAndGet();

        publicar(CanaisSaga.SAGA, saga, CompensacaoPagamentoConcluida.builder()
                .solicitacaoId(saga).etapaOrigemFalha(EtapaSaga.T2).build());

        await().atMost(Duration.ofSeconds(10)).until(() -> coletor.recebeu(CompensacaoBookingConcluida.class));

        assertThat(coletor.recebeu(HoldHotelLiberado.class)).isFalse();
        assertThat(coletor.recebeu(HoldVooLiberado.class)).isFalse();
        assertThat(coletor.primeiro(CompensacaoBookingConcluida.class).getSolicitacaoId()).isEqualTo(saga);
    }

    @Test
    @DisplayName("reentrega da compensação não libera o mesmo hold duas vezes")
    void compensacaoEIdempotente() {
        Long saga = aprovar();
        await().atMost(Duration.ofSeconds(10)).until(() -> coletor.recebeu(HoldHotelCriado.class));
        coletor.limpar();

        CompensacaoPagamentoConcluida evento = CompensacaoPagamentoConcluida.builder()
                .solicitacaoId(saga).etapaOrigemFalha(EtapaSaga.T6).build();

        publicar(CanaisSaga.SAGA, saga, evento);
        await().atMost(Duration.ofSeconds(10)).until(() -> coletor.recebeu(HoldVooLiberado.class));

        publicar(CanaisSaga.SAGA, saga, evento);
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5))
                .until(() -> coletor.doTipo(HoldVooLiberado.class).size() == 1);

        assertThat(coletor.doTipo(HoldVooLiberado.class)).hasSize(1);
        assertThat(coletor.doTipo(HoldHotelLiberado.class)).hasSize(1);
    }
}
