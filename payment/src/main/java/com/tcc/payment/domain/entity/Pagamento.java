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
