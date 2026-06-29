package com.tcc.booking.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tcc.booking.infrastructure.entity.HoldJpaEntity;

/**
 * Repositório JPA para Hold.
 * Fornece acesso aos dados através do Spring Data JPA e PostgreSQL.
 */
@Repository
public interface HoldJpaRepository extends JpaRepository<HoldJpaEntity, Long> {
}
