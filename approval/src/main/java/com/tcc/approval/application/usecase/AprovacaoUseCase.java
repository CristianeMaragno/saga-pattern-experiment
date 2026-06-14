package com.tcc.approval.application.usecase;

import com.tcc.approval.application.dto.CreateAprovacaoRequestDTO;
import com.tcc.approval.domain.entity.Aprovacao;
import com.tcc.approval.domain.exception.ResourceNotFoundException;
import com.tcc.approval.domain.repository.AprovacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Caso de uso para operações com Aprovações.
 */
@Service
@RequiredArgsConstructor
public class AprovacaoUseCase {

    private final AprovacaoRepository aprovacaoRepository;

    /**
     * Cria uma nova aprovação.
     * 
     * @param request dados de entrada validados da requisição HTTP
     * @return a aprovação criada
     */
    public Aprovacao criarAprovacao(CreateAprovacaoRequestDTO request) {
        Aprovacao aprovacao = Aprovacao.builder()
                .solicitanteId(request.getSolicitanteId())
                .responsavelId(request.getResponsavelId())
                .tempoLimite(request.getTempoLimite())
                .status(Aprovacao.StatusAprovacao.PENDENTE)
                .dataCriacao(LocalDateTime.now())
                .build();

        // Valida regras de negócio
        aprovacao.validar();

        // Persiste a aprovação
        return aprovacaoRepository.salvar(aprovacao);
    }

    /**
     * Obtém uma aprovação por ID.
     * 
     * @param id o ID da aprovação
     * @return a aprovação encontrada
     * @throws ResourceNotFoundException se a aprovação não for encontrada
     */
    public Aprovacao obterAprovacao(Long id) {
        return aprovacaoRepository.obterPorId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aprovação não encontrada com ID: " + id));
    }

    /**
     * Obtém todas as aprovações.
     * 
     * @return lista de todas as aprovações
     */
    public List<Aprovacao> obterTodasAprovacoes() {
        return aprovacaoRepository.obterTodas();
    }

    /**
     * Aprova uma aprovação.
     * 
     * @param id o ID da aprovação
     * @return a aprovação aprovada
     * @throws ResourceNotFoundException se a aprovação não for encontrada
     */
    public Aprovacao aprovarAprovacao(Long id) {
        Aprovacao aprovacao = obterAprovacao(id);
        aprovacao.aprovar();
        return aprovacaoRepository.salvar(aprovacao);
    }

    /**
     * Rejeita uma aprovação.
     * 
     * @param id o ID da aprovação
     * @return a aprovação rejeitada
     * @throws ResourceNotFoundException se a aprovação não for encontrada
     */
    public Aprovacao rejeitarAprovacao(Long id) {
        Aprovacao aprovacao = obterAprovacao(id);
        aprovacao.rejeitar();
        return aprovacaoRepository.salvar(aprovacao);
    }

    /**
     * Cancela uma aprovação.
     * 
     * @param id o ID da aprovação
     * @return a aprovação cancelada
     * @throws ResourceNotFoundException se a aprovação não for encontrada
     */
    public Aprovacao cancelarAprovacao(Long id) {
        Aprovacao aprovacao = obterAprovacao(id);
        aprovacao.cancelar();
        return aprovacaoRepository.salvar(aprovacao);
    }
}
