package com.tcc.saga.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * T1 — solicitação de viagem criada (estado RASCUNHO). Dispara a saga.

 As datas trafegam como String ISO-8601 e instantes como epoch millis porque o
 ObjectMapper do Eventuate Tram não registra o módulo JSR-310.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitacaoCriada implements EventoSaga {

    private Long solicitacaoId;
    private Long usuarioId;
    private String destino;
    private String dataIda;
    private String dataVolta;
    private String motivo;
    private BigDecimal valorVoo;
    private BigDecimal valorHotel;
}
