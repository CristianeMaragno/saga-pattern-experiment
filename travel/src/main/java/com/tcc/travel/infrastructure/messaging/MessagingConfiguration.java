package com.tcc.travel.infrastructure.messaging;

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

    /**
     * O identificador do dispatcher vira o consumer group no Kafka e o
     * {@code consumer_id} na tabela de deduplicação: mudá-lo faz o serviço
     * reprocessar eventos antigos.
     */
    @Bean
    public DomainEventDispatcher travelEventDispatcher(SolicitacaoEventHandlers handlers,
                                                       DomainEventDispatcherFactory factory) {
        return factory.make("travelEventDispatcher", handlers.domainEventHandlers());
    }

    /**
     * Transporte de produção: outbox JDBC no banco do próprio serviço, com o
     * Eventuate CDC lendo a tabela {@code eventuate.message} e entregando ao
     * Kafka. Os testes de integração substituem isto por mensageria em memória,
     * por isso o transporte fica separado do resto da configuração.
     */
    @Configuration
    @Profile("!test")
    @Import(TramJdbcKafkaConfiguration.class)
    static class TransporteKafka {
    }
}
