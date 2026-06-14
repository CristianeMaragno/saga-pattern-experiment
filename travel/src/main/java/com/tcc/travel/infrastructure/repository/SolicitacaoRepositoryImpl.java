package com.tcc.travel.infrastructure.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.tcc.travel.domain.entity.Solicitacao;
import com.tcc.travel.domain.repository.SolicitacaoRepository;
import com.tcc.travel.infrastructure.entity.SolicitacaoJpaEntity;
import com.tcc.travel.infrastructure.mapper.SolicitacaoMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementação do repositório de Solicitação usando JPA/PostgreSQL.
 * 
 * Ativa quando: spring.jpa.mock.enabled=false (ou não configurado)
 */
@Slf4j
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.jpa.mock.enabled", havingValue = "false", matchIfMissing = true)
public class SolicitacaoRepositoryImpl implements SolicitacaoRepository {

    private final SolicitacaoJpaRepository jpaRepository;
    private final SolicitacaoMapper mapper;

    @Override
    public Solicitacao salvar(Solicitacao solicitacao) {
        log.info("Salvando solicitação: {}", solicitacao.getDestino());
        SolicitacaoJpaEntity jpaEntity = mapper.toJpa(solicitacao);
        SolicitacaoJpaEntity savedEntity = jpaRepository.save(jpaEntity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Solicitacao> obterPorId(Long id) {
        log.info("Buscando solicitação com ID: {}", id);
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<Solicitacao> obterTodas() {
        log.info("Buscando todas as solicitações");
        return jpaRepository.findAll()
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deletar(Long id) {
        log.info("Deletando solicitação com ID: {}", id);
        jpaRepository.deleteById(id);
    }

    @Override
    public List<Solicitacao> obterPorUsuarioId(Long usuarioId) {
        log.info("Buscando solicitações do usuário: {}", usuarioId);
        return jpaRepository.findByUsuarioId(usuarioId)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Solicitacao> obterPorStatus(Solicitacao.StatusSolicitacao status) {
        log.info("Buscando solicitações com status: {}", status);
        return jpaRepository.findByStatus(status.name())
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}
