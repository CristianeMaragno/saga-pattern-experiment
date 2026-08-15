package com.tcc.audit;

import io.eventuate.tram.spring.inmemory.TramInMemoryConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/**
 * Mensageria em memória para os testes, no lugar do Kafka + outbox JDBC.
 */
@Configuration
@Profile("test")
@Import(TramInMemoryConfiguration.class)
public class TestMessagingConfiguration {
}
