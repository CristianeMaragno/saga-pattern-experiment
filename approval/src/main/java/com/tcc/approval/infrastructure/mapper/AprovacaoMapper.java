package com.tcc.approval.infrastructure.mapper;

import com.tcc.approval.domain.entity.Aprovacao;
import com.tcc.approval.infrastructure.entity.AprovacaoJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper para converter entre a entidade de domínio (Aprovacao) 
 * e a entidade JPA (AprovacaoJpaEntity).
 */
@Component
public class AprovacaoMapper {

    /**
     * Converte uma entidade JPA para entidade de domínio.
     */
    public Aprovacao toDomain(AprovacaoJpaEntity jpaEntity) {
        if (jpaEntity == null) {
            return null;
        }

        return Aprovacao.builder()
                .id(jpaEntity.getId())
                .solicitanteId(jpaEntity.getSolicitanteId())
                .responsavelId(jpaEntity.getResponsavelId())
                .tempoLimite(jpaEntity.getTempoLimite())
                .status(jpaEntity.getStatus() != null ?
                        Aprovacao.StatusAprovacao.valueOf(jpaEntity.getStatus().name())
                        : null)
                .dataCriacao(jpaEntity.getDataCriacao())
                .dataAtualizacao(jpaEntity.getDataAtualizacao())
                .build();
    }

    /**
     * Converte uma entidade de domínio para entidade JPA.
     */
    public AprovacaoJpaEntity toJpa(Aprovacao domain) {
        if (domain == null) {
            return null;
        }

        return AprovacaoJpaEntity.builder()
                .id(domain.getId())
                .solicitanteId(domain.getSolicitanteId())
                .responsavelId(domain.getResponsavelId())
                .tempoLimite(domain.getTempoLimite())
                .status(domain.getStatus() != null ?
                        AprovacaoJpaEntity.StatusAprovacao.valueOf(domain.getStatus().name())
                        : null)
                .dataCriacao(domain.getDataCriacao())
                .dataAtualizacao(domain.getDataAtualizacao())
                .build();
    }
}
