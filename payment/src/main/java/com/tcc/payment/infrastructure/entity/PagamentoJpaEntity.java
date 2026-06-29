package com.tcc.payment.infrastructure.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidade JPA que representa um Pagamento no banco de dados.
 */
@Entity
@Table(name = "pagamentos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagamentoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoPagamento tipo;

    @Column(name = "referencia", nullable = false, length = 255)
    private String referencia;

    @Column(name = "valor", nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusPagamento status;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

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
