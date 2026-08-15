package com.tcc.saga.event;

/**
 * Nomes dos canais Eventuate Tram (que viram tópicos Kafka) usados pela saga.
 *
 * O Eventuate Tram usa o "aggregate type" como canal e o nome da classe do
 * evento como cabeçalho, então os nomes de negócio da convenção do plano
 * (solicitacao.criada, aprovacao.aprovada, ...) viram canal + classe de evento.
 */
public final class CanaisSaga {

    public static final String SOLICITACAO = "solicitacao";
    public static final String APROVACAO = "aprovacao";
    public static final String HOLD = "hold";
    public static final String PAGAMENTO = "pagamento";
    public static final String SAGA = "saga";
    public static final String AUDITORIA = "saga-audit";

    private CanaisSaga() {
    }
}
