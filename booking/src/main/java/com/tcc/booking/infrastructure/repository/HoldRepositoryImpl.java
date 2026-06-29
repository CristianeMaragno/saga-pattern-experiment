package com.tcc.booking.infrastructure.repository;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.tcc.booking.domain.entity.Hold;
import com.tcc.booking.domain.repository.HoldRepository;
import com.tcc.booking.infrastructure.entity.HoldJpaEntity;
import com.tcc.booking.infrastructure.mapper.HoldMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementação do repositório de Hold usando JPA/PostgreSQL.
 *
 * Ativa quando: spring.jpa.mock.enabled=false (ou não configurado)
 */
@Slf4j
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.jpa.mock.enabled", havingValue = "false", matchIfMissing = true)
public class HoldRepositoryImpl implements HoldRepository {

    private final HoldJpaRepository jpaRepository;
    private final HoldMapper mapper;

    @Override
    public Hold salvar(Hold hold) {
        log.info("Salvando hold do tipo {} para referência {}", hold.getType(), hold.getReference());
        HoldJpaEntity jpaEntity = mapper.toJpa(hold);
        HoldJpaEntity savedEntity = jpaRepository.save(jpaEntity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Hold> obterPorId(Long id) {
        log.info("Buscando hold com ID: {}", id);
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }
}
