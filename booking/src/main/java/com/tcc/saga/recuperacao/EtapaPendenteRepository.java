package com.tcc.saga.recuperacao;

import com.tcc.saga.event.EtapaSaga;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Acesso às etapas pendentes de execução/retentativa.
 */
@Repository
public interface EtapaPendenteRepository extends JpaRepository<EtapaPendenteJpaEntity, Long> {

    List<EtapaPendenteJpaEntity> findByStatusAndProximaTentativaEmLessThanEqualOrderByProximaTentativaEmAsc(
            EtapaPendenteJpaEntity.StatusEtapa status, Instant limite, Pageable pageable);

    /**
     * Usado para tornar o enfileiramento idempotente: o mesmo evento reentregue
     * pelo Kafka não pode gerar uma segunda etapa pendente.
     */
    Optional<EtapaPendenteJpaEntity> findBySolicitacaoIdAndEtapa(Long solicitacaoId, EtapaSaga etapa);
}
