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

Para cada combinação o script reconfigura os serviços, **aquece a JVM** com
sagas descartáveis, limpa os bancos e o rastro de auditoria desse aquecimento,
dispara a carga, **espera as sagas estabilizarem** (a saga continua depois que
o HTTP responde — medir antes disso daria números truncados), exporta a linha
do tempo em CSV e as métricas em JSON, faz uma pausa de resfriamento e segue
para a próxima. Ver a seção seguinte para o porquê de cada uma dessas etapas.

Saída em `resultados/`:

- `<runId>.csv` — linha do tempo completa da rodada, um evento por linha
- `<runId>-metricas.json` — métricas agregadas
- `<runId>.jtl` — resultados brutos do JMeter
- `<runId>.estabilizou` — `sim`/`nao`: se a rodada saiu do loop de espera por
  ter realmente estabilizado ou por ter esgotado `TIMEOUT_ESTABILIZACAO`
  (`consolidar-resultados.py` usa isso para não deixar uma rodada incompleta
  passar por normal silenciosamente)
- `consolidado.csv` — uma linha por rodada, com os fatores já em colunas e o
  IC95% do tempo de conclusão, pronto para a análise estatística

## Calibração de carga (justificando os volumes 100/1000/5000)

Experimento separado da matriz de estratégia × cenário × posição de falha,
que responde a uma pergunta diferente: **que volumes de usuários usar** na
matriz principal, e por quê. Em vez de comparar estratégias de recuperação,
roda uma única configuração fixa (por padrão sem falha injetada, para isolar
o efeito do volume do custo de compensação/retry) sob uma varredura fina de
cargas — do menor ao maior volume — e permite observar em que faixa as
métricas param de mudar de forma relevante entre um ponto e o próximo. Essa
estabilização é o critério objetivo para justificar, na monografia, que
100/1000/5000 representam de fato os regimes de carga baixa, intermediária e
já estabilizada, em vez de serem valores arbitrários.

```bash
docker compose up --build -d
./scripts/run-experimento-carga.sh                                   # varredura padrão (10 pontos)
./scripts/run-experimento-carga.sh --cargas 10,50,100,500,1000,5000,10000
./scripts/run-experimento-carga.sh --estrategia FORWARD --cenario TEMPORARY --posicao T4
```

Reaproveita a mesma infraestrutura de isolamento da matriz principal (JVM
nova por rodada, aquecimento, limpeza de bancos, espera de consistência
eventual, pausa de resfriamento — ver `scripts/experimento-lib.sh`, extraída
de `run-experiments.sh` para as duas rotinas compartilharem o mesmo código).

Saída em `resultados/calibracao-carga/` (pasta própria, para não misturar
com `resultados/consolidado.csv` da matriz principal):

- `CARGA_<carga>.csv` / `-metricas.json` / `.jtl` / `.estabilizou` — por
  ponto de carga, mesmo formato da matriz principal
- `consolidado-carga.csv` — uma linha por carga, **ordenada numericamente**,
  com colunas extras de variação percentual em relação ao ponto anterior
  (`taxa_sucesso_variacao_pct`, `tempo_medio_conclusao_variacao_pct`,
  `throughput_variacao_pct` — ver `scripts/consolidar-carga.py`). É essa
  variação que sustenta a alegação de estabilização: por exemplo, throughput
  variando 33% de 10→100 usuários mas menos de 1% de 1000→5000 indica que a
  faixa alta já saturou o que a mudança de volume consegue afetar.

## Controle de isolamento experimental

Medir tempo de execução de um sistema Java sob carga tem as mesmas armadilhas
descritas no Capítulo 5 da tese de HUF (2025) — *FasterSparql: An architecture
for query mediation over loosely coupled federations of knowledge graphs*
(UFSC, orientador Frank A. Siqueira): JIT ainda "frio", GC, throttling térmico
e variância não controlada podem confundir o efeito da estratégia de
recuperação sendo comparada com ruído do ambiente. A extração completa das
técnicas do capítulo 5.2 da tese e a análise de aplicabilidade a este
experimento estão em [`Isolamento-experimento.md`](Isolamento-experimento.md);
esta seção resume o que foi adotado, adaptado ou descartado, e onde cada
decisão está implementada.

O experimento da tese usa JMH (Java Microbenchmark Harness) para medir um
único processo em memória; o nosso mede uma saga coreografada fim a fim, entre
5 JVMs, Kafka e 5 bancos, via HTTP/JMeter — por isso nem toda técnica se
transporta 1:1. As adaptações e recusas estão marcadas como tal.

| # | Técnica (HUF, 2025) | Status aqui | Onde |
|---|---|---|---|
| 2 | JVM nova por configuração testada (fork) | **Adotado** — `docker compose up --force-recreate` recria os 5 containers a cada combinação, evitando JIT "aquecido", GC e estado estático vazando de uma rodada para outra | `scripts/run-experiments.sh` (`reconfigurar_servicos`) |
| 3 | Warm-up com descarte de amostras iniciais | **Adotado** — antes de medir, `N` sagas descartáveis aquecem JIT, pool de conexões e os tópicos Kafka; o próprio `TRUNCATE` e um `DELETE /auditoria?runId=` descartam esse tráfego antes da carga real | `scripts/run-experiments.sh` (`aquecer_servicos`), configurável via `AQUECIMENTO`/`--aquecimento` |
| 5 | Aguardar término real de tasks assíncronas antes da próxima medição | **Adaptado** — Kafka não tem um "cancelamento com espera" como o `Future` do JMH; a defesa aqui é descartar mensagens tardias de uma rodada anterior nos 5 handlers de evento (`obterSeExistir`/`orElse(null)`), em vez de deixá-las contaminar o `run_id` corrente ou derrubar o consumidor (episódio documentado em `Ajustes.md`, 2026-09-07) | `travel/.../SolicitacaoUseCase.java` e equivalentes em approval/booking/payment |
| 6 | Pausas de resfriamento térmico entre iterações | **Adaptado** — a fórmula da tese vale para iterações de milissegundos dentro de um fork; aqui a pausa é entre rodadas inteiras (minutos), para que a carga térmica/CPU de uma rodada não vaze sistematicamente para a próxima | `scripts/run-experiments.sh`, `PAUSA_ENTRE_RODADAS`/`--pausa` (padrão 15s) |
| 7 | Máquina única dedicada, sem vizinhos ruidosos / fora de horário de pico | **Adotado** (já era o caso) — todos os serviços, bancos e Kafka rodam no mesmo `docker-compose.yaml`, numa única máquina; recomendação operacional: evitar rodar a matriz completa com a máquina sob outra carga pesada concorrente | `docker-compose.yaml` |
| 8 | Heap máximo fixo (`-Xmx`) igual entre configurações | **Adotado** — `-Xms512m -Xmx512m` fixo e idêntico nos 5 serviços e em todas as rodadas, via `JAVA_TOOL_OPTIONS` | `docker-compose.yaml` (`x-experimento`), ajustável por `JAVA_HEAP_OPTS` |
| 10 | IC95% via bootstrap BCa em vez de teste-t clássico | **Adotado** — tempos de execução têm cauda longa (GC, I/O, retry/backoff) e não se pode assumir normalidade; `consolidar-resultados.py` reconstrói os tempos de conclusão por saga a partir do `<runId>.csv` e calcula o IC95% por bootstrap BCa (Efron; Tibshirani, 1986), sem depender de numpy/scipy | `scripts/consolidar-resultados.py` (`bootstrap_bca`), colunas `tempo_conclusao_ic95_*` em `consolidado.csv` |
| 11 | Agregar sobre uma janela mínima em vez de medir invocações isoladas | **Já satisfeito por construção** — as métricas sempre agregam entre 100 e 5.000 sagas por rodada, nunca uma invocação isolada; nenhuma mudança necessária | `audit` (`AuditoriaUseCase.calcularMetricas`) |
| 1 | JMH como base do isolamento | **Não aplicável** — JMH mede um método Java isolado, dentro de um processo; aqui a métrica é fim a fim entre 5 processos/rede/mensageria, o que está fora do escopo do JMH. JMeter cumpre o papel equivalente (orquestrar carga, medir, agregar) | — |
| 4 | GC completo forçado entre warm-up e medição | **Avaliado e não adotado** — exigiria `jcmd` (só vem no JDK completo; as imagens de runtime aqui são `eclipse-temurin:*-jre-alpine`, sem ferramentas de diagnóstico) trocar a imagem só para isso não se justificava. Além disso, o lixo do warm-up pesa muito menos numa janela de medição de segundos/minutos do que numa iteração JMH de 500ms | — |
| 9 | VM "dedicated" vs. "burstable"/"shared" | **Não aplicável** — execução local, não em nuvem; não há *neighbor* multi-tenant disputando os mesmos núcleos | — |
| 12 | Profiling de amostragem (async-profiler) separado da medição | **Não aplicável por ora** — não há instrumentação manual fina (`System.nanoTime()` espalhado) dentro dos handlers que precise ser substituída; fica registrado como abordagem recomendada caso o trabalho futuro precise decompor onde o tempo é gasto dentro da saga | — |

**Limitação sabida:** o aquecimento (item 3) usa um número fixo e pequeno de
sagas (`AQUECIMENTO`, padrão 5) e uma espera fixa (`AQUECIMENTO_ESPERA_SEGUNDOS`,
padrão 10s) em vez de convergência observada — suficiente para tocar os
caminhos de código relevantes (incluindo o de compensação, quando o cenário da
rodada injeta falha), mas não garante que 100% do tráfego de aquecimento
chegue a um estado terminal antes de prosseguir. Isso é aceitável porque o
objetivo é aquecer a JVM, não medir o aquecimento.

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

## Exemplos de consultas PromQL

Já vêm prontas como gráficos no dashboard *Saga — visão operacional* do
Grafana (ver seção de observabilidade acima) — isto aqui é só referência caso
queira rodar alguma direto na aba **Graph** do Prometheus (`:9090`).

**Requisições por segundo, por serviço**

```promql
sum by (servico) (rate(http_server_requests_seconds_count[1m]))
```

**Taxa de erro HTTP 5xx, por serviço** (se subir do zero, algo quebrou de
verdade — não é falha injetada pelo experimento)

```promql
sum by (servico) (rate(http_server_requests_seconds_count{status=~"5.."}[1m]))
```

**Latência p95 do endpoint de criação de solicitação** (em segundos)

```promql
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/api/v1/solicitacoes"}[1m])))
```

**Heap da JVM em uso, por serviço** (útil se uma rodada de carga alta, tipo
5000, parecer estar sufocando algum container)

```promql
sum by (servico) (jvm_memory_used_bytes{area="heap"})
```

