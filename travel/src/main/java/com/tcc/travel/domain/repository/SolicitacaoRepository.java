package com.tcc.travel.domain.repository;

import java.util.List;
import java.util.Optional;

import com.tcc.travel.domain.entity.Solicitacao;

/**
 * Interface do repositório para a entidade Solicitacao.
 * Define o contrato para persistência de dados sem dependência de framework específico.
 */
public interface SolicitacaoRepository {

    /**
     * Salva uma nova solicitação ou atualiza uma existente.
     *
     * @param solicitacao a solicitação a ser salva
     * @return a solicitação salva com ID atualizado
     */
    Solicitacao salvar(Solicitacao solicitacao);

    /**
     * Busca uma solicitação pelo ID.
     *
     * @param id o ID da solicitação
     * @return a solicitação, ou vazio se não encontrada
     */
    Optional<Solicitacao> obterPorId(Long id);

    /**
     * Busca todas as solicitações.
     *
     * @return lista de todas as solicitações
     */
    List<Solicitacao> obterTodas();

    /**
     * Deleta uma solicitação pelo ID.
     *
     * @param id o ID da solicitação
     */
    void deletar(Long id);

    /**
     * Busca solicitações de um usuário específico.
     *
     * @param usuarioId ID do usuário
     * @return lista de solicitações do usuário
     */
    List<Solicitacao> obterPorUsuarioId(Long usuarioId);

    /**
     * Busca solicitações por status.
     *
     * @param status o status da solicitação
     * @return lista de solicitações com o status especificado
     */
    List<Solicitacao> obterPorStatus(Solicitacao.StatusSolicitacao status);
}
