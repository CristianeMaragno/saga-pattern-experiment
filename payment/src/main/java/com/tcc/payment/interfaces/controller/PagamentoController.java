package com.tcc.payment.interfaces.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tcc.payment.application.dto.CreatePagamentoRequestDTO;
import com.tcc.payment.application.dto.PagamentoResponseDTO;
import com.tcc.payment.application.usecase.PagamentoUseCase;
import com.tcc.payment.domain.entity.Pagamento;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller REST para processamento de pagamentos.
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
@RequestMapping("/api/v1/pagamentos")
@RequiredArgsConstructor
public class PagamentoController {

    private final PagamentoUseCase pagamentoUseCase;

    /**
     * POST /api/v1/pagamentos/voo - Confirma a reserva e processa o pagamento do voo (T5)
     */
    @PostMapping("/voo")
    public ResponseEntity<PagamentoResponseDTO> pagarVoo(@Valid @RequestBody CreatePagamentoRequestDTO request) {
        log.info("Processando pagamento de voo para referência: {}", request.getReferencia());

        Pagamento pagamento = pagamentoUseCase.processarPagamentoVoo(request);

        log.info("Pagamento de voo confirmado com sucesso, ID: {}", pagamento.getId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(PagamentoResponseDTO.fromDomain(pagamento));
    }

    /**
     * POST /api/v1/pagamentos/hotel - Confirma a reserva e processa o pagamento do hotel (T6)
     */
    @PostMapping("/hotel")
    public ResponseEntity<PagamentoResponseDTO> pagarHotel(@Valid @RequestBody CreatePagamentoRequestDTO request) {
        log.info("Processando pagamento de hotel para referência: {}", request.getReferencia());

        Pagamento pagamento = pagamentoUseCase.processarPagamentoHotel(request);

        log.info("Pagamento de hotel confirmado com sucesso, ID: {}", pagamento.getId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(PagamentoResponseDTO.fromDomain(pagamento));
    }

    /**
     * GET /api/v1/pagamentos/{id} - Obtém um pagamento pelo ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<PagamentoResponseDTO> obter(@PathVariable Long id) {
        Pagamento pagamento = pagamentoUseCase.obterPorId(id);
        return ResponseEntity.ok(PagamentoResponseDTO.fromDomain(pagamento));
    }

    /**
     * GET /api/v1/pagamentos - Lista todos os pagamentos
     */
    @GetMapping
    public ResponseEntity<List<PagamentoResponseDTO>> listar() {
        List<PagamentoResponseDTO> pagamentos = pagamentoUseCase.listarTodos()
                .stream()
                .map(PagamentoResponseDTO::fromDomain)
                .toList();

        return ResponseEntity.ok(pagamentos);
    }
}
