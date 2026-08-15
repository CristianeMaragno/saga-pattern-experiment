package com.tcc.saga.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * T2 — aprovação criada e aguardando decisão (aprovação de longa duração).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AprovacaoSolicitada implements EventoSaga {

    private Long solicitacaoId;
    private Long aprovacaoId;
    private Long responsavelId;
}
