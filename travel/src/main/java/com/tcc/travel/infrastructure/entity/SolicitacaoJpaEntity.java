package com.tcc.travel.infrastructure.entity;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidade JPA que representa uma Solicitação de Viagem no banco de dados.
 */
@Entity
@Table(name = "solicitacoes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitacaoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "destino", nullable = false, length = 255)
    private String destino;

    @Column(name = "data_ida", nullable = false)
    private LocalDate dataIda;

    @Column(name = "data_volta")
    private LocalDate dataVolta;

    @Column(name = "motivo", nullable = false, columnDefinition = "TEXT")
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusSolicitacao status;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado_saga", length = 20)
    private ResultadoSaga resultadoSaga;

    @Column(name = "etapa_falha", length = 10)
    private String etapaFalha;

    @Column(name = "valor_voo", precision = 12, scale = 2)
    private BigDecimal valorVoo;

    @Column(name = "valor_hotel", precision = 12, scale = 2)
    private BigDecimal valorHotel;

    @Column(name = "pagamento_voo_confirmado", nullable = false)
    private boolean pagamentoVooConfirmado;

    @Column(name = "pagamento_hotel_confirmado", nullable = false)
    private boolean pagamentoHotelConfirmado;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    /**
     * Enum dos possíveis status de uma solicitação.
     */
    public enum StatusSolicitacao {
        RASCUNHO, PENDENTE, APROVADA, REJEITADA, CONFIRMADO, CANCELADA
    }

    /**
     * Desfecho da instância de saga.
     */
    public enum ResultadoSaga {
        SUCESSO, COMPENSADA, ABORTADA, REJEITADA
    }
}
