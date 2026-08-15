package com.tcc.travel.infrastructure.mapper;

import com.tcc.travel.domain.entity.Solicitacao;
import com.tcc.travel.infrastructure.entity.SolicitacaoJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper para converter entre a entidade de domínio (Solicitacao)
 * e a entidade JPA (SolicitacaoJpaEntity).
 */
@Component
public class SolicitacaoMapper {

    /**
     * Converte uma entidade JPA para entidade de domínio.
     */
    public Solicitacao toDomain(SolicitacaoJpaEntity jpaEntity) {
        if (jpaEntity == null) {
            return null;
        }

        return Solicitacao.builder()
                .id(jpaEntity.getId())
                .usuarioId(jpaEntity.getUsuarioId())
                .destino(jpaEntity.getDestino())
                .dataIda(jpaEntity.getDataIda())
                .dataVolta(jpaEntity.getDataVolta())
                .motivo(jpaEntity.getMotivo())
                .status(jpaEntity.getStatus() != null ?
                        Solicitacao.StatusSolicitacao.valueOf(jpaEntity.getStatus().name())
                        : null)
                .resultadoSaga(jpaEntity.getResultadoSaga() != null ?
                        Solicitacao.ResultadoSaga.valueOf(jpaEntity.getResultadoSaga().name())
                        : null)
                .etapaFalha(jpaEntity.getEtapaFalha())
                .valorVoo(jpaEntity.getValorVoo())
                .valorHotel(jpaEntity.getValorHotel())
                .pagamentoVooConfirmado(jpaEntity.isPagamentoVooConfirmado())
                .pagamentoHotelConfirmado(jpaEntity.isPagamentoHotelConfirmado())
                .dataCriacao(jpaEntity.getDataCriacao())
                .dataAtualizacao(jpaEntity.getDataAtualizacao())
                .build();
    }

    /**
     * Converte uma entidade de domínio para entidade JPA.
     */
    public SolicitacaoJpaEntity toJpa(Solicitacao domain) {
        if (domain == null) {
            return null;
        }

        return SolicitacaoJpaEntity.builder()
                .id(domain.getId())
                .usuarioId(domain.getUsuarioId())
                .destino(domain.getDestino())
                .dataIda(domain.getDataIda())
                .dataVolta(domain.getDataVolta())
                .motivo(domain.getMotivo())
                .status(domain.getStatus() != null ?
                        SolicitacaoJpaEntity.StatusSolicitacao.valueOf(domain.getStatus().name())
                        : null)
                .resultadoSaga(domain.getResultadoSaga() != null ?
                        SolicitacaoJpaEntity.ResultadoSaga.valueOf(domain.getResultadoSaga().name())
                        : null)
                .etapaFalha(domain.getEtapaFalha())
                .valorVoo(domain.getValorVoo())
                .valorHotel(domain.getValorHotel())
                .pagamentoVooConfirmado(domain.isPagamentoVooConfirmado())
                .pagamentoHotelConfirmado(domain.isPagamentoHotelConfirmado())
                .dataCriacao(domain.getDataCriacao())
                .dataAtualizacao(domain.getDataAtualizacao())
                .build();
    }
}
