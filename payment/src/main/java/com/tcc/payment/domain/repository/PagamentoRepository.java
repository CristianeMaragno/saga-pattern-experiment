package com.tcc.payment.domain.repository;

import java.util.List;
import java.util.Optional;

import com.tcc.payment.domain.entity.Pagamento;

/**
 * Interface do repositório para a entidade Pagamento.
 * Define o contrato para persistência de dados sem dependência de framework específico.
 */
public interface PagamentoRepository {

    /**
     * Salva um novo pagamento ou atualiza um existente.
     *
     * @param pagamento o pagamento a ser salvo
     * @return o pagamento salvo com ID atualizado
     */
    Pagamento salvar(Pagamento pagamento);

    /**
     * Busca um pagamento pelo ID.
     *
     * @param id o ID do pagamento
     * @return o pagamento, ou vazio se não encontrado
     */
    Optional<Pagamento> obterPorId(Long id);

    /**
     * Busca todos os pagamentos.
     *
     * @return lista de todos os pagamentos
     */
    List<Pagamento> obterTodos();
}
