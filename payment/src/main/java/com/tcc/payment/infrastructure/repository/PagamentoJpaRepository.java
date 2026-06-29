package com.tcc.payment.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tcc.payment.infrastructure.entity.PagamentoJpaEntity;

/**
 * Repositório JPA para Pagamento.
 * Fornece acesso aos dados através do Spring Data JPA e PostgreSQL.
 */
@Repository
public interface PagamentoJpaRepository extends JpaRepository<PagamentoJpaEntity, Long> {
}
