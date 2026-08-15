package com.tcc.audit.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Métricas do experimento, derivadas por agregação sobre a tabela de auditoria.
 *
 * Cobre a lista da seção de métricas da metodologia: taxa de sucesso, tempo
 * médio de conclusão, tempo médio de recuperação, número de compensações e de
 * retries, throughput, volume de eventos, percentual de sagas recuperadas sem
 * compensação e tempo até a consistência eventual.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricasResponseDTO {

    private String runId;
    private String estrategia;
    private String cenarioFalha;
    private String posicaoFalha;

    /** Sagas iniciadas (T1). */
    private long totalSagas;
    private long sagasConcluidas;
    private long sagasCompensadas;
    private long sagasAbortadas;
    private long sagasRejeitadas;
    /** Sagas que iniciaram mas ainda não atingiram estado terminal. */
    private long sagasEmAndamento;

    /** sagasConcluidas / totalSagas. */
    private double taxaSucesso;

    /** Média de (fim − início) das sagas concluídas com sucesso. */
    private double tempoMedioConclusaoMs;

    /**
     * Média de (fim − primeira falha) das sagas que sofreram ao menos uma
     * falha: quanto tempo o sistema leva para chegar a um desfecho depois que
     * o problema aparece, seja recuperando, compensando ou abortando.
     */
    private double tempoMedioRecuperacaoMs;

    /**
     * Média de (fim − início) de todas as sagas que atingiram estado terminal,
     * independentemente do desfecho: é o tempo até a consistência eventual.
     */
    private double tempoAteConsistenciaEventualMs;

    private long totalCompensacoes;
    private long totalRetries;
    /** Registros de auditoria da rodada; serve de proxy do volume de eventos. */
    private long totalEventos;

    /** Sagas que falharam ao menos uma vez e ainda assim concluíram com sucesso. */
    private long sagasComFalha;
    private long sagasRecuperadasSemCompensacao;
    private double percentualRecuperadasSemCompensacao;

    /** Sagas iniciadas por segundo, do primeiro ao último evento da rodada. */
    private double throughputSagasPorSegundo;
    private long duracaoRodadaMs;
}
