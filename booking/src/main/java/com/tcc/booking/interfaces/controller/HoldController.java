package com.tcc.booking.interfaces.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tcc.booking.application.dto.CreateHoldRequestDTO;
import com.tcc.booking.application.dto.HoldResponseDTO;
import com.tcc.booking.application.usecase.HoldUseCase;
import com.tcc.booking.domain.entity.Hold;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller REST para criação e consulta de holds de reserva.
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
@RequestMapping("/api/v1/holds")
@RequiredArgsConstructor
public class HoldController {

    private final HoldUseCase holdUseCase;

    /**
     * POST /api/v1/holds/flight - Cria um hold de voo (T3)
     */
    @PostMapping("/flight")
    public ResponseEntity<HoldResponseDTO> criarHoldVoo(@Valid @RequestBody CreateHoldRequestDTO request) {
        log.info("Criando hold de voo para referência: {}", request.getReference());

        Hold hold = holdUseCase.criarHoldVoo(request);

        log.info("Hold de voo criado com sucesso, ID: {}", hold.getId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(HoldResponseDTO.fromDomain(hold));
    }

    /**
     * POST /api/v1/holds/hotel - Cria um hold de hotel (T4)
     */
    @PostMapping("/hotel")
    public ResponseEntity<HoldResponseDTO> criarHoldHotel(@Valid @RequestBody CreateHoldRequestDTO request) {
        log.info("Criando hold de hotel para referência: {}", request.getReference());

        Hold hold = holdUseCase.criarHoldHotel(request);

        log.info("Hold de hotel criado com sucesso, ID: {}", hold.getId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(HoldResponseDTO.fromDomain(hold));
    }

    /**
     * GET /api/v1/holds/{id} - Obtém um hold pelo ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<HoldResponseDTO> obterHold(@PathVariable Long id) {
        Hold hold = holdUseCase.obterPorId(id);
        return ResponseEntity.ok(HoldResponseDTO.fromDomain(hold));
    }
}
