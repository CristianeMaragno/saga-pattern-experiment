package com.tcc.payment.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidade de domínio que representa um Pagamento (voo ou hotel).
 * Esta entidade contém apenas lógica e atributos de negócio,
 * sem nenhuma dependência de Spring ou frameworks de persistência.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Pagamento {

    private Long id;
    /** Identificador de correlação da instância de saga. */
    private Long solicitacaoId;
    private TipoPagamento tipo;
    private String referencia;
    private BigDecimal valor;
    private StatusPagamento status;
    private LocalDateTime dataCriacao;
    private LocalDateTime dataAtualizacao;

    /**
     * Valida regras de negócio para criação do pagamento.
     */
    public void validar() {
        if (solicitacaoId == null || solicitacaoId <= 0) {
            throw new IllegalArgumentException("ID da solicitação é obrigatório");
        }
        if (referencia == null || referencia.isBlank()) {
            throw new IllegalArgumentException("Referência é obrigatória");
        }
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valor deve ser positivo");
        }
    }

    /**
     * Confirma a reserva e processa o pagamento (T5: voo / T6: hotel).
     */
    public void confirmar() {
        if (this.status != StatusPagamento.PENDENTE) {
            throw new IllegalStateException("Apenas pagamentos pendentes podem ser confirmados");
        }
        this.status = StatusPagamento.CONFIRMADO;
        this.dataAtualizacao = LocalDateTime.now();
    }

    /**
     * Marca o pagamento como falho.
     */
    public void falhar() {
        if (this.status != StatusPagamento.PENDENTE) {
            throw new IllegalStateException("Apenas pagamentos pendentes podem falhar");
        }
        this.status = StatusPagamento.FALHOU;
        this.dataAtualizacao = LocalDateTime.now();
    }

    /**
     * Compensação de T5/T6: estorna o pagamento.
     *
     * Diferente das compensações de T1–T4, esta acontece depois do ponto de
     * pivô e é a que pode ter custo financeiro (multas) no domínio real.
     * Idempotente, porque o evento de compensação pode ser reentregue.
     *
     * @return {@code true} se este chamado de fato cancelou o pagamento
     */
    public boolean cancelar() {
        // Um pagamento recusado não tem o que estornar, e sobrescrevê-lo com
        // CANCELADO apagaria justamente o registro da recusa — que é o dado que
        // distingue "a saga foi desfeita" de "o pagamento foi negado".
        if (this.status == StatusPagamento.CANCELADO || this.status == StatusPagamento.FALHOU) {
            return false;
        }
        this.status = StatusPagamento.CANCELADO;
        this.dataAtualizacao = LocalDateTime.now();
        return true;
    }

    /**
     * Tipos de pagamento suportados.
     */
    public enum TipoPagamento {
        VOO, HOTEL
    }

    /**
     * Enum dos possíveis status de um pagamento.
     */
    public enum StatusPagamento {
        PENDENTE, CONFIRMADO, FALHOU, CANCELADO
    }
}
