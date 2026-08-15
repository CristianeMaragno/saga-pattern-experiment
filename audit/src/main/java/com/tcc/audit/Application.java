package com.tcc.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

/**
 * Classe principal do serviço de auditoria da saga.
 *
 * Consome o canal único {@code saga-audit}, alimentado por todos os demais
 * serviços, e grava tudo em uma tabela central. É dela que saem as métricas do
 * capítulo de resultados — o Prometheus/Grafana serve para observar a rodada ao
 * vivo, não para produzir os números da monografia.
 */
@Slf4j
@SpringBootApplication(scanBasePackages = {"com.tcc.audit", "com.tcc.saga"})
public class Application {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Application.class);
        Environment env = app.run(args).getEnvironment();

        log.info("========================================");
        log.info("Aplicação iniciada com sucesso!");
        log.info("Nome da aplicação: {}", env.getProperty("spring.application.name"));
        log.info("Porta: {}", env.getProperty("server.port"));
        log.info("========================================");
    }
}
