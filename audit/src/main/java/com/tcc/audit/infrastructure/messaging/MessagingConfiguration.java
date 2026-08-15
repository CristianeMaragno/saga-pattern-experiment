package com.tcc.audit.infrastructure.messaging;

import io.eventuate.tram.events.subscriber.DomainEventDispatcher;
import io.eventuate.tram.events.subscriber.DomainEventDispatcherFactory;
import io.eventuate.tram.spring.events.publisher.TramEventsPublisherConfiguration;
import io.eventuate.tram.spring.events.subscriber.TramEventSubscriberConfiguration;
import io.eventuate.tram.spring.jdbckafka.TramJdbcKafkaConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/**
 * Liga o serviço à mensageria do Eventuate Tram.
 */
@Configuration
@Import({TramEventsPublisherConfiguration.class, TramEventSubscriberConfiguration.class})
public class MessagingConfiguration {

    @Bean
    public DomainEventDispatcher auditEventDispatcher(AuditoriaEventHandlers handlers,
                                                      DomainEventDispatcherFactory factory) {
        return factory.make("auditEventDispatcher", handlers.domainEventHandlers());
    }

    /**
     * Transporte de produção; nos testes é substituído por mensageria em memória.
     */
    @Configuration
    @Profile("!test")
    @Import(TramJdbcKafkaConfiguration.class)
    static class TransporteKafka {
    }
}
