package com.tcc.booking.domain.repository;

import java.util.Optional;

import com.tcc.booking.domain.entity.Solicitacao;

public interface SolicitacaoRepository {
    Solicitacao save(Solicitacao s);
    Optional<Solicitacao> findById(Long id);
}
