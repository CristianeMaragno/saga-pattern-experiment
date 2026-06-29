package com.tcc.payment.infrastructure.mapper;

import org.springframework.stereotype.Component;

import com.tcc.payment.domain.entity.Pagamento;
import com.tcc.payment.infrastructure.entity.PagamentoJpaEntity;

/**
 * Mapper para converter entre a entidade de domínio (Pagamento)
 * e a entidade JPA (PagamentoJpaEntity).
 */
@Component
public class PagamentoMapper {

    /**
     * Converte uma entidade JPA para entidade de domínio.
     */
    public Pagamento toDomain(PagamentoJpaEntity jpaEntity) {
        if (jpaEntity == null) {
            return null;
        }

        return Pagamento.builder()
                .id(jpaEntity.getId())
                .tipo(jpaEntity.getTipo() != null ?
                        Pagamento.TipoPagamento.valueOf(jpaEntity.getTipo().name())
                        : null)
                .referencia(jpaEntity.getReferencia())
                .valor(jpaEntity.getValor())
                .status(jpaEntity.getStatus() != null ?
                        Pagamento.StatusPagamento.valueOf(jpaEntity.getStatus().name())
                        : null)
                .dataCriacao(jpaEntity.getDataCriacao())
                .dataAtualizacao(jpaEntity.getDataAtualizacao())
                .build();
    }

    /**
     * Converte uma entidade de domínio para entidade JPA.
     */
    public PagamentoJpaEntity toJpa(Pagamento domain) {
        if (domain == null) {
            return null;
        }

        return PagamentoJpaEntity.builder()
                .id(domain.getId())
                .tipo(domain.getTipo() != null ?
                        PagamentoJpaEntity.TipoPagamento.valueOf(domain.getTipo().name())
                        : null)
                .referencia(domain.getReferencia())
                .valor(domain.getValor())
                .status(domain.getStatus() != null ?
                        PagamentoJpaEntity.StatusPagamento.valueOf(domain.getStatus().name())
                        : null)
                .dataCriacao(domain.getDataCriacao())
                .dataAtualizacao(domain.getDataAtualizacao())
                .build();
    }
}
