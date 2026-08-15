package com.tcc.approval.domain.repository;

import com.tcc.approval.domain.entity.Aprovacao;

import java.util.List;
import java.util.Optional;

/**
 * Interface do repositório para a entidade Aprovacao.
 */
public interface AprovacaoRepository {

    /**
     * Salva uma nova aprovação ou atualiza uma existente.
     *
     * @param aprovacao a aprovação a ser salva
     * @return a aprovação salva com ID atualizado
     */
    Aprovacao salvar(Aprovacao aprovacao);

    /**
     * Busca uma aprovação por ID.
     *
     * @param id o ID da aprovação
     * @return a aprovação encontrada ou vazio se não existir
     */
    Optional<Aprovacao> obterPorId(Long id);

    /**
     * Busca todas as aprovações.
     *
     * @return lista de todas as aprovações
     */
    List<Aprovacao> obterTodas();

    /**
     * Busca a aprovação de uma instância de saga.
     *
     * @param solicitacaoId identificador de correlação da saga
     * @return a aprovação encontrada ou vazio se não existir
     */
    Optional<Aprovacao> obterPorSolicitacaoId(Long solicitacaoId);
}
