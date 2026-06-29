package com.tcc.payment.infrastructure.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.tcc.payment.domain.entity.Pagamento;
import com.tcc.payment.domain.repository.PagamentoRepository;
import com.tcc.payment.infrastructure.entity.PagamentoJpaEntity;
import com.tcc.payment.infrastructure.mapper.PagamentoMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementação do repositório de Pagamento usando JPA/PostgreSQL.
 *
 * Ativa quando: spring.jpa.mock.enabled=false (ou não configurado)
 */
@Slf4j
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.jpa.mock.enabled", havingValue = "false", matchIfMissing = true)
public class PagamentoRepositoryImpl implements PagamentoRepository {

    private final PagamentoJpaRepository jpaRepository;
    private final PagamentoMapper mapper;

    @Override
    public Pagamento salvar(Pagamento pagamento) {
        log.info("Salvando pagamento do tipo {} para referência {}", pagamento.getTipo(), pagamento.getReferencia());
        PagamentoJpaEntity jpaEntity = mapper.toJpa(pagamento);
        PagamentoJpaEntity savedEntity = jpaRepository.save(jpaEntity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Pagamento> obterPorId(Long id) {
        log.info("Buscando pagamento com ID: {}", id);
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<Pagamento> obterTodos() {
        log.info("Buscando todos os pagamentos");
        return jpaRepository.findAll()
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}
