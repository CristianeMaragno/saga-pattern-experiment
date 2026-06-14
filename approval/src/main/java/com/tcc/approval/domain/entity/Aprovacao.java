package com.tcc.approval.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entidade de domínio que representa uma Aprovação com Responsável, Tempo Limite e Status.
 * Esta entidade contém apenas lógica e atributos de negócio,
 * sem nenhuma dependência de Spring ou frameworks de persistência.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Aprovacao {

    private Long id;
    private Long solicitanteId;
    private Long responsavelId;
    private LocalDateTime tempoLimite;
    private StatusAprovacao status;
    private LocalDateTime dataCriacao;
    private LocalDateTime dataAtualizacao;

    /**
     * Valida regras de negócio para criação da aprovação.
     * Exemplo de lógica pura de domínio.
     */
    public void validar() {
        if (solicitanteId == null || solicitanteId <= 0) {
            throw new IllegalArgumentException("ID do solicitante é obrigatório");
        }
        if (responsavelId == null || responsavelId <= 0) {
            throw new IllegalArgumentException("ID do responsável é obrigatório");
        }
        if (tempoLimite == null) {
            throw new IllegalArgumentException("Tempo limite é obrigatório");
        }
        if (tempoLimite.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Tempo limite não pode ser no passado");
        }
    }

    /**
     * Aprova a solicitação.
     */
    public void aprovar() {
        if (this.status != StatusAprovacao.PENDENTE) {
            throw new IllegalStateException("Apenas aprovações pendentes podem ser aprovadas");
        }
        this.status = StatusAprovacao.APROVADA;
        this.dataAtualizacao = LocalDateTime.now();
    }

    /**
     * Rejeita a aprovação.
     */
    public void rejeitar() {
        if (this.status != StatusAprovacao.PENDENTE) {
            throw new IllegalStateException("Apenas aprovações pendentes podem ser rejeitadas");
        }
        this.status = StatusAprovacao.REJEITADA;
        this.dataAtualizacao = LocalDateTime.now();
    }

    /**
     * Cancela a aprovação.
     */
    public void cancelar() {
        if (this.status == StatusAprovacao.CANCELADA) {
            throw new IllegalStateException("Aprovação já foi cancelada");
        }
        this.status = StatusAprovacao.CANCELADA;
        this.dataAtualizacao = LocalDateTime.now();
    }

    /**
     * Enum dos possíveis status de uma aprovação.
     */
    public enum StatusAprovacao {
        PENDENTE, APROVADA, REJEITADA, CANCELADA
    }
}
