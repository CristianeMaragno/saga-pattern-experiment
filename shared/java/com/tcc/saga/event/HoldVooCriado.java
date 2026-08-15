package com.tcc.saga.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * T3 — hold temporário de voo criado (24h–72h).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldVooCriado implements EventoSaga {

    private Long solicitacaoId;
    private Long holdId;
    private String referencia;
    private Long expiraEmMillis;
    private BigDecimal valor;
}
