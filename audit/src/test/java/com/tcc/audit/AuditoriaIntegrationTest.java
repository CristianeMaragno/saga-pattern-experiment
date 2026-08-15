package com.tcc.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.tcc.audit.application.dto.MetricasResponseDTO;
import com.tcc.audit.application.usecase.AuditoriaUseCase;
import com.tcc.saga.event.CanaisSaga;
import com.tcc.saga.event.EtapaSaga;
import com.tcc.saga.event.SagaAuditEvento;
import com.tcc.saga.event.SagaAuditEvento.AcaoAuditoria;

import io.eventuate.tram.events.publisher.DomainEventPublisher;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Coleta central da auditoria e derivação das métricas do experimento.
 *
 * O teste monta linhas do tempo sintéticas — uma saga de sucesso, uma
 * compensada e uma recuperada por retry — e confere que cada métrica da
 * metodologia sai corretamente da agregação.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Auditoria — coleta central e métricas")
class AuditoriaIntegrationTest {

    @Autowired
    private AuditoriaUseCase auditoriaUseCase;

    @Autowired
    private DomainEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private void publicar(SagaAuditEvento evento) {
        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publish(CanaisSaga.AUDITORIA, evento.getSolicitacaoId(), List.of(evento)));
    }

    private SagaAuditEvento evento(String runId, long saga, EtapaSaga etapa,
                                   AcaoAuditoria acao, int tentativa, long ts) {
        return SagaAuditEvento.builder()
                .runId(runId)
                .solicitacaoId(saga)
                .servico("teste")
                .etapa(etapa)
                .acao(acao)
                .tentativa(tentativa)
                .estrategia("HYBRID")
                .cenarioFalha("TEMPORARY")
                .posicaoFalha("T4")
                .timestampMillis(ts)
                .build();
    }

    @Test
    @DisplayName("o evento publicado no canal saga-audit vira linha na tabela central")
    void eventoDeAuditoriaEPersistido() {
        String runId = "rodada-persistencia";
        publicar(evento(runId, 1L, EtapaSaga.T1, AcaoAuditoria.SAGA_INICIADA, 1, 1_000L));

        await().atMost(Duration.ofSeconds(10)).until(() ->
                !auditoriaUseCase.listarPorRodada(runId).isEmpty());

        var registros = auditoriaUseCase.listarPorRodada(runId);
        assertThat(registros).hasSize(1);
        assertThat(registros.get(0).getSolicitacaoId()).isEqualTo(1L);
        assertThat(registros.get(0).getEtapa()).isEqualTo("T1");
        assertThat(registros.get(0).getAcao()).isEqualTo("SAGA_INICIADA");
        assertThat(registros.get(0).getEstrategia()).isEqualTo("HYBRID");
    }

    @Test
    @DisplayName("métricas da rodada saem da agregação por saga")
    void metricasSaoDerivadasDaLinhaDoTempo() {
        String runId = "rodada-metricas";

        // Saga 10 — caminho feliz: inicia em 1000, conclui em 3000 (2000 ms).
        publicar(evento(runId, 10L, EtapaSaga.T1, AcaoAuditoria.SAGA_INICIADA, 1, 1_000L));
        publicar(evento(runId, 10L, EtapaSaga.T7, AcaoAuditoria.SAGA_CONCLUIDA, 1, 3_000L));

        // Saga 11 — falha em T4 e é compensada: falha em 2500, termina em 4000.
        publicar(evento(runId, 11L, EtapaSaga.T1, AcaoAuditoria.SAGA_INICIADA, 1, 1_000L));
        publicar(evento(runId, 11L, EtapaSaga.T4, AcaoAuditoria.FALHA, 1, 2_500L));
        publicar(evento(runId, 11L, EtapaSaga.T4, AcaoAuditoria.COMPENSACAO, 1, 3_500L));
        publicar(evento(runId, 11L, EtapaSaga.T3, AcaoAuditoria.COMPENSACAO, 1, 3_700L));
        publicar(evento(runId, 11L, EtapaSaga.T1, AcaoAuditoria.SAGA_COMPENSADA, 1, 4_000L));

        // Saga 12 — falha em T4 mas se recupera por retry e conclui: sucesso
        // sem nenhuma compensação, que é a métrica-chave do Forward.
        publicar(evento(runId, 12L, EtapaSaga.T1, AcaoAuditoria.SAGA_INICIADA, 1, 1_000L));
        publicar(evento(runId, 12L, EtapaSaga.T4, AcaoAuditoria.FALHA, 1, 2_000L));
        publicar(evento(runId, 12L, EtapaSaga.T4, AcaoAuditoria.RETRY, 2, 2_200L));
        publicar(evento(runId, 12L, EtapaSaga.T4, AcaoAuditoria.SUCESSO, 2, 2_400L));
        publicar(evento(runId, 12L, EtapaSaga.T7, AcaoAuditoria.SAGA_CONCLUIDA, 1, 5_000L));

        await().atMost(Duration.ofSeconds(10)).until(() ->
                auditoriaUseCase.listarPorRodada(runId).size() == 12);

        MetricasResponseDTO m = auditoriaUseCase.calcularMetricas(runId);

        assertThat(m.getTotalSagas()).isEqualTo(3);
        assertThat(m.getSagasConcluidas()).isEqualTo(2);
        assertThat(m.getSagasCompensadas()).isEqualTo(1);
        assertThat(m.getSagasAbortadas()).isZero();
        assertThat(m.getSagasEmAndamento()).isZero();

        assertThat(m.getTaxaSucesso()).isEqualTo(2.0 / 3.0);

        // Conclusão: (3000-1000) e (5000-1000) → média 3000.
        assertThat(m.getTempoMedioConclusaoMs()).isEqualTo(3000.0);

        // Recuperação, só das sagas que falharam:
        // saga 11 → 4000-2500 = 1500; saga 12 → 5000-2000 = 3000; média 2250.
        assertThat(m.getTempoMedioRecuperacaoMs()).isEqualTo(2250.0);

        // Consistência eventual, sobre as três: (2000 + 3000 + 4000)/3 = 3000.
        assertThat(m.getTempoAteConsistenciaEventualMs()).isEqualTo(3000.0);

        assertThat(m.getTotalCompensacoes()).isEqualTo(2);
        assertThat(m.getTotalRetries()).isEqualTo(1);
        assertThat(m.getTotalEventos()).isEqualTo(12);

        // Duas sagas falharam; só a 12 concluiu sem compensar.
        assertThat(m.getSagasComFalha()).isEqualTo(2);
        assertThat(m.getSagasRecuperadasSemCompensacao()).isEqualTo(1);
        assertThat(m.getPercentualRecuperadasSemCompensacao()).isEqualTo(50.0);

        // Rodada de 1000 a 5000 ms → 4 s para 3 sagas.
        assertThat(m.getDuracaoRodadaMs()).isEqualTo(4000);
        assertThat(m.getThroughputSagasPorSegundo()).isEqualTo(0.75);
    }

    @Test
    @DisplayName("as rodadas não se misturam: cada runId agrega só os seus eventos")
    void rodadasSaoIsoladasPorRunId() {
        publicar(evento("rodada-a", 20L, EtapaSaga.T1, AcaoAuditoria.SAGA_INICIADA, 1, 1_000L));
        publicar(evento("rodada-a", 20L, EtapaSaga.T7, AcaoAuditoria.SAGA_CONCLUIDA, 1, 2_000L));
        publicar(evento("rodada-b", 30L, EtapaSaga.T1, AcaoAuditoria.SAGA_INICIADA, 1, 1_000L));

        await().atMost(Duration.ofSeconds(10)).until(() ->
                auditoriaUseCase.listarPorRodada("rodada-a").size() == 2
                        && auditoriaUseCase.listarPorRodada("rodada-b").size() == 1);

        assertThat(auditoriaUseCase.calcularMetricas("rodada-a").getTotalSagas()).isEqualTo(1);
        assertThat(auditoriaUseCase.calcularMetricas("rodada-a").getSagasConcluidas()).isEqualTo(1);

        MetricasResponseDTO b = auditoriaUseCase.calcularMetricas("rodada-b");
        assertThat(b.getTotalSagas()).isEqualTo(1);
        assertThat(b.getSagasConcluidas()).isZero();
        assertThat(b.getSagasEmAndamento()).isEqualTo(1);
    }

    @Test
    @DisplayName("limpar a rodada descarta apenas os dados dela")
    void limparRodadaRemoveSomenteAquelaRodada() {
        publicar(evento("rodada-descartavel", 40L, EtapaSaga.T1, AcaoAuditoria.SAGA_INICIADA, 1, 1_000L));
        publicar(evento("rodada-preservada", 41L, EtapaSaga.T1, AcaoAuditoria.SAGA_INICIADA, 1, 1_000L));

        await().atMost(Duration.ofSeconds(10)).until(() ->
                !auditoriaUseCase.listarPorRodada("rodada-descartavel").isEmpty()
                        && !auditoriaUseCase.listarPorRodada("rodada-preservada").isEmpty());

        auditoriaUseCase.limparRodada("rodada-descartavel");

        assertThat(auditoriaUseCase.listarPorRodada("rodada-descartavel")).isEmpty();
        assertThat(auditoriaUseCase.listarPorRodada("rodada-preservada")).hasSize(1);
    }
}
