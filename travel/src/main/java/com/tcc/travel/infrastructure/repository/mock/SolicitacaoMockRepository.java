package com.tcc.travel.infrastructure.repository.mock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.tcc.travel.domain.entity.Solicitacao;
import com.tcc.travel.domain.repository.SolicitacaoRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementação mock do repositório de Solicitação.
 * Utilizada para testes e desenvolvimento sem conexão real com o banco de dados.
 *
 * Ativa quando: spring.jpa.mock.enabled=true
 */
@Slf4j
@Repository
@ConditionalOnProperty(name = "spring.jpa.mock.enabled", havingValue = "true")
public class SolicitacaoMockRepository implements SolicitacaoRepository {

    private static final Map<Long, Solicitacao> database = new HashMap<>();
    private static final AtomicLong idGenerator = new AtomicLong(1);

    static {
        // Dados mock iniciais
        initializeMockData();
    }

    private static void initializeMockData() {
        Solicitacao solicitacao1 = Solicitacao.builder()
                .id(1L)
                .usuarioId(1L)
                .destino("São Paulo")
                .dataIda(java.time.LocalDate.now().plusDays(10))
                .dataVolta(java.time.LocalDate.now().plusDays(15))
                .motivo("Reunião com cliente")
                .status(Solicitacao.StatusSolicitacao.PENDENTE)
                .dataCriacao(LocalDateTime.now())
                .dataAtualizacao(LocalDateTime.now())
                .build();

        Solicitacao solicitacao2 = Solicitacao.builder()
                .id(2L)
                .usuarioId(1L)
                .destino("Rio de Janeiro")
                .dataIda(java.time.LocalDate.now().plusDays(20))
                .dataVolta(java.time.LocalDate.now().plusDays(25))
                .motivo("Conferência")
                .status(Solicitacao.StatusSolicitacao.APROVADA)
                .dataCriacao(LocalDateTime.now().minusDays(5))
                .dataAtualizacao(LocalDateTime.now().minusDays(2))
                .build();

        database.put(solicitacao1.getId(), solicitacao1);
        database.put(solicitacao2.getId(), solicitacao2);
        idGenerator.set(3L);
    }

    @Override
    public Solicitacao salvar(Solicitacao solicitacao) {
        log.info("[MOCK] Salvando solicitação: {}", solicitacao.getDestino());
        if (solicitacao.getId() == null) {
            solicitacao.setId(idGenerator.getAndIncrement());
            solicitacao.setDataCriacao(LocalDateTime.now());
        }
        solicitacao.setDataAtualizacao(LocalDateTime.now());
        database.put(solicitacao.getId(), solicitacao);
        return solicitacao;
    }

    @Override
    public Optional<Solicitacao> obterPorId(Long id) {
        log.info("[MOCK] Buscando solicitação com ID: {}", id);
        return Optional.ofNullable(database.get(id));
    }

    @Override
    public List<Solicitacao> obterTodas() {
        log.info("[MOCK] Buscando todas as solicitações");
        return new ArrayList<>(database.values());
    }

    @Override
    public void deletar(Long id) {
        log.info("[MOCK] Deletando solicitação com ID: {}", id);
        database.remove(id);
    }

    @Override
    public List<Solicitacao> obterPorUsuarioId(Long usuarioId) {
        log.info("[MOCK] Buscando solicitações do usuário: {}", usuarioId);
        return database.values().stream()
                .filter(s -> s.getUsuarioId().equals(usuarioId))
                .collect(Collectors.toList());
    }

    @Override
    public List<Solicitacao> obterPorStatus(Solicitacao.StatusSolicitacao status) {
        log.info("[MOCK] Buscando solicitações com status: {}", status);
        return database.values().stream()
                .filter(s -> s.getStatus().equals(status))
                .collect(Collectors.toList());
    }

    /**
     * Limpa os dados mock (útil para testes).
     */
    public void clear() {
        log.info("[MOCK] Limpando dados do repositório mock");
        database.clear();
        idGenerator.set(1L);
        initializeMockData();
    }
}

