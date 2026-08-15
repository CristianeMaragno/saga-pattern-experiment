package com.tcc.saga.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * T5 — pagamento do voo confirmado (primeiro passo depois do ponto de pivô).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagamentoVooConfirmado implements EventoSaga {

    private Long solicitacaoId;
    private Long pagamentoId;
    private BigDecimal valor;
}
