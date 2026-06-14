package com.tcc.travel.interfaces.controller;

import com.tcc.travel.application.dto.CreateSolicitacaoRequestDTO;
import com.tcc.travel.application.dto.SolicitacaoResponseDTO;
import com.tcc.travel.application.usecase.SolicitacaoUseCase;
import com.tcc.travel.domain.entity.Solicitacao;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller REST para operações com Solicitações de Viagem.
 * Responsável apenas por:
 * - Validação de entrada HTTP (@Valid)
 * - Conversão de dados de/para DTOs
 * - Delegação para o UseCase
 * - Retorno de respostas HTTP apropriadas
 * 
 * Não contém lógica de negócio.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/solicitacoes")
@RequiredArgsConstructor
public class SolicitacaoController {

    private final SolicitacaoUseCase solicitacaoUseCase;

    /**
     * POST /api/v1/solicitacoes - Cria uma nova solicitação de viagem
     */
    @PostMapping
    public ResponseEntity<SolicitacaoResponseDTO> criar(
            @Valid @RequestBody CreateSolicitacaoRequestDTO request) {
        log.info("Criando nova solicitação de viagem para usuário: {}", request.getUsuarioId());
        
        Solicitacao solicitacao = solicitacaoUseCase.criarSolicitacao(request);
        
        log.info("Solicitação criada com sucesso, ID: {}", solicitacao.getId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SolicitacaoResponseDTO.fromDomain(solicitacao));
    }
}
