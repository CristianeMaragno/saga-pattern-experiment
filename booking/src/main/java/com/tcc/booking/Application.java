package com.tcc.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

import lombok.extern.slf4j.Slf4j;

/**
 * Classe principal da aplicação Booking API.
 */
@Slf4j
@SpringBootApplication(scanBasePackages = "com.tcc.booking")
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
        log.info("========================================");
    }

}
