-- Script de inicialização do banco de dados PostgreSQL para Travel API

-- Criação da tabela de solicitações de viagem
CREATE TABLE IF NOT EXISTS solicitacoes (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    destino VARCHAR(255) NOT NULL,
    data_ida DATE NOT NULL,
    data_volta DATE,
    motivo TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE', 'APROVADA', 'REJEITADA', 'CANCELADA')),
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Dados de exemplo para testes
INSERT INTO solicitacoes (usuario_id, destino, data_ida, data_volta, motivo, status) VALUES
    (1, 'São Paulo', CURRENT_DATE + INTERVAL '10 days', CURRENT_DATE + INTERVAL '15 days', 'Reunião com cliente', 'PENDENTE'),
    (1, 'Rio de Janeiro', CURRENT_DATE + INTERVAL '20 days', CURRENT_DATE + INTERVAL '25 days', 'Conferência', 'APROVADA'),
    (2, 'Belo Horizonte', CURRENT_DATE + INTERVAL '5 days', CURRENT_DATE + INTERVAL '8 days', 'Treinamento', 'PENDENTE')
ON CONFLICT DO NOTHING;
