package com.tcc.saga.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Compensação de T2 — último elo da cadeia backward antes de T1.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AprovacaoCancelada implements EventoSaga {

    private Long solicitacaoId;
    private Long aprovacaoId;
    private EtapaSaga etapaOrigemFalha;
}
