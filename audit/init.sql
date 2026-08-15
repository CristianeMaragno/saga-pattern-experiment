-- Script de inicialização do banco de dados PostgreSQL para Audit API
--
-- Tabela única com a linha do tempo de todas as sagas de todos os serviços.
-- Centralizar aqui evita ter que fazer join entre os quatro bancos separados na
-- hora de analisar os resultados — que é o que tornaria a análise inviável.

CREATE TABLE IF NOT EXISTS saga_auditoria (
    id BIGSERIAL PRIMARY KEY,
    -- Identifica a rodada da matriz de experimentos (uma combinação de
    -- estratégia × cenário × posição × carga).
    run_id VARCHAR(100) NOT NULL,
    solicitacao_id BIGINT NOT NULL,
    servico VARCHAR(50) NOT NULL,
    etapa VARCHAR(10),
    acao VARCHAR(30) NOT NULL,
    tentativa INT NOT NULL DEFAULT 1,
    estrategia VARCHAR(20),
    cenario_falha VARCHAR(20),
    posicao_falha VARCHAR(10),
    mensagem TEXT,
    -- Momento em que a transição ocorreu no serviço de origem (epoch millis).
    -- É este o campo usado nos cálculos de tempo, não a hora de gravação aqui,
    -- que inclui a latência do Kafka e do CDC.
    timestamp_millis BIGINT NOT NULL,
    registrado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS saga_auditoria_run_idx ON saga_auditoria (run_id);
CREATE INDEX IF NOT EXISTS saga_auditoria_run_saga_idx ON saga_auditoria (run_id, solicitacao_id);
CREATE INDEX IF NOT EXISTS saga_auditoria_run_acao_idx ON saga_auditoria (run_id, acao);

-- ---------------------------------------------------------------------------
-- Eventuate Tram: necessário para a deduplicação no consumo (received_messages).
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
