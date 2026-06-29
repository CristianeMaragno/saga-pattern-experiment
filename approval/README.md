# Approval API

API para gerenciamento de aprovações, construída com Java 21 + Spring Boot 3, persistindo em PostgreSQL.

## 🐳 Rodando com Docker (recomendado)

Sobe a aplicação e o banco PostgreSQL com um único comando:

```bash
docker compose up
```

Isso inicia:
- **postgres** — banco de dados PostgreSQL na porta `5432` (dados de exemplo carregados via `init.sql`)
- **app** — API Spring Boot na porta `8081`, conectada ao banco
- **pgadmin** *(opcional)* — interface web para gerenciar o banco, em `http://localhost:5050` (login: `admin@example.com` / `admin`)

Para reconstruir a imagem da aplicação após alterações no código:

```bash
docker compose up --build
```

Para derrubar os containers:

```bash
docker compose down
```

Para derrubar e também remover os dados do banco:

```bash
docker compose down -v
```

## 💻 Rodando localmente (sem Docker)

Requer um PostgreSQL acessível em `localhost:5432` (pode ser o do `docker compose up postgres`) com banco `approval_db`, usuário `approval_user` e senha `approval_password`.

```bash
# Compilar
mvn clean install

# Rodar a aplicação
mvn spring-boot:run
```

A aplicação roda na porta `8081`.

As credenciais e o host do banco podem ser sobrescritos pelas variáveis de ambiente `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` e `DB_PASSWORD`.

## 📝 Criar uma Aprovação

```bash
curl -X POST http://localhost:8081/api/v1/aprovacoes \
  -H "Content-Type: application/json" \
  -d '{
    "solicitanteId": 1,
    "responsavelId": 101,
    "tempoLimite": "2026-07-10T18:00:00"
  }'
```

## 📋 Outras operações

```bash
# Listar todas as aprovações
curl http://localhost:8081/api/v1/aprovacoes

# Obter uma aprovação por ID
curl http://localhost:8081/api/v1/aprovacoes/1

# Aprovar
curl -X PUT http://localhost:8081/api/v1/aprovacoes/1/aprovar

# Rejeitar
curl -X PUT http://localhost:8081/api/v1/aprovacoes/1/rejeitar

# Cancelar
curl -X DELETE http://localhost:8081/api/v1/aprovacoes/1
```
