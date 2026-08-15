package com.tcc.approval.application.usecase;

import java.util.List;

import org.springframework.stereotype.Component;

import com.tcc.approval.domain.entity.Aprovacao;
import com.tcc.approval.domain.repository.AprovacaoRepository;
import com.tcc.saga.event.AprovacaoAprovada;
import com.tcc.saga.event.AprovacaoRejeitada;
import com.tcc.saga.event.CanaisSaga;
import com.tcc.saga.event.EtapaSaga;
import com.tcc.saga.event.SagaAbortada;
import com.tcc.saga.event.SagaAuditEvento;
import com.tcc.saga.event.SagaFalhou;
import com.tcc.saga.event.SolicitacaoCriada;
import com.tcc.saga.experimento.AuditoriaPublisher;
import com.tcc.saga.experimento.InjetorFalha;
import com.tcc.saga.recuperacao.EtapaPendenteJpaEntity;
import com.tcc.saga.recuperacao.ExecutorEtapa;
import com.tcc.saga.recuperacao.PayloadEtapaMapper;

import io.eventuate.tram.events.publisher.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * T2 — decisão da aprovação, e o que fazer quando ela falha.
 *
 * O caminho de falha permanente reaproveita a rejeição de negócio que a
 * entidade já modelava: uma aprovação recusada é o desfecho natural e não
 * precisa de compensação nenhuma, porque T2 ainda está antes do ponto de pivô
 * e nada foi reservado. Por isso, para uma falha permanente em T2, as três
 * estratégias convergem para o mesmo resultado — o que é, em si, um achado do
 * experimento, não uma limitação da implementação.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutorAprovacao implements ExecutorEtapa {

    private final AprovacaoRepository aprovacaoRepository;
    private final DomainEventPublisher eventPublisher;
    private final AuditoriaPublisher auditoria;
    private final InjetorFalha injetorFalha;
    private final PayloadEtapaMapper payloadMapper;

    @Override
    public EtapaSaga etapa() {
        return EtapaSaga.T2;
    }

    @Override
    public void executar(EtapaPendenteJpaEntity pendente, int tentativa) {
        Long solicitacaoId = pendente.getSolicitacaoId();
        SolicitacaoCriada origem = payloadMapper.desserializar(pendente.getPayload(), SolicitacaoCriada.class);

        // Antes de qualquer efeito colateral, para que uma falha não deixe
        // estado pela metade que a retentativa teria de limpar.
        injetorFalha.verificar(EtapaSaga.T2, solicitacaoId, tentativa);

        Aprovacao aprovacao = aprovacaoRepository.obterPorSolicitacaoId(solicitacaoId)
                .orElseThrow(() -> new IllegalStateException("Aprovação não encontrada para a saga " + solicitacaoId));

        if (aprovacao.getStatus() == Aprovacao.StatusAprovacao.PENDENTE) {
            aprovacao.aprovar();
            aprovacaoRepository.salvar(aprovacao);
        }

        eventPublisher.publish(CanaisSaga.APROVACAO, solicitacaoId, List.of(
                AprovacaoAprovada.builder()
                        .solicitacaoId(solicitacaoId)
                        .aprovacaoId(aprovacao.getId())
                        .valorVoo(origem.getValorVoo())
                        .valorHotel(origem.getValorHotel())
                        .build()));
    }

    @Override
    public void aoFalharDefinitivamente(EtapaPendenteJpaEntity pendente,
                                        int tentativa,
                                        boolean permanente,
                                        String motivo,
                                        DecisaoFalha decisao) {

        Long solicitacaoId = pendente.getSolicitacaoId();

        if (permanente) {
            Aprovacao aprovacao = aprovacaoRepository.obterPorSolicitacaoId(solicitacaoId).orElse(null);
            Long aprovacaoId = aprovacao != null ? aprovacao.getId() : null;

            if (aprovacao != null && aprovacao.getStatus() == Aprovacao.StatusAprovacao.PENDENTE) {
                aprovacao.rejeitar();
                aprovacaoRepository.salvar(aprovacao);
            }

            eventPublisher.publish(CanaisSaga.APROVACAO, solicitacaoId, List.of(
                    AprovacaoRejeitada.builder()
                            .solicitacaoId(solicitacaoId)
                            .aprovacaoId(aprovacaoId)
                            .motivo(motivo)
                            .build()));
            return;
        }

        if (decisao == DecisaoFalha.ABORTAR) {
            eventPublisher.publish(CanaisSaga.SAGA, solicitacaoId, List.of(
                    SagaAbortada.builder()
                            .solicitacaoId(solicitacaoId)
                            .etapa(EtapaSaga.T2)
                            .servico("approval")
                            .motivo(motivo)
                            .tentativas(tentativa)
                            .build()));
            return;
        }

        auditoria.registrar(solicitacaoId, EtapaSaga.T2, SagaAuditEvento.AcaoAuditoria.FALHA, tentativa,
                "Disparando cadeia de compensação");

        eventPublisher.publish(CanaisSaga.SAGA, solicitacaoId, List.of(
                SagaFalhou.builder()
                        .solicitacaoId(solicitacaoId)
                        .etapa(EtapaSaga.T2)
                        .servico("approval")
                        .motivo(motivo)
                        .permanente(false)
                        .tentativas(tentativa)
                        .build()));
    }
}
