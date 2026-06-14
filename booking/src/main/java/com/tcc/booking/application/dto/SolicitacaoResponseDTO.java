package com.tcc.booking.application.dto;

import java.time.LocalDate;

import com.tcc.booking.domain.entity.Solicitacao;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SolicitacaoResponseDTO {

    private Long id;
    private Long usuarioId;
    private String destino;
    private LocalDate dataIda;
    private LocalDate dataVolta;
    private String motivo;

    public static SolicitacaoResponseDTO from(Solicitacao s) {
        return new SolicitacaoResponseDTO(
                s.getId(), s.getUsuarioId(), s.getDestino(), s.getDataIda(), s.getDataVolta(), s.getMotivo());
    }
}
