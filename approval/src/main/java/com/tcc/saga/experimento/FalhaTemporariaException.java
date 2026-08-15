package com.tcc.saga.experimento;

/**
 * Falha classificada como recuperável (indisponibilidade momentânea, timeout,
 * atraso). É o que os modos Forward e Híbrido tentam recuperar por retry.
 */
public class FalhaTemporariaException extends RuntimeException {

    public FalhaTemporariaException(String message) {
        super(message);
    }
}
