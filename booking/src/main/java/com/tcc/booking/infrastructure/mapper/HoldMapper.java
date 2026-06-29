package com.tcc.booking.infrastructure.mapper;

import org.springframework.stereotype.Component;

import com.tcc.booking.domain.entity.Hold;
import com.tcc.booking.infrastructure.entity.HoldJpaEntity;

/**
 * Mapper para converter entre a entidade de domínio (Hold)
 * e a entidade JPA (HoldJpaEntity).
 */
@Component
public class HoldMapper {

    /**
     * Converte uma entidade JPA para entidade de domínio.
     */
    public Hold toDomain(HoldJpaEntity jpaEntity) {
        if (jpaEntity == null) {
            return null;
        }

        return Hold.builder()
                .id(jpaEntity.getId())
                .type(jpaEntity.getType() != null ?
                        Hold.HoldType.valueOf(jpaEntity.getType().name())
                        : null)
                .reference(jpaEntity.getReference())
                .createdAt(jpaEntity.getCreatedAt())
                .expiresAt(jpaEntity.getExpiresAt())
                .build();
    }

    /**
     * Converte uma entidade de domínio para entidade JPA.
     */
    public HoldJpaEntity toJpa(Hold domain) {
        if (domain == null) {
            return null;
        }

        return HoldJpaEntity.builder()
                .id(domain.getId())
                .type(domain.getType() != null ?
                        HoldJpaEntity.HoldType.valueOf(domain.getType().name())
                        : null)
                .reference(domain.getReference())
                .createdAt(domain.getCreatedAt())
                .expiresAt(domain.getExpiresAt())
                .build();
    }
}
