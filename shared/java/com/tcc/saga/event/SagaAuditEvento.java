package com.tcc.saga.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento de auditoria emitido a cada transição relevante da saga.
 *
 * É a fonte real das métricas da monografia (taxa de sucesso, tempo de
 * conclusão, tempo de recuperação, nº de compensações, nº de retries,
 * throughput, volume de eventos, tempo até consistência eventual): todas são
 * deriváveis por agregação sobre a tabela única alimentada por estes eventos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaAuditEvento implements EventoSaga {

    /** Identifica a rodada do experimento, para exportar um CSV por combinação da matriz. */
    private String runId;
    private Long solicitacaoId;
    private String servico;
    private EtapaSaga etapa;
    private AcaoAuditoria acao;
    private int tentativa;
    private String estrategia;
    private String cenarioFalha;
    private String posicaoFalha;
    private String mensagem;
    private long timestampMillis;

    /**
     * O que aconteceu com a transação local no momento registrado.
     */
    public enum AcaoAuditoria {
        /** Início da saga (T1). */
        SAGA_INICIADA,
        /** Início de uma transação local. */
        INICIO,
        /** Transação local concluída com sucesso. */
        SUCESSO,
        /** Transação local falhou (pode ainda ser recuperada). */
        FALHA,
        /** Nova tentativa de uma transação local que falhou (Forward/Híbrido). */
        RETRY,
        /** Transação local compensada (Backward). */
        COMPENSACAO,
        /** Saga terminou com sucesso (T7). */
        SAGA_CONCLUIDA,
        /** Saga terminou revertida pela cadeia backward. */
        SAGA_COMPENSADA,
        /** Saga terminou sem compensação, com tentativas esgotadas (Forward puro). */
        SAGA_ABORTADA,
        /** Saga terminou por rejeição de negócio antes do ponto de pivô. */
        SAGA_REJEITADA
    }
}
