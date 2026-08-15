package com.tcc.saga.experimento;

import com.tcc.saga.event.CanaisSaga;
import com.tcc.saga.event.EtapaSaga;
import com.tcc.saga.event.SagaAuditEvento;
import io.eventuate.tram.events.publisher.DomainEventPublisher;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publica os eventos de auditoria da saga no canal único {@code saga-audit}.
 *
 * Como usa o mesmo {@link DomainEventPublisher} transacional do Eventuate Tram,
 * o registro de auditoria entra na tabela de outbox dentro da mesma transação
 * local que ele descreve: ou os dois são gravados, ou nenhum. Isso é o que
 * garante que a linha do tempo reconstruída na análise não tenha buracos.
 */
@Component
@RequiredArgsConstructor
public class AuditoriaPublisher {

    private final DomainEventPublisher eventPublisher;
    private final ExperimentoProperties experimento;

    @Value("${spring.application.name}")
    private String servico;

    public void registrar(Long solicitacaoId, EtapaSaga etapa, SagaAuditEvento.AcaoAuditoria acao) {
        registrar(solicitacaoId, etapa, acao, 1, null);
    }

    public void registrar(Long solicitacaoId, EtapaSaga etapa, SagaAuditEvento.AcaoAuditoria acao, String mensagem) {
        registrar(solicitacaoId, etapa, acao, 1, mensagem);
    }

    public void registrar(Long solicitacaoId,
                          EtapaSaga etapa,
                          SagaAuditEvento.AcaoAuditoria acao,
                          int tentativa,
                          String mensagem) {

        SagaAuditEvento evento = SagaAuditEvento.builder()
                .runId(experimento.getRunId())
                .solicitacaoId(solicitacaoId)
                .servico(servico)
                .etapa(etapa)
                .acao(acao)
                .tentativa(tentativa)
                .estrategia(experimento.getEstrategia().name())
                .cenarioFalha(experimento.getCenarioFalha().name())
                .posicaoFalha(experimento.getPosicaoFalha() != null ? experimento.getPosicaoFalha().name() : null)
                .mensagem(mensagem)
                .timestampMillis(System.currentTimeMillis())
                .build();

        eventPublisher.publish(CanaisSaga.AUDITORIA, solicitacaoId, List.of(evento));
    }
}
