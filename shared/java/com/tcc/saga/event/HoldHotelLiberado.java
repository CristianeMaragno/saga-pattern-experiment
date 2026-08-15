package com.tcc.saga.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Compensação de T4 — hold de hotel liberado.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldHotelLiberado implements EventoSaga {

    private Long solicitacaoId;
    private Long holdId;
}
