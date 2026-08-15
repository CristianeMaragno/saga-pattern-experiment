package com.tcc.saga.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Falha que exige compensação: dispara a cadeia backward.
 *
 * A cadeia percorre o grafo de eventos de trás para frente e é coreografada —
 * cada serviço reage ao elo anterior, não existe um orquestrador chamando as
 * compensações. A ordem é sempre a mesma (payment → booking → approval →
 * travel) mesmo quando a falha ocorre cedo: os serviços que ainda não
 * executaram nada apenas repassam a cadeia adiante.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaFalhou implements EventoSaga {

    private Long solicitacaoId;
    private EtapaSaga etapa;
    private String servico;
    private String motivo;
    /** {@code true} quando a falha é definitiva (rejeição de negócio, tentativas esgotadas). */
    private boolean permanente;
    private int tentativas;
}
