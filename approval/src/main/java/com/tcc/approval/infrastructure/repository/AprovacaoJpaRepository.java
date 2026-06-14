package com.tcc.approval.infrastructure.repository;

import com.tcc.approval.infrastructure.entity.AprovacaoJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositório JPA para operações com Aprovações no banco de dados.
 */
@Repository
public interface AprovacaoJpaRepository extends JpaRepository<AprovacaoJpaEntity, Long> {
}
