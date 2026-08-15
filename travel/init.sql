-- Script de inicialização do banco de dados PostgreSQL para Travel API
--
-- Sem dados de exemplo: cada rodada do experimento precisa começar de um estado
-- limpo, senão as linhas de fixture entram nas agregações da análise.

-- ---------------------------------------------------------------------------
-- Domínio: solicitações de viagem (T1 e T7)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS solicitacoes (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    destino VARCHAR(255) NOT NULL,
    data_ida DATE NOT NULL,
    data_volta DATE,
    motivo TEXT NOT NULL,
    -- RASCUNHO e CONFIRMADO fazem parte do enum StatusSolicitacao desde o
    -- início, mas faltavam neste CHECK: T1 cria em RASCUNHO e T7 confirma.
    status VARCHAR(20) NOT NULL DEFAULT 'RASCUNHO'
        CHECK (status IN ('RASCUNHO', 'PENDENTE', 'APROVADA', 'REJEITADA', 'CONFIRMADO', 'CANCELADA')),
    -- Desfecho da saga, separado do status para distinguir "cancelada pela
    -- cadeia de compensação" de "abortada sem compensação" (Forward puro).
    resultado_saga VARCHAR(20)
        CHECK (resultado_saga IN ('SUCESSO', 'COMPENSADA', 'ABORTADA', 'REJEITADA')),
    etapa_falha VARCHAR(10),
    valor_voo NUMERIC(12,2),
    valor_hotel NUMERIC(12,2),
    -- Ponto de junção dos dois ramos paralelos: T7 só dispara com os dois true.
    pagamento_voo_confirmado BOOLEAN NOT NULL DEFAULT FALSE,
    pagamento_hotel_confirmado BOOLEAN NOT NULL DEFAULT FALSE,
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS solicitacoes_status_idx ON solicitacoes (status);

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
