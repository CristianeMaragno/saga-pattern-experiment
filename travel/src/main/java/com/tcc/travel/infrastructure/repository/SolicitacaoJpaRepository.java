package com.tcc.travel.infrastructure.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tcc.travel.infrastructure.entity.SolicitacaoJpaEntity;

/**
 * Repositório JPA para Solicitação de Viagem.
 * Fornece acesso aos dados através do Spring Data JPA e PostgreSQL.
 */
@Repository
public interface SolicitacaoJpaRepository extends JpaRepository<SolicitacaoJpaEntity, Long> {
    
    /**
     * Busca todas as solicitações de um usuário específico.
     *
     * @param usuarioId ID do usuário
     * @return Lista de solicitações do usuário
     */
    List<SolicitacaoJpaEntity> findByUsuarioId(Long usuarioId);
    
    /**
     * Busca solicitações por status.
     *
     * @param status Status da solicitação
     * @return Lista de solicitações com o status especificado
     */
    List<SolicitacaoJpaEntity> findByStatus(String status);
}
