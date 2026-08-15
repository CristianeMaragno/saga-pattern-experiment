package com.tcc.saga.recuperacao;

import com.tcc.saga.event.EtapaSaga;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Transação local pendente de execução ou de nova tentativa.
 *
 * Toda etapa capaz de falhar (T2, T4, T6) passa por esta tabela, inclusive na
 * primeira execução. Isso dá um único caminho de código para "executar" e
 * "tentar de novo", torna cada tentativa observável na auditoria e permite
 * backoff sem bloquear a thread do consumidor Kafka — que é o que aconteceria
 * com um retry síncrono dentro do handler.
 */
@Entity
@Table(name = "etapa_pendente")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EtapaPendenteJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "solicitacao_id", nullable = false)
    private Long solicitacaoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "etapa", nullable = false, length = 10)
    private EtapaSaga etapa;

    /** Execuções já realizadas; a próxima execução é {@code tentativa + 1}. */
    @Column(name = "tentativa", nullable = false)
    private int tentativa;

    @Column(name = "proxima_tentativa_em", nullable = false)
    private Instant proximaTentativaEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusEtapa status;

    /** JSON do evento que originou a etapa, para reexecutá-la sem consultar outro serviço. */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "ultimo_erro", columnDefinition = "TEXT")
    private String ultimoErro;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private Instant dataCriacao;

    @Column(name = "data_atualizacao")
    private Instant dataAtualizacao;

    /**
     * Estados possíveis de uma etapa pendente.
     */
    public enum StatusEtapa {
        /** Aguardando execução ou nova tentativa. */
        PENDENTE,
        /** Executada com sucesso. */
        CONCLUIDA,
        /** Falhou em definitivo (permanente, ou tentativas esgotadas). */
        ESGOTADA
    }
}
