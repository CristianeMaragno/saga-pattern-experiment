# Travel API

API para gerenciamento de solicitações de viagem, construída com Java 21 + Spring Boot 3, persistindo em PostgreSQL.

## 🐳 Rodando com Docker (recomendado)

Sobe a aplicação e o banco PostgreSQL com um único comando:

```bash
docker compose up
```

Isso inicia:
- **postgres** — banco de dados PostgreSQL na porta `5432` (dados de exemplo carregados via `init.sql`)
- **app** — API Spring Boot na porta `8080`, conectada ao banco
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

Requer um PostgreSQL acessível em `localhost:5432` (pode ser o do `docker compose up postgres`) com banco `travel_db`, usuário `travel_user` e senha `travel_password`.

```bash
# Compilar
mvn clean install

# Rodar a aplicação
mvn spring-boot:run
```

A aplicação roda na porta `8080`.

As credenciais e o host do banco podem ser sobrescritos pelas variáveis de ambiente `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` e `DB_PASSWORD`.

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
