package com.tcc.audit.application.usecase;

import com.tcc.audit.application.dto.MetricasResponseDTO;
import com.tcc.audit.infrastructure.entity.RegistroAuditoriaJpaEntity;
import com.tcc.audit.infrastructure.repository.RegistroAuditoriaJpaRepository;
import com.tcc.saga.event.SagaAuditEvento;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava os eventos de auditoria e deriva as métricas do experimento.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditoriaUseCase {

    private final RegistroAuditoriaJpaRepository repository;

    @Transactional
    public void registrar(SagaAuditEvento evento) {
        repository.save(RegistroAuditoriaJpaEntity.builder()
                .runId(evento.getRunId())
                .solicitacaoId(evento.getSolicitacaoId())
                .servico(evento.getServico())
                .etapa(evento.getEtapa() != null ? evento.getEtapa().name() : null)
                .acao(evento.getAcao() != null ? evento.getAcao().name() : null)
                .tentativa(evento.getTentativa())
                .estrategia(evento.getEstrategia())
                .cenarioFalha(evento.getCenarioFalha())
                .posicaoFalha(evento.getPosicaoFalha())
                .mensagem(evento.getMensagem())
                .timestampMillis(evento.getTimestampMillis())
                .registradoEm(Instant.now())
                .build());
    }

    @Transactional(readOnly = true)
    public List<RegistroAuditoriaJpaEntity> listarPorRodada(String runId) {
        return repository.findByRunIdOrderBySolicitacaoIdAscTimestampMillisAsc(runId);
    }

    @Transactional
    public int limparRodada(String runId) {
        return repository.deleteByRunId(runId);
    }

    /**
     * Calcula as métricas da rodada a partir do resumo por saga.
     */
    @Transactional(readOnly = true)
    public MetricasResponseDTO calcularMetricas(String runId) {
        List<Object[]> resumo = repository.resumirSagas(runId);

        long totalSagas = 0;
        long concluidas = 0;
        long compensadas = 0;
        long abortadas = 0;
        long rejeitadas = 0;
        long totalCompensacoes = 0;
        long totalRetries = 0;
        long totalEventos = 0;
        long sagasComFalha = 0;
        long recuperadasSemCompensacao = 0;

        long somaConclusao = 0;
        long qtdConclusao = 0;
        long somaRecuperacao = 0;
        long qtdRecuperacao = 0;
        long somaTerminal = 0;
        long qtdTerminal = 0;

        long menorInicio = Long.MAX_VALUE;
        long maiorFim = Long.MIN_VALUE;

        for (Object[] linha : resumo) {
            Long inicio = valorLongOuNulo(linha[1]);
            Long fim = valorLongOuNulo(linha[2]);
            boolean concluida = valorLong(linha[3]) == 1;
            boolean compensada = valorLong(linha[4]) == 1;
            boolean abortada = valorLong(linha[5]) == 1;
            boolean rejeitada = valorLong(linha[6]) == 1;
            long falhas = valorLong(linha[7]);
            long retries = valorLong(linha[8]);
            long compensacoes = valorLong(linha[9]);
            Long primeiraFalha = valorLongOuNulo(linha[10]);
            long eventos = valorLong(linha[11]);

            // Sagas sem T1 registrado não são instâncias completas do
            // experimento (só podem existir se a auditoria de T1 se perdeu).
            if (inicio == null) {
                continue;
            }

            totalSagas++;
            totalEventos += eventos;
            totalCompensacoes += compensacoes;
            totalRetries += retries;

            if (concluida) {
                concluidas++;
            }
            if (compensada) {
                compensadas++;
            }
            if (abortada) {
                abortadas++;
            }
            if (rejeitada) {
                rejeitadas++;
            }

            menorInicio = Math.min(menorInicio, inicio);

            if (fim != null) {
                maiorFim = Math.max(maiorFim, fim);
                somaTerminal += fim - inicio;
                qtdTerminal++;

                if (concluida) {
                    somaConclusao += fim - inicio;
                    qtdConclusao++;
                }
            }

            if (falhas > 0) {
                sagasComFalha++;
                if (fim != null && primeiraFalha != null) {
                    somaRecuperacao += fim - primeiraFalha;
                    qtdRecuperacao++;
                }
                // Recuperada sem compensação: falhou, chegou a T7 e nada foi desfeito.
                if (concluida && compensacoes == 0) {
                    recuperadasSemCompensacao++;
                }
            }
        }

        long duracao = (totalSagas > 0 && maiorFim > menorInicio) ? maiorFim - menorInicio : 0;

        return MetricasResponseDTO.builder()
                .runId(runId)
                .totalSagas(totalSagas)
                .sagasConcluidas(concluidas)
                .sagasCompensadas(compensadas)
                .sagasAbortadas(abortadas)
                .sagasRejeitadas(rejeitadas)
                .sagasEmAndamento(totalSagas - qtdTerminal)
                .taxaSucesso(divisao(concluidas, totalSagas))
                .tempoMedioConclusaoMs(divisao(somaConclusao, qtdConclusao))
                .tempoMedioRecuperacaoMs(divisao(somaRecuperacao, qtdRecuperacao))
                .tempoAteConsistenciaEventualMs(divisao(somaTerminal, qtdTerminal))
                .totalCompensacoes(totalCompensacoes)
                .totalRetries(totalRetries)
                .totalEventos(totalEventos)
                .sagasComFalha(sagasComFalha)
                .sagasRecuperadasSemCompensacao(recuperadasSemCompensacao)
                .percentualRecuperadasSemCompensacao(divisao(recuperadasSemCompensacao, sagasComFalha) * 100)
                .throughputSagasPorSegundo(duracao > 0 ? totalSagas / (duracao / 1000.0) : 0)
                .duracaoRodadaMs(duracao)
                .build();
    }

    private static double divisao(long numerador, long denominador) {
        return denominador == 0 ? 0 : (double) numerador / denominador;
    }

    private static long valorLong(Object valor) {
        return valor == null ? 0L : ((Number) valor).longValue();
    }

    private static Long valorLongOuNulo(Object valor) {
        return valor == null ? null : ((Number) valor).longValue();
    }
}
