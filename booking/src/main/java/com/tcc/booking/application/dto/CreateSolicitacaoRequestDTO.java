package com.tcc.booking.application.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de entrada para criação de uma nova solicitação de viagem.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateSolicitacaoRequestDTO {

    @NotNull(message = "ID do usuário é obrigatório")
    private Long usuarioId;

    @NotBlank(message = "Destino é obrigatório")
    private String destino;

    @NotNull(message = "Data de ida é obrigatória")
    @FutureOrPresent(message = "Data de ida não pode ser no passado")
    private LocalDate dataIda;

    @Future(message = "Data de volta deve ser no futuro")
    private LocalDate dataVolta;

    @NotBlank(message = "Motivo é obrigatório")
    private String motivo;
}
