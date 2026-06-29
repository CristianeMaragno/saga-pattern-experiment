package com.tcc.payment.infrastructure.repository.mock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.tcc.payment.domain.entity.Pagamento;
import com.tcc.payment.domain.repository.PagamentoRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementação mock do repositório de Pagamento.
 * Utilizada para testes e desenvolvimento sem conexão real com o banco de dados.
 *
 * Ativa quando: spring.jpa.mock.enabled=true
 */
@Slf4j
@Repository
@ConditionalOnProperty(name = "spring.jpa.mock.enabled", havingValue = "true")
public class PagamentoMockRepository implements PagamentoRepository {

    private static final Map<Long, Pagamento> database = new HashMap<>();
    private static final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Pagamento salvar(Pagamento pagamento) {
        log.info("[MOCK] Salvando pagamento do tipo {} para referência {}", pagamento.getTipo(), pagamento.getReferencia());
        if (pagamento.getId() == null) {
            pagamento.setId(idGenerator.getAndIncrement());
        }
        database.put(pagamento.getId(), pagamento);
        return pagamento;
    }

    @Override
    public Optional<Pagamento> obterPorId(Long id) {
        log.info("[MOCK] Buscando pagamento com ID: {}", id);
        return Optional.ofNullable(database.get(id));
    }

    @Override
    public List<Pagamento> obterTodos() {
        log.info("[MOCK] Buscando todos os pagamentos");
        return new ArrayList<>(database.values());
    }
}
