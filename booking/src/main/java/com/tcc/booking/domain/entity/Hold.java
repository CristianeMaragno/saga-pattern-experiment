package com.tcc.booking.domain.entity;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidade de domínio que representa um hold temporário de reserva
 * (voo ou hotel). Contém apenas lógica e atributos de negócio,
 * sem nenhuma dependência de Spring ou frameworks de persistência.
 *
 * <p>O ciclo de vida importa para o experimento: T3/T4 criam holds
 * temporários, cuja compensação não tem custo, e a restrição temporal
 * {@code t5 - t3 < Δt_voo} exige distinguir um hold ainda ATIVO de um
 * já EXPIRADO.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Hold {

    private Long id;
    /** Identificador de correlação da instância de saga. */
    private Long solicitacaoId;
    private HoldType type;
    private String reference;
    private HoldStatus status;
    private Instant createdAt;
    private Instant expiresAt;

    /**
     * Confirma o hold depois do pagamento correspondente (T5 para voo, T6 para hotel).
     */
    public void confirmar() {
        if (this.status != HoldStatus.ATIVO) {
            throw new IllegalStateException("Apenas holds ativos podem ser confirmados");
        }
        this.status = HoldStatus.CONFIRMADO;
    }

    /**
     * Compensação de T3/T4: libera o hold.
     * Idempotente, porque o evento que dispara a compensação pode ser reentregue.
     *
     * @return {@code true} se este chamado de fato liberou o hold
     */
    public boolean liberar() {
        if (this.status == HoldStatus.LIBERADO) {
            return false;
        }
        this.status = HoldStatus.LIBERADO;
        return true;
    }

    /**
     * Marca o hold como expirado: o pagamento não ocorreu dentro do prazo
     * (violação da restrição temporal da saga).
     */
    public void expirar() {
        if (this.status == HoldStatus.ATIVO) {
            this.status = HoldStatus.EXPIRADO;
        }
    }

    /** O prazo do hold já passou sem confirmação? */
    public boolean estaExpirado(Instant agora) {
        return status == HoldStatus.ATIVO && expiresAt != null && expiresAt.isBefore(agora);
    }

    /**
     * Tipos de hold suportados.
     */
    public enum HoldType {
        FLIGHT, HOTEL
    }

    /**
     * Ciclo de vida do hold.
     */
    public enum HoldStatus {
        /** Criado e dentro do prazo, aguardando o pagamento. */
        ATIVO,
        /** Pagamento concluído. */
        CONFIRMADO,
        /** Desfeito pela cadeia de compensação. */
        LIBERADO,
        /** Prazo esgotado sem pagamento. */
        EXPIRADO
    }
}
