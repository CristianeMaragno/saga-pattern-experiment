package com.tcc.saga.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * T6 — pagamento do hotel confirmado.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagamentoHotelConfirmado implements EventoSaga {

    private Long solicitacaoId;
    private Long pagamentoId;
    private BigDecimal valor;
}
