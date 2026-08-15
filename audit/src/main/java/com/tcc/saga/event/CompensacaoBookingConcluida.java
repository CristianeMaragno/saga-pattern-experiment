package com.tcc.saga.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Elo da cadeia backward: o booking terminou de compensar T4 e T3
 * (ou não tinha nada a compensar). Consumido pelo approval.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompensacaoBookingConcluida implements EventoSaga {

    private Long solicitacaoId;
    private EtapaSaga etapaOrigemFalha;
}
