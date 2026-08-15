package com.tcc.saga.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * T2 rejeitada — fim da saga sem custo (ainda antes do ponto de pivô).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AprovacaoRejeitada implements EventoSaga {

    private Long solicitacaoId;
    private Long aprovacaoId;
    private String motivo;
}
