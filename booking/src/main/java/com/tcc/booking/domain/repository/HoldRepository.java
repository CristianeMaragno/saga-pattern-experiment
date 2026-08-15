package com.tcc.booking.domain.repository;

import java.util.Optional;

import com.tcc.booking.domain.entity.Hold;

/**
 * Interface do repositório para a entidade Hold.
 * Define o contrato para persistência de dados sem dependência de framework específico.
 */
public interface HoldRepository {

    /**
     * Salva um novo hold.
     *
     * @param hold o hold a ser salvo
     * @return o hold salvo com ID atualizado
     */
    Hold salvar(Hold hold);

    /**
     * Busca um hold pelo ID.
     *
     * @param id o ID do hold
     * @return o hold, ou vazio se não encontrado
     */
    Optional<Hold> obterPorId(Long id);

    /**
     * Busca o hold de um tipo específico dentro de uma instância de saga.
     *
     * @param solicitacaoId identificador de correlação da saga
     * @param type tipo do hold (voo ou hotel)
     * @return o hold, ou vazio se não encontrado
     */
    Optional<Hold> obterPorSolicitacaoIdETipo(Long solicitacaoId, Hold.HoldType type);
}
