package com.tcc.approval.infrastructure.entity;

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

import java.time.LocalDateTime;

/**
 * Entidade JPA que representa uma Aprovação no banco de dados.
 */
@Entity
@Table(name = "aprovacoes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AprovacaoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "solicitante_id", nullable = false)
    private Long solicitanteId;

    @Column(name = "responsavel_id", nullable = false)
    private Long responsavelId;

    @Column(name = "tempo_limite", nullable = false)
    private LocalDateTime tempoLimite;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusAprovacao status;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    /**
     * Enum dos possíveis status de uma aprovação.
     */
    public enum StatusAprovacao {
        PENDENTE, APROVADA, REJEITADA, CANCELADA
    }
}
