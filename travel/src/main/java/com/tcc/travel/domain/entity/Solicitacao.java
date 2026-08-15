package com.tcc.travel.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidade de domínio que representa uma Solicitação de Viagem.
 * Esta entidade contém apenas lógica e atributos de negócio,
 * sem nenhuma dependência de Spring ou frameworks de persistência.
 *
 * <p>É também o agregado que guarda o estado da instância de saga: o ciclo
 * RASCUNHO → PENDENTE → APROVADA → CONFIRMADO acompanha T1, T2 e T7, e o
 * {@link ResultadoSaga} registra o desfecho para as métricas do experimento.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Solicitacao {

    private Long id;
    private Long usuarioId;
    private String destino;
    private LocalDate dataIda;
    private LocalDate dataVolta;
    private String motivo;
    private StatusSolicitacao status;
    private LocalDateTime dataCriacao;
    private LocalDateTime dataAtualizacao;

    private ResultadoSaga resultadoSaga;
    private String etapaFalha;
    private BigDecimal valorVoo;
    private BigDecimal valorHotel;

    /** Ponto de junção do ramo do voo (T5). */
    @Builder.Default
    private boolean pagamentoVooConfirmado = false;

    /** Ponto de junção do ramo do hotel (T6). */
    @Builder.Default
    private boolean pagamentoHotelConfirmado = false;

    /**
     * Valida regras de negócio para criação da solicitação.
     * Exemplo de lógica pura de domínio.
     */
    public void validar() {
        if (destino == null || destino.isBlank()) {
            throw new IllegalArgumentException("Destino é obrigatório");
        }
        if (dataIda == null) {
            throw new IllegalArgumentException("Data de ida é obrigatória");
        }
        if (dataVolta != null && dataVolta.isBefore(dataIda)) {
            throw new IllegalArgumentException("Data de volta não pode ser anterior à data de ida");
        }
    }

    /**
     * Marca que a aprovação (T2) foi aberta e a saga aguarda a decisão.
     * RASCUNHO é o estado logo após T1; PENDENTE é "aguardando aprovação".
     */
    public void aguardarAprovacao() {
        if (this.status != StatusSolicitacao.RASCUNHO) {
            throw new IllegalStateException("Apenas solicitações em RASCUNHO podem aguardar aprovação");
        }
        this.status = StatusSolicitacao.PENDENTE;
        this.dataAtualizacao = LocalDateTime.now();
    }

    /**
     * Aprova a solicitação.
     */
    public void aprovar() {
        if (this.status != StatusSolicitacao.PENDENTE) {
            throw new IllegalStateException("Apenas solicitações pendentes podem ser aprovadas");
        }
        this.status = StatusSolicitacao.APROVADA;
        this.dataAtualizacao = LocalDateTime.now();
    }

    /**
     * Rejeita a solicitação.
     */
    public void rejeitar() {
        if (this.status != StatusSolicitacao.PENDENTE) {
            throw new IllegalStateException("Apenas solicitações pendentes podem ser rejeitadas");
        }
        this.status = StatusSolicitacao.REJEITADA;
        this.resultadoSaga = ResultadoSaga.REJEITADA;
        this.dataAtualizacao = LocalDateTime.now();
    }

    /**
     * Compensação de T1: encerra a saga sem sucesso.
     * Aceita qualquer estado não terminal porque a falha pode vir de qualquer
     * etapa, e é idempotente porque o evento de fim de saga pode ser reentregue.
     */
    public void cancelar(ResultadoSaga resultado, String etapaFalha) {
        if (isTerminal()) {
            return;
        }
        this.status = StatusSolicitacao.CANCELADA;
        this.resultadoSaga = resultado;
        this.etapaFalha = etapaFalha;
        this.dataAtualizacao = LocalDateTime.now();
    }

    /**
     * Registra a chegada do pagamento do voo (T5) no ponto de junção.
     *
     * @return {@code true} se este registro é novo (não é uma reentrega)
     */
    public boolean registrarPagamentoVoo() {
        if (this.pagamentoVooConfirmado) {
            return false;
        }
        this.pagamentoVooConfirmado = true;
        this.dataAtualizacao = LocalDateTime.now();
        return true;
    }

    /**
     * Registra a chegada do pagamento do hotel (T6) no ponto de junção.
     *
     * @return {@code true} se este registro é novo (não é uma reentrega)
     */
    public boolean registrarPagamentoHotel() {
        if (this.pagamentoHotelConfirmado) {
            return false;
        }
        this.pagamentoHotelConfirmado = true;
        this.dataAtualizacao = LocalDateTime.now();
        return true;
    }

    /** Os dois ramos paralelos (voo e hotel) chegaram: T7 pode executar. */
    public boolean prontaParaConfirmar() {
        return pagamentoVooConfirmado && pagamentoHotelConfirmado
                && status == StatusSolicitacao.APROVADA;
    }

    /**
     * Confirma a viagem (T7 do experimento).
     * Só faz sentido depois da aprovação (T2) e dos dois pagamentos (T5 e T6);
     * é o estado final do caminho feliz da saga.
     */
    public void confirmar() {
        if (this.status != StatusSolicitacao.APROVADA) {
            throw new IllegalStateException("Apenas solicitações em APROVADA podem ser confirmadas");
        }
        this.status = StatusSolicitacao.CONFIRMADO;
        this.resultadoSaga = ResultadoSaga.SUCESSO;
        this.dataAtualizacao = LocalDateTime.now();
    }

    /** Estados dos quais a saga não sai mais. */
    public boolean isTerminal() {
        return status == StatusSolicitacao.CONFIRMADO
                || status == StatusSolicitacao.CANCELADA
                || status == StatusSolicitacao.REJEITADA;
    }

    /**
     * Enum dos possíveis status de uma solicitação.
     */
    public enum StatusSolicitacao {
        RASCUNHO, PENDENTE, APROVADA, REJEITADA, CONFIRMADO, CANCELADA
    }

    /**
     * Desfecho da instância de saga, mantido separado do status porque
     * "cancelada pela cadeia de compensação" e "abortada sem compensação"
     * levam ao mesmo status mas são resultados experimentais diferentes.
     */
    public enum ResultadoSaga {
        /** Chegou a T7. */
        SUCESSO,
        /** Revertida pela cadeia backward. */
        COMPENSADA,
        /** Encerrada com tentativas esgotadas, sem compensação (Forward puro). */
        ABORTADA,
        /** Rejeitada por regra de negócio antes do ponto de pivô. */
        REJEITADA
    }
}
