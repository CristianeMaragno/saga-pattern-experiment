package com.tcc.booking.application.usecase;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

import com.tcc.booking.application.dto.CreateHoldRequestDTO;
import com.tcc.booking.domain.entity.Hold;
import com.tcc.booking.domain.exception.BusinessException;
import com.tcc.booking.domain.exception.ResourceNotFoundException;
import com.tcc.booking.domain.repository.HoldRepository;

import lombok.RequiredArgsConstructor;

/**
 * Caso de uso para criação e consulta de holds temporários de reserva.
 *
 * Regras de negócio:
 * - T3 (voo): hold válido por um período entre 24h e 72h.
 * - T4 (hotel): hold válido por um período padrão de 48h, se não informado.
 */
@Service
@RequiredArgsConstructor
public class HoldUseCase {

    private static final int FLIGHT_MIN_HOURS = 24;
    private static final int FLIGHT_MAX_HOURS = 72;
    private static final int HOTEL_DEFAULT_HOURS = 48;

    private final HoldRepository holdRepository;

    /**
     * Cria um hold de voo (T3). A duração deve estar entre 24h e 72h;
     * se não informada, assume o mínimo de 24h.
     */
    public Hold criarHoldVoo(CreateHoldRequestDTO request) {
        int hours = request.getDurationHours() != null ? request.getDurationHours() : FLIGHT_MIN_HOURS;
        if (hours < FLIGHT_MIN_HOURS || hours > FLIGHT_MAX_HOURS) {
            throw new BusinessException(
                    "Duração do hold de voo deve estar entre " + FLIGHT_MIN_HOURS + "h e " + FLIGHT_MAX_HOURS + "h");
        }
        return criarHold(Hold.HoldType.FLIGHT, request.getReference(), hours);
    }

    /**
     * Cria um hold de hotel (T4). Se a duração não for informada,
     * assume o padrão de 48h.
     */
    public Hold criarHoldHotel(CreateHoldRequestDTO request) {
        int hours = request.getDurationHours() != null ? request.getDurationHours() : HOTEL_DEFAULT_HOURS;
        return criarHold(Hold.HoldType.HOTEL, request.getReference(), hours);
    }

    /**
     * Busca um hold pelo ID.
     */
    public Hold obterPorId(Long id) {
        return holdRepository.obterPorId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hold não encontrado: " + id));
    }

    private Hold criarHold(Hold.HoldType type, String reference, int hours) {
        Instant now = Instant.now();
        Hold hold = Hold.builder()
                .type(type)
                .reference(reference)
                .createdAt(now)
                .expiresAt(now.plus(hours, ChronoUnit.HOURS))
                .build();
        return holdRepository.salvar(hold);
    }
}
