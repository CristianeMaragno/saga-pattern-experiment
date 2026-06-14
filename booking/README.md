# Compilar
mvn clean install

# Rodar a aplicação
mvn spring-boot:run

# Info 
Roda na porta 8080

## 📝 Criar uma Solicitação

```bash
curl -X POST http://localhost:8080/api/v1/solicitacoes \
  -H "Content-Type: application/json" \
  -d '{
    "usuarioId": 999,
    "destino": "Salvador",
    "dataIda": "2026-05-01",
    "dataVolta": "2026-05-05",
    "motivo": "Férias bem merecidas"
  }'
```