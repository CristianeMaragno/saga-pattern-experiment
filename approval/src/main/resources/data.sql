-- Script de inicialização com dados de exemplo para desenvolvimento

-- Inserir aprovações de exemplo
INSERT INTO aprovacoes (solicitante_id, responsavel_id, tempo_limite, status, data_criacao, data_atualizacao) 
VALUES 
  (1, 101, '2026-04-15 14:00:00', 'PENDENTE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (1, 102, '2026-05-10 10:30:00', 'APROVADA', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (2, 101, '2026-03-20 16:00:00', 'PENDENTE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (2, 103, '2026-06-01 09:00:00', 'REJEITADA', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (3, 102, '2026-07-05 13:30:00', 'PENDENTE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
