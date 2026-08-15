package com.tcc.saga.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Elo da cadeia backward: o payment terminou de compensar T6 e T5
 * (ou não tinha nada a compensar). Consumido pelo booking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompensacaoPagamentoConcluida implements EventoSaga {

    private Long solicitacaoId;
    private EtapaSaga etapaOrigemFalha;
}
