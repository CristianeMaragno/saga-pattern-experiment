package com.tcc.booking.infrastructure.repository.mock;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Repository;

import com.tcc.booking.domain.entity.Solicitacao;
import com.tcc.booking.domain.repository.SolicitacaoRepository;

@Repository
public class SolicitacaoMockRepository implements SolicitacaoRepository {

    private final Map<Long, Solicitacao> store = new HashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    @Override
    public Solicitacao save(Solicitacao s) {
        if (s.getId() == null) {
            s.setId(seq.getAndIncrement());
        }
        store.put(s.getId(), s);
        return s;
    }

    @Override
    public Optional<Solicitacao> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }
}
