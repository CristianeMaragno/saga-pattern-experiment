-- Script de inicialização do banco de dados PostgreSQL para Approval API

-- Criação da tabela de aprovações
CREATE TABLE IF NOT EXISTS aprovacoes (
    id BIGSERIAL PRIMARY KEY,
    solicitante_id BIGINT NOT NULL,
    responsavel_id BIGINT NOT NULL,
    tempo_limite TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE', 'APROVADA', 'REJEITADA', 'CANCELADA')),
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP
);

-- Dados de exemplo para testes
INSERT INTO aprovacoes (solicitante_id, responsavel_id, tempo_limite, status) VALUES
    (1, 101, CURRENT_TIMESTAMP + INTERVAL '2 days', 'PENDENTE'),
    (1, 102, CURRENT_TIMESTAMP + INTERVAL '5 days', 'APROVADA'),
    (2, 101, CURRENT_TIMESTAMP + INTERVAL '1 day', 'PENDENTE'),
    (2, 103, CURRENT_TIMESTAMP - INTERVAL '1 day', 'REJEITADA'),
    (3, 102, CURRENT_TIMESTAMP + INTERVAL '3 days', 'PENDENTE')
ON CONFLICT DO NOTHING;
