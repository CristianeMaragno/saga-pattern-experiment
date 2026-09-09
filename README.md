# Recuperação de Falhas em Sagas Distribuídas — Experimento

Experimento empírico que compara três estratégias de recuperação de falhas
(**Backward**, **Forward** e **Híbrida**) aplicadas ao padrão Saga no estilo
**AEC — Assíncrono, Consistência Eventual, Coreografado**, em um domínio de
reserva e gestão de viagens corporativas. São cinco microsserviços Spring Boot,
cada um com seu próprio PostgreSQL, comunicando-se apenas por eventos via
Eventuate Tram (outbox transacional) + Eventuate CDC + Kafka. Não há
orquestrador: cada serviço reage aos eventos de forma independente.

## Subir tudo

```bash
docker compose up --build
```

Depois de alguns minutos, os cinco serviços respondem:

```bash
curl localhost:8080/actuator/health   # travel (único ponto de entrada externo)
curl localhost:8081/actuator/health   # approval
curl localhost:8082/actuator/health   # booking
curl localhost:8083/actuator/health   # payment
curl localhost:8084/actuator/health   # audit
```

Para rodar uma saga completa, do início ao fim, com um único comando:

```bash
curl -X POST localhost:8080/api/v1/solicitacoes \
  -H 'Content-Type: application/json' \
  -d '{"usuarioId":1,"destino":"São Paulo","dataIda":"2026-12-01",
       "dataVolta":"2026-12-05","motivo":"Reunião com cliente"}'

sleep 10
curl -s localhost:8080/api/v1/solicitacoes | head #status: CONFIRMADO
```

## A saga

`S_criacao = ⟨T1..T7⟩`. T1–T4 criam apenas reservas temporárias, cuja
compensação não tem custo; **a partir de T5 (ponto de pivô)** a compensação
significa estornar um pagamento.

| Etapa | Descrição | Serviço |
|---|---|---|
| T1 | Criar requisição de viagem (RASCUNHO) | travel |
| T2 | Solicitar e decidir a aprovação | approval |
| T3 | Reserva de voo — hold temporário | booking |
| T4 | Reserva de hotel — hold temporário | booking |
| T5 | Pagamento do voo | payment |
| T6 | Pagamento do hotel | payment |
| T7 | Confirmar viagem (CONFIRMADO) | travel |

T3/T5 (voo) e T4/T6 (hotel) correm em **ramos paralelos**, disparados pela
aprovação e reunidos só em T7 — cada par tem seu próprio prazo de hold, sem
depender do outro.

```
travel ──solicitacao.criada──▶ approval ──aprovacao.aprovada──▶ booking
                                                                   │
                             ┌─────────────────────────────────────┴──────┐
                             ▼ (ramo voo)                    (ramo hotel) ▼
                       hold.voo.criado                     hold.hotel.criado
                             │                                          │
                             ▼                                          ▼
                     payment: T5                                payment: T6
                             │                                          │
                   pagamento.voo.confirmado          pagamento.hotel.confirmado
                             └──────────────┬───────────────────────────┘
                                            ▼
                              travel: T7 quando os dois chegam
```

A **cadeia de compensação** percorre o mesmo grafo de trás para frente, e é
coreografada, ou seja, não existe uma função central que chame as compensações:

```
saga.falhou ▶ payment (estorna T6, T5) ▶ booking (libera T4, T3)
            ▶ approval (cancela T2) ▶ travel (cancela T1)
```

Um serviço que não tem nada a compensar ainda assim repassa a cadeia adiante.
É o que permite que uma falha em T2 e uma falha em T6 percorram o mesmo caminho
de volta.

## Configurar a rodada

Tudo por variável de ambiente (arquivo `.env`, ver `.env.example`):

| Variável | Valores | Efeito |
|---|---|---|
| `RECOVERY_STRATEGY` | `BACKWARD` \| `FORWARD` \| `HYBRID` | estratégia de recuperação ativa |
| `FAULT_SCENARIO` | `NONE` \| `TEMPORARY` \| `PERMANENT` | se e que tipo de falha é injetada |
| `FAULT_POSITION` | `T2` \| `T4` \| `T6` | onde a falha é injetada |
| `RECOVERY_RETRY_LIMIT` | inteiro (3) | tentativas Forward antes de desistir |
| `RECOVERY_RETRY_BACKOFF_MS` | inteiro (200) | backoff exponencial inicial |
| `FAULT_TEMPORARY_FAILURES` | inteiro (2) | execuções que a falha temporária derruba antes de se curar |
| `APPROVAL_DECISION_DELAY_SECONDS` | inteiro (2) | tempo simulado da decisão de aprovação |
| `EXPERIMENT_RUN_ID` | texto | identifica a rodada na tabela de auditoria |

Depois de alterar o `.env`:

```bash
docker compose up -d --force-recreate --no-deps travel approval booking payment audit
```

### O que esperar de cada combinação

`FAULT_POSITION` é ignorado quando o cenário é `NONE`.

| Cenário | Estratégia | Resultado esperado |
|---|---|---|
| `NONE` | qualquer | todas as sagas chegam a `CONFIRMADO` |
| `TEMPORARY` | `BACKWARD` | sem retry; compensa na primeira falha; saga `COMPENSADA` |
| `TEMPORARY` | `FORWARD` | retry com backoff; a falha se cura e a saga conclui, sem compensar |
| `TEMPORARY` | `HYBRID` | igual ao Forward enquanto se recupera; se esgotar, cai para a cadeia backward |
| `PERMANENT` em `T2` | qualquer | rejeição de negócio: saga `REJEITADA`, sem compensação (ainda antes do pivô) |
| `PERMANENT` em `T4`/`T6` | `BACKWARD`/`HYBRID` | compensa; saga `COMPENSADA` |
| `PERMANENT` em `T4`/`T6` | `FORWARD` | aborta sem desfazer nada; saga `ABORTADA` |

Para o Forward conseguir se recuperar de uma falha temporária,
`FAULT_TEMPORARY_FAILURES` precisa ser **menor** que `RECOVERY_RETRY_LIMIT`.
Com os dois iguais ou invertidos, a falha nunca se cura dentro do limite e o
Forward sempre aborta — o que é um cenário válido, mas outro cenário.

## Rodar a matriz de experimentos

Requer [JMeter](https://jmeter.apache.org/) no `PATH` e o stack no ar.

```bash
docker compose up --build -d
./scripts/run-experiments.sh #matriz completa (57 rodadas)
./scripts/run-experiments.sh --rapido #verificação smoke test (4 rodadas)
./scripts/run-experiments.sh --cargas 100 --posicoes T4 #outras configurações
```

Para cada combinação o script reconfigura os serviços, limpa os bancos, dispara
a carga, **espera as sagas estabilizarem** (a saga continua depois que o HTTP
responde — medir antes disso daria números truncados), exporta a linha do tempo
em CSV e as métricas em JSON, e segue para a próxima.

Saída em `resultados/`:

- `<runId>.csv` — linha do tempo completa da rodada, um evento por linha
- `<runId>-metricas.json` — métricas agregadas
- `<runId>.jtl` — resultados brutos do JMeter
- `consolidado.csv` — uma linha por rodada, com os fatores já em colunas,
  pronto para a análise estatística

## Métricas

A fonte dos números da monografia é a **tabela única `saga_auditoria`** do
serviço `audit`: cada serviço publica um evento de auditoria a cada transição
relevante (início, sucesso, falha, retry, compensação, desfecho da saga) no
canal `saga-audit`, e o `audit` grava tudo em um só lugar — evitando join entre
os cinco bancos na hora de analisar.

```bash
curl "localhost:8084/api/v1/auditoria/metricas?runId=local" #agregados
curl "localhost:8084/api/v1/auditoria/export?runId=local" #CSV bruto
```

Derivadas por agregação: taxa de sucesso, tempo médio de conclusão, tempo médio
de recuperação, número de compensações, número de retries, throughput, volume de
eventos, percentual de sagas recuperadas sem compensação e tempo até a
consistência eventual.

O Prometheus (`:9090`) e o Grafana (`:3000`, admin/admin) servem para observar a
rodada ao vivo. O Prometheus só guarda séries agregadas (throughput HTTP, taxa
de erro, JVM — dashboard *Saga — visão operacional*), porque as métricas do
experimento são por instância de saga. Por isso o Grafana tem um segundo
dashboard, *Métricas do experimento (auditoria da saga)*, que consulta o
audit-db diretamente (datasource Postgres `audit-postgres`) e mostra as nove
métricas acima ao vivo para a rodada selecionada — sem precisar do `curl` em
`/metricas`.

## Testes

```bash
for s in travel approval booking payment audit; do (cd $s && ./mvnw -B test) || break; done
```

Os testes de integração exercitam a coreografia real — mesmos eventos, mesmos
handlers, mesma serialização do Eventuate Tram — com mensageria **em memória** e
H2, então rodam sem Kafka, sem Postgres e sem Docker. Cobrem o caminho feliz,
cada compensação, cada caminho de retry, o fallback do modo Híbrido e o
recebimento duplicado de eventos.

## Portas

| Serviço | Aplicação | Banco |
|---|---|---|
| travel | 8080 | 5432 |
| approval | 8081 | 5433 |
| booking | 8082 | 5434 |
| payment | 8083 | 5435 |
| audit | 8084 | 5436 |

pgAdmin `5050` · kafka-ui `8090` · Kafka `9092` · Eventuate CDC `8099` ·
Prometheus `9090` · Grafana `3000`

## Serviços

- [travel](travel/README.md) — abre (T1) e fecha (T7) a saga; único ponto de entrada HTTP externo
- [approval](approval/README.md) — decisão de aprovação (T2)
- [booking](booking/README.md) — holds de voo e hotel (T3, T4)
- [payment](payment/README.md) — pagamentos de voo e hotel (T5, T6)
- `audit` — coletor central da auditoria e cálculo das métricas

## Notas de implementação

**Contrato de eventos compartilhado.** O Eventuate Tram casa produtor e
consumidor pelo nome totalmente qualificado da classe do evento, então os cinco
serviços precisam das mesmas classes em `com.tcc.saga`. Como cada serviço é um
projeto Maven independente que se constrói sozinho no seu Dockerfile, a fonte da
verdade fica em `shared/` e é copiada para dentro de cada serviço por
`scripts/sync-shared.sh`. **Alterou algo em `shared/`? Rode o script e faça
commit das cópias.**

**Motor de recuperação.** Toda etapa que pode falhar (T2, T4, T6) passa pela
tabela `etapa_pendente`, inclusive na primeira execução. Isso dá um único
caminho de código para "executar" e "tentar de novo", torna cada tentativa
observável na auditoria e permite backoff sem bloquear a thread do consumidor
Kafka — que é o que um retry síncrono dentro do handler faria.

**Aprovação de longa duração.** A metodologia descreve aprovações que levam
dias. Isso é inviável em um teste de carga automatizado, então a decisão é
automática e configurável por `APPROVAL_DECISION_DELAY_SECONDS`, agendada pelo
mesmo motor que executa as retentativas. **Essa simplificação precisa constar
explicitamente na metodologia do TCC.**

**Modo polling no CDC.** Os readers do Eventuate CDC usam polling em vez de
replicação lógica, então o Postgres não precisa de `wal_level=logical` nem do
plugin `wal2json`, que não vem na imagem oficial. Uma única instância do CDC
atende os cinco bancos, com um reader e um pipeline para cada. O ZooKeeper no
compose existe **apenas** para a eleição de líder do CDC — o Kafka roda em modo
KRaft e não o utiliza.

**Sem dados de exemplo nos `init.sql`.** Cada rodada precisa começar de um
estado limpo, senão as linhas de fixture entram nas agregações da análise.
