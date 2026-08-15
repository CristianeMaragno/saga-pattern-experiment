package com.tcc.saga.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * T2 concluída com sucesso — libera os ramos paralelos de voo (T3/T5) e hotel (T4/T6).

 Carrega os valores adiante porque o pagamento (T5/T6) precisa deles e não há
 chamada síncrona entre serviços.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AprovacaoAprovada implements EventoSaga {

    private Long solicitacaoId;
    private Long aprovacaoId;
    private BigDecimal valorVoo;
    private BigDecimal valorHotel;
}
