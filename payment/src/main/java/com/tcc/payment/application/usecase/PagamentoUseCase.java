package com.tcc.payment.application.usecase;

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
        Pagamento pagamento = Pagamento.builder()
                .tipo(tipo)
                .referencia(request.getReferencia())
                .valor(request.getValor())
                .status(Pagamento.StatusPagamento.PENDENTE)
                .dataCriacao(LocalDateTime.now())
                .build();

        pagamento.validar();

        // Processa o pagamento e confirma a reserva (T5/T6 são executadas em um único passo)
        pagamento.confirmar();

        return pagamentoRepository.salvar(pagamento);
    }
}
