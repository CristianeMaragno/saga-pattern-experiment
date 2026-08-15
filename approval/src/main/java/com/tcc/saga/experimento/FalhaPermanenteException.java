package com.tcc.saga.experimento;

/**
 * Falha não recuperável (recusa de pagamento, violação de regra de negócio).
 * Retry não ajuda: qualquer estratégia encerra a saga imediatamente.
 */
public class FalhaPermanenteException extends RuntimeException {

    public FalhaPermanenteException(String message) {
        super(message);
    }
}
