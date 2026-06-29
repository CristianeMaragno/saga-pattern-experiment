package com.tcc.booking.infrastructure.repository.mock;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.tcc.booking.domain.entity.Hold;
import com.tcc.booking.domain.repository.HoldRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementação mock do repositório de Hold.
 * Utilizada para testes e desenvolvimento sem conexão real com o banco de dados.
 *
 * Ativa quando: spring.jpa.mock.enabled=true
 */
@Slf4j
@Repository
@ConditionalOnProperty(name = "spring.jpa.mock.enabled", havingValue = "true")
public class HoldMockRepository implements HoldRepository {

    private static final Map<Long, Hold> database = new HashMap<>();
    private static final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Hold salvar(Hold hold) {
        log.info("[MOCK] Salvando hold do tipo {} para referência {}", hold.getType(), hold.getReference());
        if (hold.getId() == null) {
            hold.setId(idGenerator.getAndIncrement());
        }
        if (hold.getCreatedAt() == null) {
            hold.setCreatedAt(Instant.now());
        }
        database.put(hold.getId(), hold);
        return hold;
    }

    @Override
    public Optional<Hold> obterPorId(Long id) {
        log.info("[MOCK] Buscando hold com ID: {}", id);
        return Optional.ofNullable(database.get(id));
    }
}
