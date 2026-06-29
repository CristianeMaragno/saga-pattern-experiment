package com.tcc.booking.interfaces.exception;

import com.tcc.booking.domain.exception.BusinessException;
import com.tcc.booking.domain.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Manipulador global de exceções para a aplicação.
 * Centraliza o tratamento de erros e garante respostas consistentes.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Trata exceções de recurso não encontrado.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex,
            WebRequest request) {
        log.warn("Recurso não encontrado: {}", ex.getMessage());

        ApiErrorResponse errorResponse = ApiErrorResponse.builder()
                .status(HttpStatus.NOT_FOUND.value())
                .message(ex.getMessage())
                .error("Not Found")
                .timestamp(LocalDateTime.now())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Trata exceções de negócio genéricas.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(
            BusinessException ex,
            WebRequest request) {
        log.warn("Erro de negócio: {}", ex.getMessage());

        ApiErrorResponse errorResponse = ApiErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(ex.getMessage())
                .error("Business Error")
                .timestamp(LocalDateTime.now())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Trata erros de validação do Spring Validation.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            WebRequest request) {
        log.warn("Erro de validação: {}", ex.getMessage());

        List<ApiErrorResponse.FieldError> fieldErrors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.add(ApiErrorResponse.FieldError.builder()
                        .field(error.getField())
                        .message(error.getDefaultMessage())
                        .build())
        );

        ApiErrorResponse errorResponse = ApiErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message("Erro de validação")
                .error("Validation Error")
                .timestamp(LocalDateTime.now())
                .path(request.getDescription(false).replace("uri=", ""))
                .fieldErrors(fieldErrors)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Trata chamadas com método HTTP não suportado pelo endpoint.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            WebRequest request) {
        log.warn("Método HTTP não suportado: {}", ex.getMessage());

        ApiErrorResponse errorResponse = ApiErrorResponse.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED.value())
                .message(ex.getMessage())
                .error("Method Not Allowed")
                .timestamp(LocalDateTime.now())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * Trata exceções genéricas não capturadas.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception ex,
            WebRequest request) {
        log.error("Erro inesperado", ex);

        ApiErrorResponse errorResponse = ApiErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("Erro interno do servidor")
                .error("Internal Server Error")
                .timestamp(LocalDateTime.now())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
