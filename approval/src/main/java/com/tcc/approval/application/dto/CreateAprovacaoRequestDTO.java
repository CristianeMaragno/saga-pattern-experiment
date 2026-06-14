package com.tcc.approval.application.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO de entrada para criação de uma nova aprovação.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAprovacaoRequestDTO {

    @NotNull(message = "ID do solicitante é obrigatório")
    private Long solicitanteId;

    @NotNull(message = "ID do responsável é obrigatório")
    private Long responsavelId;

    @NotNull(message = "Tempo limite é obrigatório")
    @Future(message = "Tempo limite deve ser no futuro")
    private LocalDateTime tempoLimite;
}
