package com.tcc.travel.application.usecase;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tcc.travel.application.dto.CreateSolicitacaoRequestDTO;
import com.tcc.travel.domain.entity.Solicitacao;
import com.tcc.travel.domain.repository.SolicitacaoRepository;

import lombok.RequiredArgsConstructor;

/**
 * Caso de uso para operações com Solicitações de Viagem.
 */
@Service
@RequiredArgsConstructor
public class SolicitacaoUseCase {

    private final SolicitacaoRepository solicitacaoRepository;

    /**
     * Cria uma nova solicitação de viagem.
     * 
     * @param request dados de entrada validados da requisição HTTP
     * @return a solicitação criada
     */
    public Solicitacao criarSolicitacao(CreateSolicitacaoRequestDTO request) {
        Solicitacao solicitacao = Solicitacao.builder()
                .usuarioId(request.getUsuarioId())
                .destino(request.getDestino())
                .dataIda(request.getDataIda())
                .dataVolta(request.getDataVolta())
                .motivo(request.getMotivo())
                .status(Solicitacao.StatusSolicitacao.PENDENTE)
                .dataCriacao(LocalDateTime.now())
                .build();

        // Valida regras de negócio
        solicitacao.validar();

        // Persiste a solicitação
        return solicitacaoRepository.salvar(solicitacao);
    }

    /**
     * Lista todas as solicitações de viagem.
     *
     * @return lista de solicitações
     */
    public List<Solicitacao> listarTodas() {
        return solicitacaoRepository.obterTodas();
    }
}
