package com.tcc.payment.application.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de entrada para criação de um pagamento (voo ou hotel).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePagamentoRequestDTO {

    @NotBlank(message = "Referência é obrigatória")
    private String referencia;

    @NotNull(message = "Valor é obrigatório")
    @Positive(message = "Valor deve ser positivo")
    private BigDecimal valor;
}
