package com.tcc.saga.recuperacao;

import com.tcc.saga.event.EtapaSaga;

/**
 * Parte específica de cada serviço na execução de uma etapa que pode falhar.
 *
 * O {@link MotorRecuperacao} cuida do que é igual em todos os serviços
 * (quando executar, quando repetir, quando desistir e qual estratégia aplicar);
 * o executor cuida do que é do domínio (o que a transação local faz e quais
 * eventos ela publica).
 */
public interface ExecutorEtapa {

    /** Etapa da saga que este executor implementa. */
    EtapaSaga etapa();

    /**
     * Executa a transação local. Deve chamar o injetor de falhas antes de
     * qualquer efeito colateral e publicar o evento de sucesso ao final.
     *
     * @param pendente registro da etapa, com o payload do evento que a originou
     * @param tentativa número desta execução, começando em 1
     */
    void executar(EtapaPendenteJpaEntity pendente, int tentativa);

    /**
     * Chamado quando não há mais recuperação possível, para o serviço publicar
     * o evento que encerra a saga conforme a decisão da estratégia ativa.
     */
    void aoFalharDefinitivamente(EtapaPendenteJpaEntity pendente,
                                 int tentativa,
                                 boolean permanente,
                                 String motivo,
                                 DecisaoFalha decisao);

    /**
     * O que fazer com a saga quando a etapa falha em definitivo.
     */
    enum DecisaoFalha {
        /** Dispara a cadeia backward, desfazendo as transações já concluídas. */
        COMPENSAR,
        /** Encerra a saga sem desfazer nada (Forward puro com tentativas esgotadas). */
        ABORTAR
    }
}
