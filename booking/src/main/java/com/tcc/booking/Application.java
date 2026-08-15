package com.tcc.booking;

import com.tcc.saga.experimento.ExperimentoProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Classe principal da aplicação booking-api.
 *
 * O pacote com.tcc.saga entra no component scan, no entity scan e no scan de
 * repositórios porque carrega o contrato de eventos e o motor de recuperação
 * compartilhados entre os serviços (ver scripts/sync-shared.sh). O agendamento
 * é habilitado porque o motor de recuperação executa as etapas por polling.
 */
@Slf4j
@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.tcc.booking", "com.tcc.saga"})
@EntityScan(basePackages = {"com.tcc.booking", "com.tcc.saga"})
@EnableJpaRepositories(basePackages = {"com.tcc.booking", "com.tcc.saga"})
@EnableConfigurationProperties(ExperimentoProperties.class)
public class Application {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Application.class);
        Environment env = app.run(args).getEnvironment();

        log.info("========================================");
        log.info("Aplicação iniciada com sucesso!");
        log.info("========================================");
        log.info("Nome da aplicação: {}", env.getProperty("spring.application.name"));
        log.info("Porta: {}", env.getProperty("server.port"));
        log.info("Perfil ativo: {}", String.join(", ", env.getActiveProfiles()));
        log.info("Estratégia de recuperação: {}", env.getProperty("experimento.estrategia"));
        log.info("Cenário de falha: {} em {}", env.getProperty("experimento.cenario-falha"),
                env.getProperty("experimento.posicao-falha"));
        log.info("========================================");
    }

}
