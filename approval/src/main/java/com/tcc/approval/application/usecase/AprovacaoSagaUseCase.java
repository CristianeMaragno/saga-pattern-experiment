package com.tcc.approval.application.usecase;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tcc.approval.domain.entity.Aprovacao;
import com.tcc.approval.domain.repository.AprovacaoRepository;
import com.tcc.saga.event.AprovacaoCancelada;
import com.tcc.saga.event.AprovacaoSolicitada;
import com.tcc.saga.event.CanaisSaga;
import com.tcc.saga.event.CompensacaoBookingConcluida;
import com.tcc.saga.event.EtapaSaga;
import com.tcc.saga.event.SagaAuditEvento;
import com.tcc.saga.event.SolicitacaoCriada;
import com.tcc.saga.experimento.AuditoriaPublisher;
import com.tcc.saga.experimento.ExperimentoProperties;
import com.tcc.saga.recuperacao.MotorRecuperacao;
import com.tcc.saga.recuperacao.PayloadEtapaMapper;

import io.eventuate.tram.events.publisher.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Participação do approval na saga (T2 e sua compensação).
 *
 * A decisão de aprovação é "de longa duração" na metodologia (dias). Aqui ela é
 * comprimida para um atraso configurável em segundos, agendado no motor de
 * recuperação — o mesmo mecanismo que executa as retentativas. Isso mantém a
 * natureza assíncrona da espera e viabiliza os testes de carga.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AprovacaoSagaUseCase {

    private static final long RESPONSAVEL_PADRAO = 101L;

    private final AprovacaoRepository aprovacaoRepository;
    private final DomainEventPublisher eventPublisher;
    private final AuditoriaPublisher auditoria;
    private final ExperimentoProperties experimento;
    private final MotorRecuperacao motorRecuperacao;
    private final PayloadEtapaMapper payloadMapper;

    /**
     * Abre a aprovação (T2) em reação a T1 e agenda a decisão.
     */
    @Transactional
    public void aoSolicitacaoCriada(SolicitacaoCriada evento) {
        Long solicitacaoId = evento.getSolicitacaoId();

        if (aprovacaoRepository.obterPorSolicitacaoId(solicitacaoId).isPresent()) {
            log.debug("Aprovação da saga {} já existe; ignorando reentrega", solicitacaoId);
            return;
        }

        Aprovacao aprovacao = Aprovacao.builder()
                .solicitacaoId(solicitacaoId)
                .solicitanteId(evento.getUsuarioId())
                .responsavelId(RESPONSAVEL_PADRAO)
                .tempoLimite(LocalDateTime.now().plusDays(2))
                .status(Aprovacao.StatusAprovacao.PENDENTE)
                .dataCriacao(LocalDateTime.now())
                .build();

        aprovacao.validar();
        Aprovacao salva = aprovacaoRepository.salvar(aprovacao);

        eventPublisher.publish(CanaisSaga.APROVACAO, solicitacaoId, List.of(
                AprovacaoSolicitada.builder()
                        .solicitacaoId(solicitacaoId)
                        .aprovacaoId(salva.getId())
                        .responsavelId(salva.getResponsavelId())
                        .build()));

        // A decisão em si (e a falha eventualmente injetada em T2) fica a cargo
        // do motor de recuperação, que a executa após o atraso configurado.
        motorRecuperacao.enfileirar(solicitacaoId, EtapaSaga.T2,
                payloadMapper.serializar(evento),
                experimento.getAtrasoDecisaoAprovacaoSegundos() * 1000L);
    }

    /**
     * Compensação de T2: penúltimo elo da cadeia backward.
     * Cancela a aprovação e passa a cadeia adiante, para o travel compensar T1.
     */
    @Transactional
    public void aoCompensacaoBookingConcluida(CompensacaoBookingConcluida evento) {
        Long solicitacaoId = evento.getSolicitacaoId();

        aprovacaoRepository.obterPorSolicitacaoId(solicitacaoId).ifPresent(aprovacao -> {
            if (aprovacao.getStatus() != Aprovacao.StatusAprovacao.CANCELADA) {
                aprovacao.cancelar();
                aprovacaoRepository.salvar(aprovacao);
                auditoria.registrar(solicitacaoId, EtapaSaga.T2, SagaAuditEvento.AcaoAuditoria.COMPENSACAO);
            }
        });

        Long aprovacaoId = aprovacaoRepository.obterPorSolicitacaoId(solicitacaoId)
                .map(Aprovacao::getId)
                .orElse(null);

        eventPublisher.publish(CanaisSaga.APROVACAO, solicitacaoId, List.of(
                AprovacaoCancelada.builder()
                        .solicitacaoId(solicitacaoId)
                        .aprovacaoId(aprovacaoId)
                        .etapaOrigemFalha(evento.getEtapaOrigemFalha())
                        .build()));
    }
}
