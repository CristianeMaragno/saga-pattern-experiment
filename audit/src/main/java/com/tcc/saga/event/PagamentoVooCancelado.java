package com.tcc.saga.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Compensação de T5 — pagamento do voo cancelado (pode ter custo financeiro).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagamentoVooCancelado implements EventoSaga {

    private Long solicitacaoId;
    private Long pagamentoId;
}
