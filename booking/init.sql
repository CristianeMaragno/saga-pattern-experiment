-- Script de inicialização do banco de dados PostgreSQL para Booking API

-- Criação da tabela de holds temporários de reserva (T3: voo, T4: hotel)
CREATE TABLE IF NOT EXISTS holds (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(20) NOT NULL CHECK (type IN ('FLIGHT', 'HOTEL')),
    reference VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);

-- Dados de exemplo para testes
INSERT INTO holds (type, reference, created_at, expires_at) VALUES
    ('FLIGHT', 'solicitacao:1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '24 hours'),
    ('FLIGHT', 'solicitacao:2', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '72 hours'),
    ('HOTEL', 'solicitacao:1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '48 hours')
ON CONFLICT DO NOTHING;
