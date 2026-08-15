package com.tcc.travel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.tcc.saga.event.AprovacaoAprovada;
import com.tcc.saga.event.AprovacaoCancelada;
import com.tcc.saga.event.AprovacaoRejeitada;
import com.tcc.saga.event.AprovacaoSolicitada;
import com.tcc.saga.event.CanaisSaga;
import com.tcc.saga.event.EtapaSaga;
import com.tcc.saga.event.PagamentoHotelConfirmado;
import com.tcc.saga.event.PagamentoVooConfirmado;
import com.tcc.saga.event.SagaAbortada;
import com.tcc.saga.event.SagaAuditEvento;
import com.tcc.saga.event.SolicitacaoCriada;
import com.tcc.travel.application.dto.CreateSolicitacaoRequestDTO;
import com.tcc.travel.application.usecase.SolicitacaoUseCase;
import com.tcc.travel.domain.entity.Solicitacao;

import io.eventuate.tram.events.common.DomainEvent;
import io.eventuate.tram.events.publisher.DomainEventPublisher;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Participação do travel na saga: T1, o ponto de junção dos dois ramos, T7 e os
 * desfechos de falha.
 *
 * Roda com mensageria em memória, então exercita os handlers e a serialização
 * reais do Eventuate Tram sem precisar de Kafka nem Docker.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Saga — travel (T1, junção e T7)")
class SagaTravelIntegrationTest {

    @Autowired
    private SolicitacaoUseCase solicitacaoUseCase;

    @Autowired
    private DomainEventPublisher eventPublisher;

    @Autowired
    private ColetorEventos coletor;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void limpar() {
        coletor.limpar();
    }

    private Solicitacao criarSolicitacao() {
        CreateSolicitacaoRequestDTO request = new CreateSolicitacaoRequestDTO(
                1L, "São Paulo", LocalDate.now().plusDays(10), LocalDate.now().plusDays(15), "Reunião com cliente");
        return solicitacaoUseCase.criarSolicitacao(request);
    }

    private void publicar(String canal, Long solicitacaoId, DomainEvent evento) {
        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publish(canal, solicitacaoId, List.of(evento)));
    }

    /** Leva a saga até APROVADA, que é o pré-requisito de T7. */
    private Solicitacao aprovar(Solicitacao solicitacao) {
        Long id = solicitacao.getId();
        publicar(CanaisSaga.APROVACAO, id, AprovacaoSolicitada.builder()
                .solicitacaoId(id).aprovacaoId(10L).responsavelId(101L).build());
        await().atMost(Duration.ofSeconds(5)).until(() ->
                solicitacaoUseCase.obter(id).getStatus() == Solicitacao.StatusSolicitacao.PENDENTE);

        publicar(CanaisSaga.APROVACAO, id, AprovacaoAprovada.builder()
                .solicitacaoId(id).aprovacaoId(10L).build());
        await().atMost(Duration.ofSeconds(5)).until(() ->
                solicitacaoUseCase.obter(id).getStatus() == Solicitacao.StatusSolicitacao.APROVADA);

        return solicitacaoUseCase.obter(id);
    }

    @Test
    @DisplayName("T1 cria em RASCUNHO e publica solicitacao.criada com o id de correlação")
    void t1PublicaEventoDeAberturaDaSaga() {
        Solicitacao solicitacao = criarSolicitacao();

        assertThat(solicitacao.getStatus()).isEqualTo(Solicitacao.StatusSolicitacao.RASCUNHO);
        assertThat(solicitacao.getResultadoSaga()).isNull();

        await().atMost(Duration.ofSeconds(5)).until(() -> coletor.recebeu(SolicitacaoCriada.class));

        SolicitacaoCriada evento = coletor.primeiro(SolicitacaoCriada.class);
        assertThat(evento.getSolicitacaoId()).isEqualTo(solicitacao.getId());
        assertThat(evento.getDestino()).isEqualTo("São Paulo");
        assertThat(evento.getValorVoo()).isNotNull();
        assertThat(evento.getValorHotel()).isNotNull();

        await().atMost(Duration.ofSeconds(5)).until(() ->
                coletor.contarAuditoria(SagaAuditEvento.AcaoAuditoria.SAGA_INICIADA) == 1);
    }

    @Test
    @DisplayName("caminho feliz: T7 confirma somente quando os dois ramos chegam")
    void t7ConfirmaApenasComOsDoisRamos() {
        Solicitacao solicitacao = aprovar(criarSolicitacao());
        Long id = solicitacao.getId();

        // Só o ramo do voo: a junção ainda não fechou.
        publicar(CanaisSaga.PAGAMENTO, id, PagamentoVooConfirmado.builder()
                .solicitacaoId(id).pagamentoId(20L).build());
        await().atMost(Duration.ofSeconds(5)).until(() ->
                solicitacaoUseCase.obter(id).isPagamentoVooConfirmado());

        assertThat(solicitacaoUseCase.obter(id).getStatus())
                .isEqualTo(Solicitacao.StatusSolicitacao.APROVADA);

        // Chega o ramo do hotel: T7 executa.
        publicar(CanaisSaga.PAGAMENTO, id, PagamentoHotelConfirmado.builder()
                .solicitacaoId(id).pagamentoId(21L).build());
        await().atMost(Duration.ofSeconds(5)).until(() ->
                solicitacaoUseCase.obter(id).getStatus() == Solicitacao.StatusSolicitacao.CONFIRMADO);

        Solicitacao confirmada = solicitacaoUseCase.obter(id);
        assertThat(confirmada.getResultadoSaga()).isEqualTo(Solicitacao.ResultadoSaga.SUCESSO);
        assertThat(coletor.contarAuditoria(SagaAuditEvento.AcaoAuditoria.SAGA_CONCLUIDA)).isEqualTo(1);
    }

    @Test
    @DisplayName("reentrega do mesmo pagamento não confirma a saga duas vezes")
    void reentregaDePagamentoEIdempotente() {
        Solicitacao solicitacao = aprovar(criarSolicitacao());
        Long id = solicitacao.getId();

        PagamentoVooConfirmado voo = PagamentoVooConfirmado.builder()
                .solicitacaoId(id).pagamentoId(20L).build();

        publicar(CanaisSaga.PAGAMENTO, id, voo);
        await().atMost(Duration.ofSeconds(5)).until(() ->
                solicitacaoUseCase.obter(id).isPagamentoVooConfirmado());

        // Mesma mensagem de novo, como o Kafka pode reentregar.
        publicar(CanaisSaga.PAGAMENTO, id, voo);
        publicar(CanaisSaga.PAGAMENTO, id, PagamentoHotelConfirmado.builder()
                .solicitacaoId(id).pagamentoId(21L).build());

        await().atMost(Duration.ofSeconds(5)).until(() ->
                solicitacaoUseCase.obter(id).getStatus() == Solicitacao.StatusSolicitacao.CONFIRMADO);

        // T7 só pode ter acontecido uma vez.
        assertThat(coletor.contarAuditoria(SagaAuditEvento.AcaoAuditoria.SAGA_CONCLUIDA)).isEqualTo(1);
    }

    @Test
    @DisplayName("aprovação rejeitada encerra a saga sem compensação")
    void rejeicaoEncerraSagaAntesDoPivo() {
        Solicitacao solicitacao = criarSolicitacao();
        Long id = solicitacao.getId();

        publicar(CanaisSaga.APROVACAO, id, AprovacaoSolicitada.builder()
                .solicitacaoId(id).aprovacaoId(10L).responsavelId(101L).build());
        await().atMost(Duration.ofSeconds(5)).until(() ->
                solicitacaoUseCase.obter(id).getStatus() == Solicitacao.StatusSolicitacao.PENDENTE);

        publicar(CanaisSaga.APROVACAO, id, AprovacaoRejeitada.builder()
                .solicitacaoId(id).aprovacaoId(10L).motivo("orçamento indisponível").build());

        await().atMost(Duration.ofSeconds(5)).until(() ->
                solicitacaoUseCase.obter(id).getStatus() == Solicitacao.StatusSolicitacao.REJEITADA);

        assertThat(solicitacaoUseCase.obter(id).getResultadoSaga())
                .isEqualTo(Solicitacao.ResultadoSaga.REJEITADA);
        assertThat(coletor.contarAuditoria(SagaAuditEvento.AcaoAuditoria.SAGA_REJEITADA)).isEqualTo(1);
    }

    @Test
    @DisplayName("fim da cadeia backward: compensa T1 e marca a saga como COMPENSADA")
    void compensacaoDeT1FechaACadeiaBackward() {
        Solicitacao solicitacao = aprovar(criarSolicitacao());
        Long id = solicitacao.getId();

        publicar(CanaisSaga.APROVACAO, id, AprovacaoCancelada.builder()
                .solicitacaoId(id).aprovacaoId(10L).etapaOrigemFalha(EtapaSaga.T4).build());

        await().atMost(Duration.ofSeconds(5)).until(() ->
                solicitacaoUseCase.obter(id).getStatus() == Solicitacao.StatusSolicitacao.CANCELADA);

        Solicitacao compensada = solicitacaoUseCase.obter(id);
        assertThat(compensada.getResultadoSaga()).isEqualTo(Solicitacao.ResultadoSaga.COMPENSADA);
        assertThat(compensada.getEtapaFalha()).isEqualTo("T4");
        assertThat(coletor.contarAuditoria(SagaAuditEvento.AcaoAuditoria.SAGA_COMPENSADA)).isEqualTo(1);
    }

    @Test
    @DisplayName("Forward esgotado: saga abortada tem desfecho distinto do compensado")
    void abortoSemCompensacaoTemResultadoProprio() {
        Solicitacao solicitacao = aprovar(criarSolicitacao());
        Long id = solicitacao.getId();

        publicar(CanaisSaga.SAGA, id, SagaAbortada.builder()
                .solicitacaoId(id).etapa(EtapaSaga.T6).servico("payment")
                .motivo("tentativas esgotadas").tentativas(4).build());

        await().atMost(Duration.ofSeconds(5)).until(() ->
                solicitacaoUseCase.obter(id).getStatus() == Solicitacao.StatusSolicitacao.CANCELADA);

        Solicitacao abortada = solicitacaoUseCase.obter(id);
        assertThat(abortada.getResultadoSaga()).isEqualTo(Solicitacao.ResultadoSaga.ABORTADA);
        assertThat(abortada.getEtapaFalha()).isEqualTo("T6");
        assertThat(coletor.contarAuditoria(SagaAuditEvento.AcaoAuditoria.SAGA_ABORTADA)).isEqualTo(1);
    }
}
