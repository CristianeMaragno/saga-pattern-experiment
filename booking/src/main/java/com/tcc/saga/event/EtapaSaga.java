package com.tcc.saga.event;

/**
 * Transações locais da saga S_criacao = ⟨T1..T7⟩.
 *
 * T1–T4 criam apenas reservas temporárias (compensação sem custo).
 * A partir de T5 (ponto de pivô) a compensação pode ter custo financeiro.
 */
public enum EtapaSaga {

    T1("Criar requisição de viagem", "travel"),
    T2("Solicitar aprovação", "approval"),
    T3("Reserva de voo (hold)", "booking"),
    T4("Reserva de hotel (hold)", "booking"),
    T5("Pagamento do voo", "payment"),
    T6("Pagamento do hotel", "payment"),
    T7("Confirmar viagem", "travel");

    private final String descricao;
    private final String servico;

    EtapaSaga(String descricao, String servico) {
        this.descricao = descricao;
        this.servico = servico;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getServico() {
        return servico;
    }

    /** Etapas a partir das quais a compensação pode ter custo financeiro. */
    public boolean isAposPivo() {
        return ordinal() >= T5.ordinal();
    }
}
