package com.tcc.audit.infrastructure.repository;

import com.tcc.audit.infrastructure.entity.RegistroAuditoriaJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Acesso à tabela central de auditoria.
 */
@Repository
public interface RegistroAuditoriaJpaRepository extends JpaRepository<RegistroAuditoriaJpaEntity, Long> {

    List<RegistroAuditoriaJpaEntity> findByRunIdOrderBySolicitacaoIdAscTimestampMillisAsc(String runId);

    long countByRunId(String runId);

    @Modifying
    @Query("delete from RegistroAuditoriaJpaEntity r where r.runId = :runId")
    int deleteByRunId(@Param("runId") String runId);

    /**
     * Resume cada instância de saga em uma linha.
     *
     * Fazer a agregação por saga no banco e só os totais em Java mantém o
     * cálculo viável para as cargas altas (5.000 sagas por rodada), sem trazer
     * todos os eventos para a memória.
     *
     * Colunas: 0 solicitacao_id, 1 inicio, 2 fim, 3 concluida, 4 compensada,
     * 5 abortada, 6 rejeitada, 7 falhas, 8 retries, 9 compensacoes,
     * 10 primeira_falha, 11 eventos.
     */
    @Query(value = """
            SELECT solicitacao_id,
                   MIN(CASE WHEN acao = 'SAGA_INICIADA' THEN timestamp_millis END) AS inicio,
                   MAX(CASE WHEN acao IN ('SAGA_CONCLUIDA', 'SAGA_COMPENSADA', 'SAGA_ABORTADA', 'SAGA_REJEITADA')
                            THEN timestamp_millis END) AS fim,
                   MAX(CASE WHEN acao = 'SAGA_CONCLUIDA' THEN 1 ELSE 0 END) AS concluida,
                   MAX(CASE WHEN acao = 'SAGA_COMPENSADA' THEN 1 ELSE 0 END) AS compensada,
                   MAX(CASE WHEN acao = 'SAGA_ABORTADA' THEN 1 ELSE 0 END) AS abortada,
                   MAX(CASE WHEN acao = 'SAGA_REJEITADA' THEN 1 ELSE 0 END) AS rejeitada,
                   SUM(CASE WHEN acao = 'FALHA' THEN 1 ELSE 0 END) AS falhas,
                   SUM(CASE WHEN acao = 'RETRY' THEN 1 ELSE 0 END) AS retries,
                   SUM(CASE WHEN acao = 'COMPENSACAO' THEN 1 ELSE 0 END) AS compensacoes,
                   MIN(CASE WHEN acao = 'FALHA' THEN timestamp_millis END) AS primeira_falha,
                   COUNT(*) AS eventos
            FROM saga_auditoria
            WHERE run_id = :runId
            GROUP BY solicitacao_id
            """, nativeQuery = true)
    List<Object[]> resumirSagas(@Param("runId") String runId);
}
