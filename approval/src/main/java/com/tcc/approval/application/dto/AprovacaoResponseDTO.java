package com.tcc.approval.application.dto;

import com.tcc.approval.domain.entity.Aprovacao;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO de saída que representa uma Aprovação.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AprovacaoResponseDTO {

    private Long id;
    private Long solicitanteId;
    private Long responsavelId;
    private LocalDateTime tempoLimite;
    private String status;
    private LocalDateTime dataCriacao;
    private LocalDateTime dataAtualizacao;

    /**
     * Converte uma entidade de domínio em DTO de resposta.
     */
    public static AprovacaoResponseDTO fromDomain(Aprovacao aprovacao) {
        return AprovacaoResponseDTO.builder()
                .id(aprovacao.getId())
                .solicitanteId(aprovacao.getSolicitanteId())
                .responsavelId(aprovacao.getResponsavelId())
                .tempoLimite(aprovacao.getTempoLimite())
                .status(aprovacao.getStatus() != null ? 
                        aprovacao.getStatus().name() : null)
                .dataCriacao(aprovacao.getDataCriacao())
                .dataAtualizacao(aprovacao.getDataAtualizacao())
                .build();
    }
}
