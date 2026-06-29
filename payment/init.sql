-- Script de inicialização do banco de dados PostgreSQL para Payment API

-- Criação da tabela de pagamentos (T5: voo, T6: hotel)
CREATE TABLE IF NOT EXISTS pagamentos (
    id BIGSERIAL PRIMARY KEY,
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('VOO', 'HOTEL')),
    referencia VARCHAR(255) NOT NULL,
    valor NUMERIC(12,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE', 'CONFIRMADO', 'FALHOU', 'CANCELADO')),
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP
);

-- Dados de exemplo para testes
INSERT INTO pagamentos (tipo, referencia, valor, status, data_atualizacao) VALUES
    ('VOO', 'solicitacao:1', 1850.00, 'CONFIRMADO', CURRENT_TIMESTAMP),
    ('HOTEL', 'solicitacao:1', 920.50, 'CONFIRMADO', CURRENT_TIMESTAMP),
    ('VOO', 'solicitacao:2', 3200.00, 'CONFIRMADO', CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;
