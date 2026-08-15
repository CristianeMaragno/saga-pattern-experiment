package com.tcc.saga.experimento;

import com.tcc.saga.event.EtapaSaga;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Injeta a falha configurada para a rodada, de forma determinística.
 *
 * Cada serviço chama {@link #verificar} imediatamente antes de executar a
 * transação local correspondente à sua posição na saga. Se a posição
 * configurada não for a dele, nada acontece.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InjetorFalha {

    private final ExperimentoProperties experimento;

    /**
     * Falha a execução se a rodada atual pedir uma falha nesta etapa.
     *
     * @param etapa      etapa que está prestes a ser executada
     * @param tentativa  número da execução, começando em 1
     * @throws FalhaTemporariaException se a falha configurada for recuperável e ainda não tiver se curado
     * @throws FalhaPermanenteException se a falha configurada for definitiva
     */
    public void verificar(EtapaSaga etapa, Long solicitacaoId, int tentativa) {
        if (experimento.getCenarioFalha() == ExperimentoProperties.CenarioFalha.NONE) {
            return;
        }
        if (experimento.getPosicaoFalha() != etapa) {
            return;
        }

        if (experimento.getCenarioFalha() == ExperimentoProperties.CenarioFalha.PERMANENT) {
            log.warn("[FALHA PERMANENTE] etapa={} solicitacao={} tentativa={}", etapa, solicitacaoId, tentativa);
            throw new FalhaPermanenteException(
                    "Falha permanente injetada em " + etapa + " (" + etapa.getDescricao() + ")");
        }

        // TEMPORARY: derruba as primeiras execuções e depois se cura sozinha,
        // para que Forward e Híbrido tenham de fato como se recuperar.
        if (tentativa <= experimento.getFalhasTemporarias()) {
            log.warn("[FALHA TEMPORÁRIA] etapa={} solicitacao={} tentativa={} (cura na tentativa {})",
                    etapa, solicitacaoId, tentativa, experimento.getFalhasTemporarias() + 1);
            throw new FalhaTemporariaException(
                    "Falha temporária injetada em " + etapa + " na tentativa " + tentativa);
        }

        log.info("[FALHA TEMPORÁRIA CURADA] etapa={} solicitacao={} tentativa={}", etapa, solicitacaoId, tentativa);
    }

    /**
     * Indica se esta instância é a responsável pela falha da rodada — usado
     * para decidir se vale a pena instrumentar o caminho de recuperação.
     */
    public boolean falhaConfiguradaPara(EtapaSaga etapa) {
        return experimento.getCenarioFalha() != ExperimentoProperties.CenarioFalha.NONE
                && experimento.getPosicaoFalha() == etapa;
    }
}
