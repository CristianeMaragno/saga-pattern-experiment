package com.tcc.approval.infrastructure.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.tcc.approval.domain.entity.Aprovacao;
import com.tcc.approval.domain.repository.AprovacaoRepository;
import com.tcc.approval.infrastructure.entity.AprovacaoJpaEntity;
import com.tcc.approval.infrastructure.mapper.AprovacaoMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementação do repositório de Aprovação usando JPA/PostgreSQL.
 *
 * Ativa quando: spring.jpa.mock.enabled=false (ou não configurado)
 */
@Slf4j
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.jpa.mock.enabled", havingValue = "false", matchIfMissing = true)
public class AprovacaoRepositoryImpl implements AprovacaoRepository {

    private final AprovacaoJpaRepository jpaRepository;
    private final AprovacaoMapper mapper;

    @Override
    public Aprovacao salvar(Aprovacao aprovacao) {
        log.info("Salvando aprovação do solicitante: {}", aprovacao.getSolicitanteId());
        AprovacaoJpaEntity jpaEntity = mapper.toJpa(aprovacao);
        AprovacaoJpaEntity savedEntity = jpaRepository.save(jpaEntity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Aprovacao> obterPorId(Long id) {
        log.info("Buscando aprovação com ID: {}", id);
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<Aprovacao> obterTodas() {
        log.info("Buscando todas as aprovações");
        return jpaRepository.findAll()
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}
