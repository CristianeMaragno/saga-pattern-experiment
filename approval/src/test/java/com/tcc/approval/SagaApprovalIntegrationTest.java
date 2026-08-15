package com.tcc.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.tcc.approval.domain.entity.Aprovacao;
import com.tcc.approval.domain.repository.AprovacaoRepository;
import com.tcc.saga.event.AprovacaoAprovada;
import com.tcc.saga.event.AprovacaoCancelada;
import com.tcc.saga.event.AprovacaoRejeitada;
import com.tcc.saga.event.AprovacaoSolicitada;
import com.tcc.saga.event.CanaisSaga;
import com.tcc.saga.event.CompensacaoBookingConcluida;
import com.tcc.saga.event.EtapaSaga;
import com.tcc.saga.event.SagaAbortada;
import com.tcc.saga.event.SagaAuditEvento;
import com.tcc.saga.event.SagaFalhou;
import com.tcc.saga.event.SolicitacaoCriada;
import com.tcc.saga.experimento.ExperimentoProperties;

import io.eventuate.tram.events.common.DomainEvent;
import io.eventuate.tram.events.publisher.DomainEventPublisher;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Participação do approval na saga (T2), incluindo o comportamento das três
 * estratégias de recuperação diante de uma falha injetada nessa posição.
 *
 * As propriedades do experimento são alteradas em tempo de execução em vez de
 * por contexto Spring separado: o motor de recuperação e o injetor de falhas
 * leem a configuração a cada execução, então um único contexto cobre todas as
 * combinações e a suíte roda em segundos.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Saga — approval (T2 e estratégias de recuperação)")
class SagaApprovalIntegrationTest {

    private static final AtomicLong PROXIMA_SAGA = new AtomicLong(1000);

    @Autowired
    private AprovacaoRepository aprovacaoRepository;

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
        experimento.setPosicaoFalha(EtapaSaga.T2);
        experimento.setLimiteTentativas(3);
        experimento.setFalhasTemporarias(2);
    }

    private void publicar(String canal, Long solicitacaoId, DomainEvent evento) {
        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publish(canal, solicitacaoId, List.of(evento)));
    }

    /** Dispara T1 → T2 e devolve o id de correlação da saga. */
    private Long abrirSaga() {
        Long solicitacaoId = PROXIMA_SAGA.incrementAndGet();
        publicar(CanaisSaga.SOLICITACAO, solicitacaoId, SolicitacaoCriada.builder()
                .solicitacaoId(solicitacaoId)
                .usuarioId(1L)
                .destino("São Paulo")
                .dataIda("2026-09-01")
                .dataVolta("2026-09-05")
                .motivo("Reunião com cliente")
                .valorVoo(new BigDecimal("1500.00"))
                .valorHotel(new BigDecimal("800.00"))
                .build());
        return solicitacaoId;
    }

    private Aprovacao aprovacaoDa(Long solicitacaoId) {
        return aprovacaoRepository.obterPorSolicitacaoId(solicitacaoId).orElse(null);
    }

    @Test
    @DisplayName("caminho feliz: abre a aprovação e decide, publicando aprovacao.aprovada")
    void caminhoFelizDecideAprovacao() {
        Long saga = abrirSaga();

        await().atMost(Duration.ofSeconds(10)).until(() -> coletor.recebeu(AprovacaoAprovada.class));

        assertThat(coletor.primeiro(AprovacaoSolicitada.class).getSolicitacaoId()).isEqualTo(saga);

        AprovacaoAprovada aprovada = coletor.primeiro(AprovacaoAprovada.class);
        assertThat(aprovada.getSolicitacaoId()).isEqualTo(saga);
        // Os valores precisam seguir adiante: o payment não faz chamada síncrona
        // para descobri-los.
        assertThat(aprovada.getValorVoo()).isEqualByComparingTo("1500.00");
        assertThat(aprovada.getValorHotel()).isEqualByComparingTo("800.00");

        assertThat(aprovacaoDa(saga).getStatus()).isEqualTo(Aprovacao.StatusAprovacao.APROVADA);
        assertThat(coletor.recebeu(SagaFalhou.class)).isFalse();
    }

    @Test
    @DisplayName("reentrega de solicitacao.criada não abre uma segunda aprovação")
    void consumoDeT1EIdempotente() {
        Long saga = PROXIMA_SAGA.incrementAndGet();
        SolicitacaoCriada evento = SolicitacaoCriada.builder()
                .solicitacaoId(saga).usuarioId(1L).destino("Recife")
                .dataIda("2026-09-01").motivo("Treinamento")
                .valorVoo(new BigDecimal("1500.00")).valorHotel(new BigDecimal("800.00"))
                .build();

        publicar(CanaisSaga.SOLICITACAO, saga, evento);
        await().atMost(Duration.ofSeconds(10)).until(() -> aprovacaoDa(saga) != null);

        publicar(CanaisSaga.SOLICITACAO, saga, evento);
        await().atMost(Duration.ofSeconds(10)).until(() -> coletor.recebeu(AprovacaoAprovada.class));

        // Uma única aprovação aberta, e T2 decidida uma única vez.
        assertThat(coletor.doTipo(AprovacaoSolicitada.class)).hasSize(1);
        assertThat(coletor.doTipo(AprovacaoAprovada.class)).hasSize(1);
    }

    @Test
    @DisplayName("Backward: falha temporária não gera retry e dispara a compensação na hora")
    void backwardCompensaSemTentarNovamente() {
        experimento.setEstrategia(ExperimentoProperties.EstrategiaRecuperacao.BACKWARD);
        experimento.setCenarioFalha(ExperimentoProperties.CenarioFalha.TEMPORARY);

        Long saga = abrirSaga();

        await().atMost(Duration.ofSeconds(10)).until(() -> coletor.recebeu(SagaFalhou.class));

        SagaFalhou falha = coletor.primeiro(SagaFalhou.class);
        assertThat(falha.getSolicitacaoId()).isEqualTo(saga);
        assertThat(falha.getEtapa()).isEqualTo(EtapaSaga.T2);
        assertThat(falha.getTentativas()).isEqualTo(1);

        // O que distingue o Backward: nenhuma retentativa antes de desfazer.
        assertThat(coletor.contarAuditoria(SagaAuditEvento.AcaoAuditoria.RETRY)).isZero();
        assertThat(coletor.recebeu(AprovacaoAprovada.class)).isFalse();
    }

    @Test
    @DisplayName("Forward: falha temporária se recupera por retry, sem compensar nada")
    void forwardRecuperaPorRetry() {
        experimento.setEstrategia(ExperimentoProperties.EstrategiaRecuperacao.FORWARD);
        experimento.setCenarioFalha(ExperimentoProperties.CenarioFalha.TEMPORARY);
        experimento.setFalhasTemporarias(2);
        experimento.setLimiteTentativas(3);

        Long saga = abrirSaga();

        await().atMost(Duration.ofSeconds(15)).until(() -> coletor.recebeu(AprovacaoAprovada.class));

        assertThat(coletor.primeiro(AprovacaoAprovada.class).getSolicitacaoId()).isEqualTo(saga);
        assertThat(aprovacaoDa(saga).getStatus()).isEqualTo(Aprovacao.StatusAprovacao.APROVADA);

        // Duas falhas e duas retentativas antes de dar certo na terceira execução.
        assertThat(coletor.contarAuditoria(SagaAuditEvento.AcaoAuditoria.FALHA)).isEqualTo(2);
        assertThat(coletor.contarAuditoria(SagaAuditEvento.AcaoAuditoria.RETRY)).isEqualTo(2);
        assertThat(coletor.contarAuditoria(SagaAuditEvento.AcaoAuditoria.SUCESSO)).isEqualTo(1);

        // Forward não desfaz: nada de compensação nem de saga falhada.
        assertThat(coletor.recebeu(SagaFalhou.class)).isFalse();
        assertThat(coletor.recebeu(SagaAbortada.class)).isFalse();
    }

    @Test
    @DisplayName("Forward esgotado: aborta a saga sem disparar compensação")
    void forwardEsgotadoAbortaSemCompensar() {
        experimento.setEstrategia(ExperimentoProperties.EstrategiaRecuperacao.FORWARD);
        experimento.setCenarioFalha(ExperimentoProperties.CenarioFalha.TEMPORARY);
        // A falha nunca se cura dentro do limite de tentativas.
        experimento.setFalhasTemporarias(99);
        experimento.setLimiteTentativas(2);

        Long saga = abrirSaga();

        await().atMost(Duration.ofSeconds(15)).until(() -> coletor.recebeu(SagaAbortada.class));

        SagaAbortada abortada = coletor.primeiro(SagaAbortada.class);
        assertThat(abortada.getSolicitacaoId()).isEqualTo(saga);
        assertThat(abortada.getEtapa()).isEqualTo(EtapaSaga.T2);
        assertThat(abortada.getTentativas()).isEqualTo(3); // 1 execução + 2 retentativas

        assertThat(coletor.contarAuditoria(SagaAuditEvento.AcaoAuditoria.RETRY)).isEqualTo(2);
        assertThat(coletor.recebeu(SagaFalhou.class)).isFalse();
    }

    @Test
    @DisplayName("Híbrido esgotado: tenta como o Forward e depois cai para a cadeia backward")
    void hibridoEsgotadoCaiParaBackward() {
        experimento.setEstrategia(ExperimentoProperties.EstrategiaRecuperacao.HYBRID);
        experimento.setCenarioFalha(ExperimentoProperties.CenarioFalha.TEMPORARY);
        experimento.setFalhasTemporarias(99);
        experimento.setLimiteTentativas(2);

        Long saga = abrirSaga();

        await().atMost(Duration.ofSeconds(15)).until(() -> coletor.recebeu(SagaFalhou.class));

        SagaFalhou falha = coletor.primeiro(SagaFalhou.class);
        assertThat(falha.getSolicitacaoId()).isEqualTo(saga);
        assertThat(falha.getTentativas()).isEqualTo(3);

        // A diferença para o Backward puro: houve retentativa antes de desistir.
        assertThat(coletor.contarAuditoria(SagaAuditEvento.AcaoAuditoria.RETRY)).isEqualTo(2);
        assertThat(coletor.recebeu(SagaAbortada.class)).isFalse();
    }

    @Test
    @DisplayName("Híbrido: falha temporária que se cura não chega a compensar")
    void hibridoRecuperaAntesDoFallback() {
        experimento.setEstrategia(ExperimentoProperties.EstrategiaRecuperacao.HYBRID);
        experimento.setCenarioFalha(ExperimentoProperties.CenarioFalha.TEMPORARY);
        experimento.setFalhasTemporarias(1);
        experimento.setLimiteTentativas(3);

        Long saga = abrirSaga();

        await().atMost(Duration.ofSeconds(15)).until(() -> coletor.recebeu(AprovacaoAprovada.class));

        assertThat(aprovacaoDa(saga).getStatus()).isEqualTo(Aprovacao.StatusAprovacao.APROVADA);
        assertThat(coletor.recebeu(SagaFalhou.class)).isFalse();
        assertThat(coletor.contarAuditoria(SagaAuditEvento.AcaoAuditoria.RETRY)).isEqualTo(1);
    }

    @Test
    @DisplayName("falha permanente em T2 vira rejeição de negócio, sem retry e sem compensação")
    void falhaPermanenteViraRejeicao() {
        experimento.setEstrategia(ExperimentoProperties.EstrategiaRecuperacao.HYBRID);
        experimento.setCenarioFalha(ExperimentoProperties.CenarioFalha.PERMANENT);

        Long saga = abrirSaga();

        await().atMost(Duration.ofSeconds(10)).until(() -> coletor.recebeu(AprovacaoRejeitada.class));

        assertThat(coletor.primeiro(AprovacaoRejeitada.class).getSolicitacaoId()).isEqualTo(saga);
        assertThat(aprovacaoDa(saga).getStatus()).isEqualTo(Aprovacao.StatusAprovacao.REJEITADA);

        // Retry não recupera falha permanente, e antes do pivô não há o que desfazer.
        assertThat(coletor.contarAuditoria(SagaAuditEvento.AcaoAuditoria.RETRY)).isZero();
        assertThat(coletor.recebeu(SagaFalhou.class)).isFalse();
        assertThat(coletor.recebeu(SagaAbortada.class)).isFalse();
    }

    @Test
    @DisplayName("compensação de T2 cancela a aprovação e repassa a cadeia ao travel")
    void compensacaoDeT2RepassaCadeia() {
        Long saga = abrirSaga();
        await().atMost(Duration.ofSeconds(10)).until(() -> coletor.recebeu(AprovacaoAprovada.class));

        publicar(CanaisSaga.SAGA, saga, CompensacaoBookingConcluida.builder()
                .solicitacaoId(saga).etapaOrigemFalha(EtapaSaga.T6).build());

        await().atMost(Duration.ofSeconds(10)).until(() -> coletor.recebeu(AprovacaoCancelada.class));

        AprovacaoCancelada cancelada = coletor.primeiro(AprovacaoCancelada.class);
        assertThat(cancelada.getSolicitacaoId()).isEqualTo(saga);
        assertThat(cancelada.getEtapaOrigemFalha()).isEqualTo(EtapaSaga.T6);
        assertThat(aprovacaoDa(saga).getStatus()).isEqualTo(Aprovacao.StatusAprovacao.CANCELADA);
        assertThat(coletor.contarAuditoria(SagaAuditEvento.AcaoAuditoria.COMPENSACAO)).isEqualTo(1);
    }
}
