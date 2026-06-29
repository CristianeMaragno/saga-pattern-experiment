# Booking API

API para criação de holds temporários de reserva (voo e hotel), construída com Java 21 + Spring Boot 3, persistindo em PostgreSQL.

## 🐳 Rodando com Docker (recomendado)

Sobe a aplicação e o banco PostgreSQL com um único comando:

```bash
docker compose up
```

Isso inicia:
- **postgres** — banco de dados PostgreSQL na porta `5432` (dados de exemplo carregados via `init.sql`)
- **app** — API Spring Boot na porta `8082`, conectada ao banco
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

Requer um PostgreSQL acessível em `localhost:5432` (pode ser o do `docker compose up postgres`) com banco `booking_db`, usuário `booking_user` e senha `booking_password`.

```bash
# Compilar
mvn clean install

# Rodar a aplicação
mvn spring-boot:run
```

A aplicação roda na porta `8082`.

As credenciais e o host do banco podem ser sobrescritos pelas variáveis de ambiente `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` e `DB_PASSWORD`.

## ✈️ Criar um hold de voo (T3)

Hold válido por um período entre 24h e 72h. Se `durationHours` não for informado, assume o mínimo de 24h.

```bash
curl -X POST http://localhost:8082/api/v1/holds/flight \
  -H "Content-Type: application/json" \
  -d '{
    "reference": "solicitacao:1",
    "durationHours": 48
  }'
```

## 🏨 Criar um hold de hotel (T4)

Hold com duração padrão de 48h, caso `durationHours` não seja informado.

```bash
curl -X POST http://localhost:8082/api/v1/holds/hotel \
  -H "Content-Type: application/json" \
  -d '{
    "reference": "solicitacao:1"
  }'
```

## 📋 Outras operações

```bash
# Obter um hold pelo ID
curl http://localhost:8082/api/v1/holds/1
```
