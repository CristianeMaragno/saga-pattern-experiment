package com.tcc.saga.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Compensação de T3 — hold de voo liberado.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldVooLiberado implements EventoSaga {

    private Long solicitacaoId;
    private Long holdId;
}
