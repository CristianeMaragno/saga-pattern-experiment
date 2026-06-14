package com.tcc.booking.application.dto;

import com.tcc.booking.hold.dto.HoldResponse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponseDTO {
    private SolicitacaoResponseDTO solicitacao;
    private HoldResponse flightHold;
    private HoldResponse hotelHold;
}
