package com.tcc.approval.infrastructure.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.tcc.approval.domain.entity.Aprovacao;
import com.tcc.approval.domain.repository.AprovacaoRepository;

/**
 * Implementação mockada do repositório de domínio.
 * Simula o comportamento de um banco de dados PostgreSQL usando uma estrutura em memória.
 * Esta implementação é temporária e será substituída por JPA quando o banco real estiver configurado.
 */
@Component
public class AprovacaoRepositoryImpl implements AprovacaoRepository {

    private final ConcurrentHashMap<Long, Aprovacao> store = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(1L);

    @Override
    public Aprovacao salvar(Aprovacao aprovacao) {
        if (aprovacao.getId() == null) {
            aprovacao.setId(idCounter.getAndIncrement());
        }
        store.put(aprovacao.getId(), aprovacao);
        return aprovacao;
    }

    @Override
    public Optional<Aprovacao> obterPorId(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Aprovacao> obterTodas() {
        return new ArrayList<>(store.values());
    }
}
