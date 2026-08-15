-- Infraestrutura comum a todos os serviços da saga.
--
-- Este arquivo é concatenado no init.sql de cada serviço (ver
-- scripts/sync-shared.sh não faz isso: as tabelas abaixo já estão copiadas
-- dentro de cada <servico>/init.sql). Mantido aqui como fonte única de
-- referência do schema.
--
-- 1) Tabelas do Eventuate Tram (outbox transacional + deduplicação de consumo).
--    DDL oficial do projeto eventuate-common (postgres/2.initialize-database.sql).
-- 2) Tabela de etapas pendentes, usada pelo motor de recuperação.

CREATE SCHEMA IF NOT EXISTS eventuate;

-- Outbox: o Eventuate Tram grava aqui, na MESMA transação local do negócio, e o
-- Eventuate CDC lê esta tabela e publica no Kafka. É o que garante que "estado
-- persistido" e "evento publicado" não divirjam.
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

-- Deduplicação no consumo: garante idempotência quando o Kafka reentrega.
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

-- Etapas da saga aguardando execução ou nova tentativa (motor de recuperação).
-- Só existe nos serviços que podem sofrer falha injetada: approval, booking e payment.
CREATE TABLE IF NOT EXISTS etapa_pendente (
    id BIGSERIAL PRIMARY KEY,
    solicitacao_id BIGINT NOT NULL,
    etapa VARCHAR(10) NOT NULL,
    tentativa INT NOT NULL DEFAULT 0,
    proxima_tentativa_em TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE', 'CONCLUIDA', 'ESGOTADA')),
    payload TEXT,
    ultimo_erro TEXT,
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP,
    CONSTRAINT etapa_pendente_saga_etapa_uk UNIQUE (solicitacao_id, etapa)
);

CREATE INDEX IF NOT EXISTS etapa_pendente_devidas_idx
    ON etapa_pendente (status, proxima_tentativa_em);
