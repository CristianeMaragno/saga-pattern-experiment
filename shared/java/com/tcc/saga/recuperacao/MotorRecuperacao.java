package com.tcc.saga.recuperacao;

import com.tcc.saga.event.EtapaSaga;
import com.tcc.saga.event.SagaAuditEvento;
import com.tcc.saga.experimento.AuditoriaPublisher;
import com.tcc.saga.experimento.ExperimentoProperties;
import com.tcc.saga.experimento.FalhaPermanenteException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Aplica a estratégia de recuperação configurada às etapas que falharam.
 *
 * É o mesmo código nos três serviços que podem sofrer falha injetada
 * (approval/T2, booking/T4, payment/T6) — o experimento é comparativo, então a
 * uniformidade entre serviços importa tanto quanto a corretude individual.
 *
 * <p>Comportamento por estratégia, diante de uma falha temporária:
 * <ul>
 *   <li><b>Backward</b>: não tenta de novo; dispara a compensação imediatamente.</li>
 *   <li><b>Forward</b>: tenta de novo com backoff exponencial; esgotadas as
 *       tentativas, encerra a saga <em>sem</em> compensar.</li>
 *   <li><b>Híbrida</b>: tenta de novo como o Forward; esgotadas as tentativas,
 *       cai para a cadeia backward a partir deste ponto.</li>
 * </ul>
 * Falha permanente não é recuperável por nenhuma estratégia: encerra na hora,
 * compensando ou não conforme a estratégia ativa.
 */
@Slf4j
@Component
public class MotorRecuperacao {

    private final Map<EtapaSaga, ExecutorEtapa> executores;
    private final EtapaPendenteRepository repository;
    private final ExperimentoProperties experimento;
    private final AuditoriaPublisher auditoria;
    private final TransactionTemplate transactionTemplate;
    private final int tamanhoLote;

    public MotorRecuperacao(List<ExecutorEtapa> executores,
                            EtapaPendenteRepository repository,
                            ExperimentoProperties experimento,
                            AuditoriaPublisher auditoria,
                            TransactionTemplate transactionTemplate,
                            @org.springframework.beans.factory.annotation.Value("${experimento.motor.tamanho-lote:200}")
                            int tamanhoLote) {
        this.executores = executores.stream()
                .collect(Collectors.toMap(ExecutorEtapa::etapa, Function.identity()));
        this.repository = repository;
        this.experimento = experimento;
        this.auditoria = auditoria;
        this.transactionTemplate = transactionTemplate;
        this.tamanhoLote = tamanhoLote;
    }

    /**
     * Enfileira uma etapa para execução. Idempotente: uma reentrega do mesmo
     * evento pelo Kafka não cria uma segunda etapa pendente.
     *
     * @param atrasoMillis espera antes da primeira execução (usado pela
     *                     aprovação, cuja decisão é de longa duração)
     */
    public void enfileirar(Long solicitacaoId, EtapaSaga etapa, String payload, long atrasoMillis) {
        if (repository.findBySolicitacaoIdAndEtapa(solicitacaoId, etapa).isPresent()) {
            log.debug("Etapa {} da solicitação {} já enfileirada; ignorando reentrega", etapa, solicitacaoId);
            return;
        }
        Instant agora = Instant.now();
        repository.save(EtapaPendenteJpaEntity.builder()
                .solicitacaoId(solicitacaoId)
                .etapa(etapa)
                .tentativa(0)
                .proximaTentativaEm(agora.plusMillis(atrasoMillis))
                .status(EtapaPendenteJpaEntity.StatusEtapa.PENDENTE)
                .payload(payload)
                .dataCriacao(agora)
                .build());
    }

    @Scheduled(fixedDelayString = "${experimento.motor.intervalo-ms:100}")
    public void processarPendentes() {
        List<EtapaPendenteJpaEntity> devidas = repository
                .findByStatusAndProximaTentativaEmLessThanEqualOrderByProximaTentativaEmAsc(
                        EtapaPendenteJpaEntity.StatusEtapa.PENDENTE,
                        Instant.now(),
                        PageRequest.of(0, tamanhoLote));

        for (EtapaPendenteJpaEntity pendente : devidas) {
            // Cada etapa em sua própria transação: uma falha não pode desfazer
            // o progresso das outras sagas processadas no mesmo lote.
            transactionTemplate.executeWithoutResult(status -> processar(pendente.getId()));
        }
    }

    private void processar(Long pendenteId) {
        EtapaPendenteJpaEntity pendente = repository.findById(pendenteId).orElse(null);
        if (pendente == null || pendente.getStatus() != EtapaPendenteJpaEntity.StatusEtapa.PENDENTE) {
            return;
        }

        ExecutorEtapa executor = executores.get(pendente.getEtapa());
        if (executor == null) {
            log.error("Sem executor registrado para a etapa {}", pendente.getEtapa());
            return;
        }

        int tentativa = pendente.getTentativa() + 1;
        pendente.setTentativa(tentativa);
        pendente.setDataAtualizacao(Instant.now());

        auditoria.registrar(pendente.getSolicitacaoId(), pendente.getEtapa(),
                tentativa == 1 ? SagaAuditEvento.AcaoAuditoria.INICIO : SagaAuditEvento.AcaoAuditoria.RETRY,
                tentativa, null);

        try {
            executor.executar(pendente, tentativa);

            pendente.setStatus(EtapaPendenteJpaEntity.StatusEtapa.CONCLUIDA);
            repository.save(pendente);
            auditoria.registrar(pendente.getSolicitacaoId(), pendente.getEtapa(),
                    SagaAuditEvento.AcaoAuditoria.SUCESSO, tentativa, null);

        } catch (RuntimeException e) {
            boolean permanente = e instanceof FalhaPermanenteException;
            registrarFalha(pendente, tentativa, permanente, e.getMessage(), executor);
        }
    }

    private void registrarFalha(EtapaPendenteJpaEntity pendente,
                                int tentativa,
                                boolean permanente,
                                String motivo,
                                ExecutorEtapa executor) {

        pendente.setUltimoErro(motivo);
        auditoria.registrar(pendente.getSolicitacaoId(), pendente.getEtapa(),
                SagaAuditEvento.AcaoAuditoria.FALHA, tentativa, motivo);

        if (!permanente && podeTentarNovamente(tentativa)) {
            long espera = calcularBackoff(tentativa);
            pendente.setProximaTentativaEm(Instant.now().plusMillis(espera));
            repository.save(pendente);
            log.info("Etapa {} da solicitação {} falhou na tentativa {}; nova tentativa em {}ms",
                    pendente.getEtapa(), pendente.getSolicitacaoId(), tentativa, espera);
            return;
        }

        pendente.setStatus(EtapaPendenteJpaEntity.StatusEtapa.ESGOTADA);
        repository.save(pendente);

        ExecutorEtapa.DecisaoFalha decisao = decidir(permanente);
        log.warn("Etapa {} da solicitação {} falhou em definitivo após {} tentativa(s); decisão={}",
                pendente.getEtapa(), pendente.getSolicitacaoId(), tentativa, decisao);

        executor.aoFalharDefinitivamente(pendente, tentativa, permanente, motivo, decisao);
    }

    /** No modo Backward a falha dispara compensação imediata, sem retry. */
    private boolean podeTentarNovamente(int tentativa) {
        if (experimento.getEstrategia() == ExperimentoProperties.EstrategiaRecuperacao.BACKWARD) {
            return false;
        }
        return tentativa <= experimento.getLimiteTentativas();
    }

    private ExecutorEtapa.DecisaoFalha decidir(boolean permanente) {
        // Forward puro nunca desfaz o que já foi feito, mesmo desistindo.
        // Backward e Híbrido convergem para a cadeia de compensação — a
        // diferença entre os dois está em quantas tentativas vieram antes.
        if (experimento.getEstrategia() == ExperimentoProperties.EstrategiaRecuperacao.FORWARD) {
            return ExecutorEtapa.DecisaoFalha.ABORTAR;
        }
        return ExecutorEtapa.DecisaoFalha.COMPENSAR;
    }

    private long calcularBackoff(int tentativa) {
        long espera = (long) (experimento.getBackoffInicialMs() * Math.pow(2, tentativa - 1));
        return Math.min(espera, experimento.getBackoffMaximoMs());
    }
}
