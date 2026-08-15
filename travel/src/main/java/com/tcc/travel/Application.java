package com.tcc.travel;

import com.tcc.saga.experimento.ExperimentoProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;

/**
 * Classe principal da aplicação Travel API.
 * Responsável por inicializar a aplicação Spring Boot.
 *
 * O pacote com.tcc.saga entra no component scan porque carrega o contrato de
 * eventos e os componentes do experimento compartilhados entre os serviços
 * (ver scripts/sync-shared.sh).
 */
@Slf4j
@SpringBootApplication(scanBasePackages = {"com.tcc.travel", "com.tcc.saga"})
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
