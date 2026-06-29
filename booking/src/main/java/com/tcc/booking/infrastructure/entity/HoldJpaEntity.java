package com.tcc.booking.infrastructure.entity;

import java.time.Instant;

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

/**
 * Entidade JPA que representa um Hold de reserva no banco de dados.
 */
@Entity
@Table(name = "holds")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private HoldType type;

    @Column(name = "reference", nullable = false, length = 255)
    private String reference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Tipos de hold suportados.
     */
    public enum HoldType {
        FLIGHT, HOTEL
    }
}
