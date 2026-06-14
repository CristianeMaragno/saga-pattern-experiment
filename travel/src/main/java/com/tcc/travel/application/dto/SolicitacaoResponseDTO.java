package com.tcc.travel.application.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.tcc.travel.domain.entity.Solicitacao;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de saída que representa uma Solicitação de Viagem.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitacaoResponseDTO {

    private Long id;
    private Long usuarioId;
    private String destino;
    private LocalDate dataIda;
    private LocalDate dataVolta;
    private String motivo;
    private String status;
    private LocalDateTime dataCriacao;
    private LocalDateTime dataAtualizacao;

    /**
     * Converte uma entidade de domínio em DTO de resposta.
     */
    public static SolicitacaoResponseDTO fromDomain(Solicitacao solicitacao) {
        return SolicitacaoResponseDTO.builder()
                .id(solicitacao.getId())
                .usuarioId(solicitacao.getUsuarioId())
                .destino(solicitacao.getDestino())
                .dataIda(solicitacao.getDataIda())
                .dataVolta(solicitacao.getDataVolta())
                .motivo(solicitacao.getMotivo())
                .status(solicitacao.getStatus() != null ? 
                        solicitacao.getStatus().name() : null)
                .dataCriacao(solicitacao.getDataCriacao())
                .dataAtualizacao(solicitacao.getDataAtualizacao())
                .build();
    }
}
