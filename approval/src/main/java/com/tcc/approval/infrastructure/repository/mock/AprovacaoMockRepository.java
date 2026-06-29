package com.tcc.approval.infrastructure.repository.mock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.tcc.approval.domain.entity.Aprovacao;
import com.tcc.approval.domain.repository.AprovacaoRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementação mock do repositório de Aprovação.
 * Utilizada para testes e desenvolvimento sem conexão real com o banco de dados.
 *
 * Ativa quando: spring.jpa.mock.enabled=true
 */
@Slf4j
@Repository
@ConditionalOnProperty(name = "spring.jpa.mock.enabled", havingValue = "true")
public class AprovacaoMockRepository implements AprovacaoRepository {

    private static final Map<Long, Aprovacao> database = new HashMap<>();
    private static final AtomicLong idGenerator = new AtomicLong(1);

    static {
        initializeMockData();
    }

    private static void initializeMockData() {
        Aprovacao aprovacao1 = Aprovacao.builder()
                .id(1L)
                .solicitanteId(1L)
                .responsavelId(101L)
                .tempoLimite(LocalDateTime.now().plusDays(2))
                .status(Aprovacao.StatusAprovacao.PENDENTE)
                .dataCriacao(LocalDateTime.now())
                .build();

        Aprovacao aprovacao2 = Aprovacao.builder()
                .id(2L)
                .solicitanteId(1L)
                .responsavelId(102L)
                .tempoLimite(LocalDateTime.now().plusDays(5))
                .status(Aprovacao.StatusAprovacao.APROVADA)
                .dataCriacao(LocalDateTime.now().minusDays(1))
                .dataAtualizacao(LocalDateTime.now().minusHours(3))
                .build();

        database.put(aprovacao1.getId(), aprovacao1);
        database.put(aprovacao2.getId(), aprovacao2);
        idGenerator.set(3L);
    }

    @Override
    public Aprovacao salvar(Aprovacao aprovacao) {
        log.info("[MOCK] Salvando aprovação do solicitante: {}", aprovacao.getSolicitanteId());
        if (aprovacao.getId() == null) {
            aprovacao.setId(idGenerator.getAndIncrement());
            aprovacao.setDataCriacao(LocalDateTime.now());
        }
        database.put(aprovacao.getId(), aprovacao);
        return aprovacao;
    }

    @Override
    public Optional<Aprovacao> obterPorId(Long id) {
        log.info("[MOCK] Buscando aprovação com ID: {}", id);
        return Optional.ofNullable(database.get(id));
    }

    @Override
    public List<Aprovacao> obterTodas() {
        log.info("[MOCK] Buscando todas as aprovações");
        return new ArrayList<>(database.values());
    }
}
