-- Script de inicialização do banco de dados PostgreSQL para Payment API
--
-- Sem dados de exemplo: cada rodada do experimento começa de um estado limpo.

-- ---------------------------------------------------------------------------
-- Domínio: pagamentos (T5 voo, T6 hotel) — primeiras etapas depois do pivô
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pagamentos (
    id BIGSERIAL PRIMARY KEY,
    solicitacao_id BIGINT NOT NULL,
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('VOO', 'HOTEL')),
    referencia VARCHAR(255) NOT NULL,
    valor NUMERIC(12,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE'
        CHECK (status IN ('PENDENTE', 'CONFIRMADO', 'FALHOU', 'CANCELADO')),
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP,
    -- Um pagamento de cada tipo por saga: torna o consumo idempotente.
    CONSTRAINT pagamentos_saga_tipo_uk UNIQUE (solicitacao_id, tipo)
);

CREATE INDEX IF NOT EXISTS pagamentos_solicitacao_idx ON pagamentos (solicitacao_id);
-- ---------------------------------------------------------------------------
-- Eventuate Tram: outbox transacional + deduplicação de consumo
-- DDL oficial de eventuate-common (postgres/2.initialize-database.sql)
-- ---------------------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS eventuate;

CREATE TABLE IF NOT EXISTS eventuate.message (
    id VARCHAR(1000) PRIMARY KEY,
    destination TEXT NOT NULL,
    headers TEXT NOT NULL,
    payload TEXT NOT NULL,
    published SMALLINT DEFAULT 0,
    message_partition SMALLINT,
    creation_time BIGINT
);

CREATE INDEX IF NOT EXISTS message_published_idx ON eventuate.message (published, id);

CREATE TABLE IF NOT EXISTS eventuate.received_messages (
    consumer_id VARCHAR(1000),
    message_id VARCHAR(1000),
    creation_time BIGINT,
    published SMALLINT DEFAULT 0,
    PRIMARY KEY (consumer_id, message_id)
);

CREATE TABLE IF NOT EXISTS eventuate.offset_store (
    client_name VARCHAR(255) NOT NULL PRIMARY KEY,
    serialized_offset VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS eventuate.cdc_monitoring (
    reader_id VARCHAR(1000) PRIMARY KEY,
    last_time BIGINT
);
-- ---------------------------------------------------------------------------
-- Motor de recuperação: etapas aguardando execução ou nova tentativa.
-- As colunas de instante são TIMESTAMPTZ porque a entidade JPA usa
-- java.time.Instant, que o Hibernate 6 mapeia para "timestamp with time zone".
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS etapa_pendente (
    id BIGSERIAL PRIMARY KEY,
    solicitacao_id BIGINT NOT NULL,
    etapa VARCHAR(10) NOT NULL,
    tentativa INT NOT NULL DEFAULT 0,
    proxima_tentativa_em TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE'
        CHECK (status IN ('PENDENTE', 'CONCLUIDA', 'ESGOTADA')),
    payload TEXT,
    ultimo_erro TEXT,
    data_criacao TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMPTZ,
    CONSTRAINT etapa_pendente_saga_etapa_uk UNIQUE (solicitacao_id, etapa)
);

CREATE INDEX IF NOT EXISTS etapa_pendente_devidas_idx ON etapa_pendente (status, proxima_tentativa_em);
