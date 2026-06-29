package com.tcc.payment.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.tcc.payment.domain.entity.Pagamento;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de saída que representa um Pagamento.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagamentoResponseDTO {

    private Long id;
    private String tipo;
    private String referencia;
    private BigDecimal valor;
    private String status;
    private LocalDateTime dataCriacao;
    private LocalDateTime dataAtualizacao;

    /**
     * Converte uma entidade de domínio em DTO de resposta.
     */
    public static PagamentoResponseDTO fromDomain(Pagamento pagamento) {
        return PagamentoResponseDTO.builder()
                .id(pagamento.getId())
                .tipo(pagamento.getTipo() != null ? pagamento.getTipo().name() : null)
                .referencia(pagamento.getReferencia())
                .valor(pagamento.getValor())
                .status(pagamento.getStatus() != null ? pagamento.getStatus().name() : null)
                .dataCriacao(pagamento.getDataCriacao())
                .dataAtualizacao(pagamento.getDataAtualizacao())
                .build();
    }
}
