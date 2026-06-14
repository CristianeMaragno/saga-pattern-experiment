package com.tcc.approval.interfaces.controller;

import com.tcc.approval.application.dto.AprovacaoResponseDTO;
import com.tcc.approval.application.dto.CreateAprovacaoRequestDTO;
import com.tcc.approval.application.usecase.AprovacaoUseCase;
import com.tcc.approval.domain.entity.Aprovacao;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller REST para operações com Aprovações.
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
@RequestMapping("/api/v1/aprovacoes")
@RequiredArgsConstructor
public class AprovacaoController {

    private final AprovacaoUseCase aprovacaoUseCase;

    /**
     * POST /api/v1/aprovacoes - Cria uma nova aprovação
     */
    @PostMapping
    public ResponseEntity<AprovacaoResponseDTO> criar(
            @Valid @RequestBody CreateAprovacaoRequestDTO request) {
        log.info("Criando nova aprovação para solicitante: {} com responsável: {}", 
                request.getSolicitanteId(), request.getResponsavelId());
        
        Aprovacao aprovacao = aprovacaoUseCase.criarAprovacao(request);
        
        log.info("Aprovação criada com sucesso, ID: {}", aprovacao.getId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(AprovacaoResponseDTO.fromDomain(aprovacao));
    }

    /**
     * GET /api/v1/aprovacoes/{id} - Obtém uma aprovação por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<AprovacaoResponseDTO> obter(@PathVariable Long id) {
        log.info("Buscando aprovação com ID: {}", id);
        
        Aprovacao aprovacao = aprovacaoUseCase.obterAprovacao(id);
        
        return ResponseEntity.ok(AprovacaoResponseDTO.fromDomain(aprovacao));
    }

    /**
     * GET /api/v1/aprovacoes - Obtém todas as aprovações
     */
    @GetMapping
    public ResponseEntity<List<AprovacaoResponseDTO>> obterTodas() {
        log.info("Buscando todas as aprovações");
        
        List<AprovacaoResponseDTO> aprovacoes = aprovacaoUseCase.obterTodasAprovacoes()
                .stream()
                .map(AprovacaoResponseDTO::fromDomain)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(aprovacoes);
    }

    /**
     * PUT /api/v1/aprovacoes/{id}/aprovar - Aprova uma aprovação
     */
    @PutMapping("/{id}/aprovar")
    public ResponseEntity<AprovacaoResponseDTO> aprovar(@PathVariable Long id) {
        log.info("Aprovando aprovação com ID: {}", id);
        
        Aprovacao aprovacao = aprovacaoUseCase.aprovarAprovacao(id);
        
        log.info("Aprovação aprovada com sucesso, ID: {}", id);
        return ResponseEntity.ok(AprovacaoResponseDTO.fromDomain(aprovacao));
    }

    /**
     * PUT /api/v1/aprovacoes/{id}/rejeitar - Rejeita uma aprovação
     */
    @PutMapping("/{id}/rejeitar")
    public ResponseEntity<AprovacaoResponseDTO> rejeitar(@PathVariable Long id) {
        log.info("Rejeitando aprovação com ID: {}", id);
        
        Aprovacao aprovacao = aprovacaoUseCase.rejeitarAprovacao(id);
        
        log.info("Aprovação rejeitada com sucesso, ID: {}", id);
        return ResponseEntity.ok(AprovacaoResponseDTO.fromDomain(aprovacao));
    }

    /**
     * DELETE /api/v1/aprovacoes/{id} - Cancela uma aprovação
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelar(@PathVariable Long id) {
        log.info("Cancelando aprovação com ID: {}", id);
        
        aprovacaoUseCase.cancelarAprovacao(id);
        
        log.info("Aprovação cancelada com sucesso, ID: {}", id);
        return ResponseEntity.noContent().build();
    }
}
