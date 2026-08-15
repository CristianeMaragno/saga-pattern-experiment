package com.tcc.approval;

import io.eventuate.tram.spring.inmemory.TramInMemoryConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/**
 * Substitui o transporte Kafka + outbox JDBC por mensageria em memória durante
 * os testes.
 *
 * Isso mantém a coreografia real (mesmos eventos, mesmos handlers, mesma
 * serialização do Eventuate Tram) sem exigir Kafka, Postgres nem Docker para
 * rodar {@code mvn test}.
 */
@Configuration
@Profile("test")
@Import(TramInMemoryConfiguration.class)
public class TestMessagingConfiguration {
}
