package com.tcc.booking.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de entrada para criação de um hold (voo ou hotel).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateHoldRequestDTO {

    @NotBlank(message = "Referência é obrigatória")
    private String reference;

    /**
     * Duração do hold em horas. Opcional: se não informada, o caso de uso
     * aplica o valor padrão (mínimo de voo ou padrão de hotel).
     */
    @Positive(message = "Duração deve ser positiva")
    private Integer durationHours;
}
