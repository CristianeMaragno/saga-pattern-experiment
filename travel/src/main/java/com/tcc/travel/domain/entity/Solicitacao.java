package com.tcc.travel.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidade de domínio que representa uma Solicitação de Viagem.
 * Esta entidade contém apenas lógica e atributos de negócio,
 * sem nenhuma dependência de Spring ou frameworks de persistência.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Solicitacao {

    private Long id;
    private Long usuarioId;
    private String destino;
    private LocalDate dataIda;
    private LocalDate dataVolta;
    private String motivo;
    private StatusSolicitacao status;
    private LocalDateTime dataCriacao;
    private LocalDateTime dataAtualizacao;

    /**
     * Valida regras de negócio para criação da solicitação.
     * Exemplo de lógica pura de domínio.
     */
    public void validar() {
        if (destino == null || destino.isBlank()) {
            throw new IllegalArgumentException("Destino é obrigatório");
        }
        if (dataIda == null) {
            throw new IllegalArgumentException("Data de ida é obrigatória");
        }
        if (dataVolta != null && dataVolta.isBefore(dataIda)) {
            throw new IllegalArgumentException("Data de volta não pode ser anterior à data de ida");
        }
    }

    /**
     * Aprova a solicitação.
     */
    public void aprovar() {
        if (this.status != StatusSolicitacao.PENDENTE) {
            throw new IllegalStateException("Apenas solicitações pendentes podem ser aprovadas");
        }
        this.status = StatusSolicitacao.APROVADA;
    }

    /**
     * Rejeita a solicitação.
     */
    public void rejeitar() {
        if (this.status != StatusSolicitacao.PENDENTE) {
            throw new IllegalStateException("Apenas solicitações pendentes podem ser rejeitadas");
        }
        this.status = StatusSolicitacao.REJEITADA;
    }

    /**
     * Enum dos possíveis status de uma solicitação.
     */
    public enum StatusSolicitacao {
        PENDENTE, APROVADA, REJEITADA, CANCELADA
    }
}
