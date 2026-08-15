package com.tcc.payment.application.usecase;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tcc.payment.application.dto.CreatePagamentoRequestDTO;
import com.tcc.payment.domain.entity.Pagamento;
import com.tcc.payment.domain.exception.ResourceNotFoundException;
import com.tcc.payment.domain.repository.PagamentoRepository;

import lombok.RequiredArgsConstructor;

/**
 * Caso de uso para operações com Pagamentos.
 *
 * Regras de negócio:
 * - T5 (voo): confirma a reserva e processa o pagamento do voo.
 * - T6 (hotel): confirma a reserva e processa o pagamento do hotel.
 */
@Service
@RequiredArgsConstructor
public class PagamentoUseCase {

    private final PagamentoRepository pagamentoRepository;

    /**
     * Processa o pagamento do voo (T5): confirma a reserva e processa o pagamento.
     */
    public Pagamento processarPagamentoVoo(CreatePagamentoRequestDTO request) {
        return processarPagamento(Pagamento.TipoPagamento.VOO, request);
    }

    /**
     * Processa o pagamento do hotel (T6): confirma a reserva e processa o pagamento.
     */
    public Pagamento processarPagamentoHotel(CreatePagamentoRequestDTO request) {
        return processarPagamento(Pagamento.TipoPagamento.HOTEL, request);
    }

    /**
     * Obtém um pagamento por ID.
     */
    public Pagamento obterPorId(Long id) {
        return pagamentoRepository.obterPorId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento", id));
    }

    /**
     * Lista todos os pagamentos.
     */
    public List<Pagamento> listarTodos() {
        return pagamentoRepository.obterTodos();
    }

    private Pagamento processarPagamento(Pagamento.TipoPagamento tipo, CreatePagamentoRequestDTO request) {
        return criarEConfirmar(request.getSolicitacaoId(), tipo, request.getReferencia(), request.getValor());
    }

    /**
     * Cria o pagamento já confirmado: no modelo da saga, T5 e T6 confirmam a
     * reserva e processam o pagamento em um único passo.
     *
     * <p>Fica aqui, e não no caso de uso da saga, porque tanto o handler de T5
     * quanto o executor de T6 precisam dela — e o executor não pode depender do
     * caso de uso da saga sem fechar um ciclo com o motor de recuperação.
     */
    public Pagamento criarEConfirmar(Long solicitacaoId, Pagamento.TipoPagamento tipo,
                                     String referencia, BigDecimal valor) {
        Pagamento pagamento = novoPagamento(solicitacaoId, tipo, referencia, valor);
        pagamento.confirmar();
        return pagamentoRepository.salvar(pagamento);
    }

    /**
     * Registra um pagamento recusado (status FALHOU), usado quando a falha em
     * T6 é permanente: a recusa precisa aparecer no banco, não só na auditoria.
     */
    public Pagamento registrarFalha(Long solicitacaoId, Pagamento.TipoPagamento tipo,
                                    String referencia, BigDecimal valor) {
        Pagamento pagamento = novoPagamento(solicitacaoId, tipo, referencia, valor);
        pagamento.falhar();
        return pagamentoRepository.salvar(pagamento);
    }

    private Pagamento novoPagamento(Long solicitacaoId, Pagamento.TipoPagamento tipo,
                                    String referencia, BigDecimal valor) {
        Pagamento pagamento = Pagamento.builder()
                .solicitacaoId(solicitacaoId)
                .tipo(tipo)
                .referencia(referencia)
                .valor(valor)
                .status(Pagamento.StatusPagamento.PENDENTE)
                .dataCriacao(LocalDateTime.now())
                .build();

        pagamento.validar();
        return pagamento;
    }
}
