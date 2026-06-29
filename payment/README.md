# Payment API

API para confirmação de reserva e processamento de pagamentos (voo e hotel), construída com Java 21 + Spring Boot 3, persistindo em PostgreSQL.

## 🐳 Rodando com Docker (recomendado)

Sobe a aplicação e o banco PostgreSQL com um único comando:

```bash
docker compose up
```

Isso inicia:
- **postgres** — banco de dados PostgreSQL na porta `5432` (dados de exemplo carregados via `init.sql`)
- **app** — API Spring Boot na porta `8083`, conectada ao banco
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

Requer um PostgreSQL acessível em `localhost:5432` (pode ser o do `docker compose up postgres`) com banco `payment_db`, usuário `payment_user` e senha `payment_password`.

```bash
# Compilar
mvn clean install

# Rodar a aplicação
mvn spring-boot:run
```

A aplicação roda na porta `8083`.

As credenciais e o host do banco podem ser sobrescritos pelas variáveis de ambiente `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` e `DB_PASSWORD`.

## ✈️ Pagar voo (T5)

Confirma a reserva e processa o pagamento do voo.

```bash
curl -X POST http://localhost:8083/api/v1/pagamentos/voo \
  -H "Content-Type: application/json" \
  -d '{
    "referencia": "solicitacao:1",
    "valor": 1850.00
  }'
```

## 🏨 Pagar hotel (T6)

Confirma a reserva e processa o pagamento do hotel.

```bash
curl -X POST http://localhost:8083/api/v1/pagamentos/hotel \
  -H "Content-Type: application/json" \
  -d '{
    "referencia": "solicitacao:1",
    "valor": 920.50
  }'
```

## 📋 Outras operações

```bash
# Obter um pagamento pelo ID
curl http://localhost:8083/api/v1/pagamentos/1

# Listar todos os pagamentos
curl http://localhost:8083/api/v1/pagamentos
```
