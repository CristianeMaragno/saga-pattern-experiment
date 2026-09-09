package com.tcc.travel.application.usecase;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.tcc.saga.experimento.AuditoriaPublisher;
import com.tcc.saga.experimento.ExperimentoProperties;
import com.tcc.travel.application.dto.CreateSolicitacaoRequestDTO;
import com.tcc.travel.domain.entity.Solicitacao;
import com.tcc.travel.domain.exception.ResourceNotFoundException;
import com.tcc.travel.domain.repository.SolicitacaoRepository;

import io.eventuate.tram.events.publisher.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso para operações com Solicitações de Viagem.
 *
 * O travel é o único ponto de entrada HTTP externo do experimento: abre a saga
 * em T1 e a fecha em T7, no ponto de junção dos dois ramos paralelos (voo e
 * hotel). Entre um e outro ele apenas reage a eventos, como qualquer outro
 * participante da coreografia — não há orquestrador.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SolicitacaoUseCase {

    private final SolicitacaoRepository solicitacaoRepository;
    private final DomainEventPublisher eventPublisher;
    private final AuditoriaPublisher auditoria;
    private final ExperimentoProperties experimento;

    /**
     * T1 — cria a solicitação em RASCUNHO e publica o evento que dispara a saga.
     *
     * A gravação e a publicação acontecem na mesma transação local: o evento vai
     * para a tabela de outbox do Eventuate Tram e só é publicado no Kafka pelo
     * CDC depois do commit, então não existe estado gravado sem evento nem
     * evento sem estado gravado.
     */
    @Transactional
    public Solicitacao criarSolicitacao(CreateSolicitacaoRequestDTO request) {
        Solicitacao solicitacao = Solicitacao.builder()
                .usuarioId(request.getUsuarioId())
                .destino(request.getDestino())
                .dataIda(request.getDataIda())
                .dataVolta(request.getDataVolta())
                .motivo(request.getMotivo())
                .status(Solicitacao.StatusSolicitacao.RASCUNHO)
                .valorVoo(experimento.getValorVoo())
                .valorHotel(experimento.getValorHotel())
                .dataCriacao(LocalDateTime.now())
                .build();

        solicitacao.validar();
        Solicitacao salva = solicitacaoRepository.salvar(solicitacao);

        eventPublisher.publish(CanaisSaga.SOLICITACAO, salva.getId(), List.of(
                SolicitacaoCriada.builder()
                        .solicitacaoId(salva.getId())
                        .usuarioId(salva.getUsuarioId())
                        .destino(salva.getDestino())
                        .dataIda(salva.getDataIda() != null ? salva.getDataIda().toString() : null)
                        .dataVolta(salva.getDataVolta() != null ? salva.getDataVolta().toString() : null)
                        .motivo(salva.getMotivo())
                        .valorVoo(salva.getValorVoo())
                        .valorHotel(salva.getValorHotel())
                        .build()));

        auditoria.registrar(salva.getId(), EtapaSaga.T1, SagaAuditEvento.AcaoAuditoria.SAGA_INICIADA);
        auditoria.registrar(salva.getId(), EtapaSaga.T1, SagaAuditEvento.AcaoAuditoria.SUCESSO);

        return salva;
    }

    /**
     * A aprovação foi aberta no approval: a saga passa a aguardar a decisão.
     */
    @Transactional
    public void aoAprovacaoSolicitada(AprovacaoSolicitada evento) {
        Solicitacao solicitacao = obterSeExistir(evento.getSolicitacaoId());
        if (solicitacao == null || solicitacao.getStatus() != Solicitacao.StatusSolicitacao.RASCUNHO) {
            return; // reentrega, ou rodada de experimento já limpa
        }
        solicitacao.aguardarAprovacao();
        solicitacaoRepository.salvar(solicitacao);
    }

    /**
     * T2 aprovada: a saga segue para os ramos de reserva.
     */
    @Transactional
    public void aoAprovacaoAprovada(AprovacaoAprovada evento) {
        Solicitacao solicitacao = obterSeExistir(evento.getSolicitacaoId());
        if (solicitacao == null || solicitacao.getStatus() != Solicitacao.StatusSolicitacao.PENDENTE) {
            return; // reentrega, ou rodada de experimento já limpa
        }
        solicitacao.aprovar();
        solicitacaoRepository.salvar(solicitacao);
    }

    /**
     * T2 rejeitada: fim da saga sem custo, ainda antes do ponto de pivô —
     * nada a compensar, porque nenhuma reserva foi criada.
     */
    @Transactional
    public void aoAprovacaoRejeitada(AprovacaoRejeitada evento) {
        Solicitacao solicitacao = obterSeExistir(evento.getSolicitacaoId());
        if (solicitacao == null || solicitacao.isTerminal()) {
            return; // reentrega, ou rodada de experimento já limpa
        }
        solicitacao.rejeitar();
        solicitacaoRepository.salvar(solicitacao);

        auditoria.registrar(solicitacao.getId(), EtapaSaga.T2,
                SagaAuditEvento.AcaoAuditoria.SAGA_REJEITADA, evento.getMotivo());
    }

    /**
     * T5 concluída: registra a chegada do ramo do voo no ponto de junção.
     */
    @Transactional
    public void aoPagamentoVooConfirmado(PagamentoVooConfirmado evento) {
        Solicitacao solicitacao = obterSeExistir(evento.getSolicitacaoId());
        if (solicitacao != null && solicitacao.registrarPagamentoVoo()) {
            solicitacaoRepository.salvar(solicitacao);
            confirmarSeAmbosOsRamosChegaram(solicitacao);
        }
    }

    /**
     * T6 concluída: registra a chegada do ramo do hotel no ponto de junção.
     */
    @Transactional
    public void aoPagamentoHotelConfirmado(PagamentoHotelConfirmado evento) {
        Solicitacao solicitacao = obterSeExistir(evento.getSolicitacaoId());
        if (solicitacao != null && solicitacao.registrarPagamentoHotel()) {
            solicitacaoRepository.salvar(solicitacao);
            confirmarSeAmbosOsRamosChegaram(solicitacao);
        }
    }

    /**
     * Último elo da cadeia backward: compensa T1 encerrando a solicitação.
     */
    @Transactional
    public void aoAprovacaoCancelada(AprovacaoCancelada evento) {
        Solicitacao solicitacao = obterSeExistir(evento.getSolicitacaoId());
        if (solicitacao == null || solicitacao.isTerminal()) {
            return; // reentrega, ou rodada de experimento já limpa
        }
        String etapaFalha = evento.getEtapaOrigemFalha() != null ? evento.getEtapaOrigemFalha().name() : null;
        solicitacao.cancelar(Solicitacao.ResultadoSaga.COMPENSADA, etapaFalha);
        solicitacaoRepository.salvar(solicitacao);

        auditoria.registrar(solicitacao.getId(), EtapaSaga.T1, SagaAuditEvento.AcaoAuditoria.COMPENSACAO);
        auditoria.registrar(solicitacao.getId(), EtapaSaga.T1, SagaAuditEvento.AcaoAuditoria.SAGA_COMPENSADA,
                "Compensação originada em " + etapaFalha);
    }

    /**
     * Forward Recovery com tentativas esgotadas: a saga termina sem compensação.
     * O que já foi reservado e pago permanece — é justamente o que diferencia
     * este modo do Backward, e o que a análise precisa conseguir enxergar.
     */
    @Transactional
    public void aoSagaAbortada(SagaAbortada evento) {
        Solicitacao solicitacao = obterSeExistir(evento.getSolicitacaoId());
        if (solicitacao == null || solicitacao.isTerminal()) {
            return; // reentrega, ou rodada de experimento já limpa
        }
        String etapaFalha = evento.getEtapa() != null ? evento.getEtapa().name() : null;
        solicitacao.cancelar(Solicitacao.ResultadoSaga.ABORTADA, etapaFalha);
        solicitacaoRepository.salvar(solicitacao);

        auditoria.registrar(solicitacao.getId(), EtapaSaga.T1, SagaAuditEvento.AcaoAuditoria.SAGA_ABORTADA,
                evento.getTentativas(), "Tentativas esgotadas em " + etapaFalha);
    }

    /**
     * T7 — confirma a viagem quando os dois ramos paralelos tiverem chegado.
     */
    private void confirmarSeAmbosOsRamosChegaram(Solicitacao solicitacao) {
        if (!solicitacao.prontaParaConfirmar()) {
            return;
        }
        auditoria.registrar(solicitacao.getId(), EtapaSaga.T7, SagaAuditEvento.AcaoAuditoria.INICIO);

        solicitacao.confirmar();
        solicitacaoRepository.salvar(solicitacao);

        auditoria.registrar(solicitacao.getId(), EtapaSaga.T7, SagaAuditEvento.AcaoAuditoria.SUCESSO);
        auditoria.registrar(solicitacao.getId(), EtapaSaga.T7, SagaAuditEvento.AcaoAuditoria.SAGA_CONCLUIDA);
    }

    /**
     * Lista todas as solicitações de viagem.
     *
     * @return lista de solicitações
     */
    public List<Solicitacao> listarTodas() {
        return solicitacaoRepository.obterTodas();
    }

    /**
     * Obtém uma solicitação por ID.
     */
    public Solicitacao obter(Long id) {
        return solicitacaoRepository.obterPorId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitação não encontrada com ID: " + id));
    }

    /**
     * Busca sem lançar, para uso nos handlers de evento.
     *
     * Os tópicos Kafka não são limpos entre rodadas do experimento — só as
     * tabelas de negócio são (TRUNCATE em limpar_bancos.sh). Um handler que
     * ainda esteja processando o backlog de uma rodada anterior pode receber
     * um evento para um ID que já não existe mais. Deixar {@link #obter}
     * lançar aqui derrubaria o consumidor Kafka inteiro — o Eventuate Tram
     * trata qualquer exceção do handler como fatal e encerra a inscrição — em
     * vez de simplesmente descartar essa mensagem tardia.
     */
    private Solicitacao obterSeExistir(Long id) {
        Solicitacao solicitacao = solicitacaoRepository.obterPorId(id).orElse(null);
        if (solicitacao == null) {
            log.warn("Solicitação {} não encontrada (mensagem tardia de rodada anterior?); ignorando evento", id);
        }
        return solicitacao;
    }
}
