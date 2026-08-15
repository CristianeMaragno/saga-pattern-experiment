package com.tcc.saga.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * T4 — hold temporário de hotel criado (48h padrão).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldHotelCriado implements EventoSaga {

    private Long solicitacaoId;
    private Long holdId;
    private String referencia;
    private Long expiraEmMillis;
    private BigDecimal valor;
}
