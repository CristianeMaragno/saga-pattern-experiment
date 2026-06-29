package com.tcc.booking.application.dto;

import java.time.Instant;

import com.tcc.booking.domain.entity.Hold;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de saída que representa um hold de reserva.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldResponseDTO {

    private Long id;
    private String type;
    private String reference;
    private Instant createdAt;
    private Instant expiresAt;

    /**
     * Converte uma entidade de domínio em DTO de resposta.
     */
    public static HoldResponseDTO fromDomain(Hold hold) {
        return HoldResponseDTO.builder()
                .id(hold.getId())
                .type(hold.getType() != null ? hold.getType().name() : null)
                .reference(hold.getReference())
                .createdAt(hold.getCreatedAt())
                .expiresAt(hold.getExpiresAt())
                .build();
    }
}
