-- Script de inicialização com dados de exemplo para desenvolvimento

-- Inserir solicitações de viagem de exemplo
INSERT INTO solicitacoes (usuario_id, destino, data_ida, data_volta, motivo, status, data_criacao, data_atualizacao) 
VALUES 
  (1, 'Paris, França', '2026-04-15', '2026-04-25', 'Conferência de Negócios', 'PENDENTE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (1, 'Tóquio, Japão', '2026-05-10', '2026-05-20', 'Reunião com clientes internacionais', 'APROVADA', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (2, 'Nova York, EUA', '2026-03-20', '2026-03-30', 'Treinamento corporativo', 'PENDENTE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (2, 'Barcelona, Espanha', '2026-06-01', '2026-06-10', 'Evento de tecnologia', 'REJEITADA', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (3, 'Sydney, Austrália', '2026-07-05', '2026-07-15', 'Visita técnica', 'PENDENTE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
