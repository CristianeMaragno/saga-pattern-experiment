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
}
