package com.tcc.saga.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Compensação de T6 — pagamento do hotel cancelado (pode ter custo financeiro).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagamentoHotelCancelado implements EventoSaga {

    private Long solicitacaoId;
    private Long pagamentoId;
}
