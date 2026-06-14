package com.tcc.approval.domain.exception;

/**
 * Exceção lançada quando um recurso não é encontrado no repositório.
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, Long id) {
        super(String.format("%s com ID %d não encontrado", resourceName, id));
    }
}
