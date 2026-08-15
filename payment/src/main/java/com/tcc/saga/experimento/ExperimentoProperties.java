package com.tcc.saga.experimento;

import com.tcc.saga.event.EtapaSaga;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração da rodada de experimento em execução.
 *
 * Todos os fatores experimentais são lidos de variáveis de ambiente (mapeadas
 * no application.yaml de cada serviço), nunca hardcoded e nunca por endpoint
 * administrativo — é o que permite ao script da matriz de experimentos trocar
 * de combinação apenas reiniciando os containers.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "experimento")
public class ExperimentoProperties {

    /** Identifica a rodada; vira uma coluna da tabela de auditoria e nomeia o CSV exportado. */
    private String runId = "local";

    /** Estratégia de recuperação ativa (RECOVERY_STRATEGY). */
    private EstrategiaRecuperacao estrategia = EstrategiaRecuperacao.BACKWARD;

    /** Se e que tipo de falha é injetada (FAULT_SCENARIO). */
    private CenarioFalha cenarioFalha = CenarioFalha.NONE;

    /** Onde a falha é injetada (FAULT_POSITION); ignorado quando o cenário é NONE. */
    private EtapaSaga posicaoFalha = EtapaSaga.T4;

    /**
     * Tentativas adicionais depois da primeira execução, nos modos Forward e
     * Híbrido (RECOVERY_RETRY_LIMIT). No modo Backward é ignorado: a falha
     * dispara compensação imediatamente, sem retry.
     */
    private int limiteTentativas = 3;

    /** Backoff exponencial: espera = backoffInicialMs * 2^(tentativa-1). */
    private long backoffInicialMs = 200;

    /** Teto do backoff exponencial, para não estourar o tempo da rodada. */
    private long backoffMaximoMs = 5000;

    /**
     * Quantas execuções iniciais uma falha TEMPORARY derruba antes de se
     * curar sozinha. Precisa ser menor que {@code limiteTentativas} para que o
     * Forward tenha como se recuperar — se fosse sempre falha, a falha não
     * seria temporária e os modos ficariam indistinguíveis.
     */
    private int falhasTemporarias = 2;

    /**
     * Tempo simulado da decisão de aprovação (APPROVAL_DECISION_DELAY_SECONDS).
     * Substitui a espera real de dias descrita na metodologia, que é inviável
     * em um teste de carga automatizado.
     */
    private int atrasoDecisaoAprovacaoSegundos = 2;

    /** Valor do voo usado por T5, fixo para manter as rodadas determinísticas. */
    private java.math.BigDecimal valorVoo = new java.math.BigDecimal("1500.00");

    /** Valor do hotel usado por T6, fixo para manter as rodadas determinísticas. */
    private java.math.BigDecimal valorHotel = new java.math.BigDecimal("800.00");

    /**
     * Estratégias de recuperação comparadas pelo experimento.
     */
    public enum EstrategiaRecuperacao {
        /** Desfaz (compensa) todas as transações já concluídas da saga. */
        BACKWARD,
        /** Tenta novamente sem desfazer nada; tentativas esgotadas abortam a saga. */
        FORWARD,
        /** Tenta forward até o limite e, se não resolver, cai para backward. */
        HYBRID
    }

    /**
     * Tipos de falha injetada.
     */
    public enum CenarioFalha {
        /** Caminho feliz, sem injeção. */
        NONE,
        /** Recuperável: falha nas primeiras execuções e depois se cura. */
        TEMPORARY,
        /** Não recuperável: rejeição de negócio, falha em toda execução. */
        PERMANENT
    }
}
