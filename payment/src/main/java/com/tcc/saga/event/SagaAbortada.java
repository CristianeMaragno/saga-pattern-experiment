package com.tcc.saga.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Falha no modo Forward Recovery com as tentativas esgotadas.
 *
 * Diferente de {@link SagaFalhou}, este evento NÃO dispara compensação: o
 * Forward puro não desfaz o que já foi feito, então os holds e pagamentos já
 * concluídos permanecem no estado em que estavam. Só o travel reage, para dar
 * um estado terminal à saga e permitir o cálculo das métricas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaAbortada implements EventoSaga {

    private Long solicitacaoId;
    private EtapaSaga etapa;
    private String servico;
    private String motivo;
    private int tentativas;
}
