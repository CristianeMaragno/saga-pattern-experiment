package com.tcc.booking.domain.entity;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidade de domínio que representa um hold temporário de reserva
 * (voo ou hotel). Contém apenas lógica e atributos de negócio,
 * sem nenhuma dependência de Spring ou frameworks de persistência.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Hold {

    private Long id;
    private HoldType type;
    private String reference;
    private Instant createdAt;
    private Instant expiresAt;

    /**
     * Tipos de hold suportados.
     */
    public enum HoldType {
        FLIGHT, HOTEL
    }
}
