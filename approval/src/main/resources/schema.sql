-- Script para criar a tabela de aprovações

CREATE TABLE IF NOT EXISTS aprovacoes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    solicitante_id BIGINT NOT NULL,
    responsavel_id BIGINT NOT NULL,
    tempo_limite TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP,
    CONSTRAINT ck_status CHECK (status IN ('PENDENTE', 'APROVADA', 'REJEITADA', 'CANCELADA'))
);
