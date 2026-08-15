package com.tcc.saga.recuperacao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Serializa o evento que originou uma etapa pendente.
 *
 * A etapa guarda o próprio evento para poder ser reexecutada muitas vezes sem
 * consultar nenhum outro serviço — uma chamada síncrona no meio da recuperação
 * reintroduziria justamente o acoplamento que a coreografia evita.
 */
@Component
@RequiredArgsConstructor
public class PayloadEtapaMapper {

    private final ObjectMapper objectMapper;

    public String serializar(Object evento) {
        try {
            return objectMapper.writeValueAsString(evento);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar o payload da etapa", e);
        }
    }

    public <T> T desserializar(String json, Class<T> tipo) {
        try {
            return objectMapper.readValue(json, tipo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao desserializar o payload da etapa", e);
        }
    }
}
