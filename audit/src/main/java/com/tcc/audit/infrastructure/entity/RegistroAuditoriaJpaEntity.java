package com.tcc.audit.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Uma transição da saga, como reportada pelo serviço que a executou.
 */
@Entity
@Table(name = "saga_auditoria")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistroAuditoriaJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 100)
    private String runId;

    @Column(name = "solicitacao_id", nullable = false)
    private Long solicitacaoId;

    @Column(name = "servico", nullable = false, length = 50)
    private String servico;

    @Column(name = "etapa", length = 10)
    private String etapa;

    @Column(name = "acao", nullable = false, length = 30)
    private String acao;

    @Column(name = "tentativa", nullable = false)
    private int tentativa;

    @Column(name = "estrategia", length = 20)
    private String estrategia;

    @Column(name = "cenario_falha", length = 20)
    private String cenarioFalha;

    @Column(name = "posicao_falha", length = 10)
    private String posicaoFalha;

    @Column(name = "mensagem", columnDefinition = "TEXT")
    private String mensagem;

    @Column(name = "timestamp_millis", nullable = false)
    private long timestampMillis;

    @Column(name = "registrado_em", nullable = false)
    private Instant registradoEm;
}
