package com.tcc.approval.domain.exception;

/**
 * Exceção base para erros de negócio.
 * Representa violações de regras de negócio que não são necessariamente
 * erros de programação, mas situações esperadas que devem ser tratadas.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
