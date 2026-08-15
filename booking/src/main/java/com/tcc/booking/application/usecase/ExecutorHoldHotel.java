package com.tcc.booking.application.usecase;

import java.util.List;

import org.springframework.stereotype.Component;

import com.tcc.booking.domain.entity.Hold;
import com.tcc.booking.domain.repository.HoldRepository;
import com.tcc.saga.event.AprovacaoAprovada;
import com.tcc.saga.event.CanaisSaga;
import com.tcc.saga.event.EtapaSaga;
import com.tcc.saga.event.HoldHotelCriado;
import com.tcc.saga.event.SagaAbortada;
import com.tcc.saga.event.SagaFalhou;
import com.tcc.saga.experimento.InjetorFalha;
import com.tcc.saga.recuperacao.EtapaPendenteJpaEntity;
import com.tcc.saga.recuperacao.ExecutorEtapa;
import com.tcc.saga.recuperacao.PayloadEtapaMapper;

import io.eventuate.tram.events.publisher.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * T4 — hold de hotel, a posição intermediária de falha do experimento.
 *
 * É o caso mais interessante para comparar as estratégias: T3 já concluiu, mas
 * a saga ainda está antes do ponto de pivô, então a compensação é possível e
 * não tem custo financeiro.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutorHoldHotel implements ExecutorEtapa {

    private final HoldRepository holdRepository;
    private final HoldUseCase holdUseCase;
    private final DomainEventPublisher eventPublisher;
    private final InjetorFalha injetorFalha;
    private final PayloadEtapaMapper payloadMapper;

    @Override
    public EtapaSaga etapa() {
        return EtapaSaga.T4;
    }

    @Override
    public void executar(EtapaPendenteJpaEntity pendente, int tentativa) {
        Long solicitacaoId = pendente.getSolicitacaoId();
        AprovacaoAprovada origem = payloadMapper.desserializar(pendente.getPayload(), AprovacaoAprovada.class);

        // Antes de qualquer efeito colateral, para que uma falha não deixe
        // estado pela metade que a retentativa teria de limpar.
        injetorFalha.verificar(EtapaSaga.T4, solicitacaoId, tentativa);

        Hold hotel = holdRepository.obterPorSolicitacaoIdETipo(solicitacaoId, Hold.HoldType.HOTEL)
                .orElseGet(() -> holdUseCase.criarHold(solicitacaoId, Hold.HoldType.HOTEL,
                        HoldSagaUseCase.referencia(solicitacaoId), HoldUseCase.HOTEL_DEFAULT_HOURS));

        eventPublisher.publish(CanaisSaga.HOLD, solicitacaoId, List.of(
                HoldHotelCriado.builder()
                        .solicitacaoId(solicitacaoId)
                        .holdId(hotel.getId())
                        .referencia(hotel.getReference())
                        .expiraEmMillis(hotel.getExpiresAt().toEpochMilli())
                        .valor(origem.getValorHotel())
                        .build()));
    }

    @Override
    public void aoFalharDefinitivamente(EtapaPendenteJpaEntity pendente,
                                        int tentativa,
                                        boolean permanente,
                                        String motivo,
                                        DecisaoFalha decisao) {

        Long solicitacaoId = pendente.getSolicitacaoId();

        if (decisao == DecisaoFalha.ABORTAR) {
            eventPublisher.publish(CanaisSaga.SAGA, solicitacaoId, List.of(
                    SagaAbortada.builder()
                            .solicitacaoId(solicitacaoId)
                            .etapa(EtapaSaga.T4)
                            .servico("booking")
                            .motivo(motivo)
                            .tentativas(tentativa)
                            .build()));
            return;
        }

        eventPublisher.publish(CanaisSaga.SAGA, solicitacaoId, List.of(
                SagaFalhou.builder()
                        .solicitacaoId(solicitacaoId)
                        .etapa(EtapaSaga.T4)
                        .servico("booking")
                        .motivo(motivo)
                        .permanente(permanente)
                        .tentativas(tentativa)
                        .build()));
    }
}
