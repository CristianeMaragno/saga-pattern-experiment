#!/usr/bin/env bash
#
# Funções de infraestrutura compartilhadas entre os scripts de experimento
# (run-experiments.sh e run-experimento-carga.sh): recriar os serviços, limpar
# os bancos, aquecer a JVM e esperar a consistência eventual antes de exportar
# resultados. Ver Isolamento-experimento.md para o porquê de cada etapa.
#
# Este arquivo só define funções — não é executável sozinho. Quem o
# `source`ia deve já ter definido antes: RAIZ, RESULTADOS, TRAVEL_URL,
# AUDIT_URL, TIMEOUT_ESTABILIZACAO, AQUECIMENTO, AQUECIMENTO_ESPERA_SEGUNDOS.

log() { echo -e "\033[1;34m[experimento]\033[0m $*"; }
erro() { echo -e "\033[1;31m[erro]\033[0m $*" >&2; }

# Trunca as tabelas de negócio e de mensageria de travel/approval/booking/
# payment. Sem isso, a rodada seguinte herdaria sagas pendentes e mensagens
# não publicadas da anterior, contaminando as métricas. saga_auditoria (no
# audit-db) é a exceção — ver comentário em limpar_bancos.
# O TRUNCATE de várias tabelas de uma vez pede lock exclusivo em todas elas, e
# o serviço recém-recriado (reconfigurar_servicos) pode ainda ter threads em
# segundo plano (leitor do CDC, consumidor Kafka) consultando essas mesmas
# tabelas — o Postgres às vezes resolve isso com "ERROR: deadlock detected",
# o que mataria a matriz inteira no meio por causa do set -e. Como é
# transitório, a tentativa é repetida antes de desistir de verdade.
truncar() {
  local servico="$1" usuario="$2" banco="$3" sql="$4"
  local tentativas=0
  until docker compose exec -T "$servico" psql -U "$usuario" -d "$banco" -q -c "$sql" >/dev/null; do
    tentativas=$((tentativas + 1))
    if [ "$tentativas" -ge 5 ]; then
      erro "limpeza de $banco falhou após $tentativas tentativas"
      return 1
    fi
    log "limpeza de $banco falhou (provável deadlock transitório), tentativa $tentativas/5"
    sleep 2
  done
}

limpar_bancos() {
  log "limpando o estado dos bancos"
  truncar travel-db travel_user travel_db \
    "TRUNCATE solicitacoes, eventuate.message, eventuate.received_messages RESTART IDENTITY;"
  truncar approval-db approval_user approval_db \
    "TRUNCATE aprovacoes, etapa_pendente, eventuate.message, eventuate.received_messages RESTART IDENTITY;"
  truncar booking-db booking_user booking_db \
    "TRUNCATE holds, etapa_pendente, eventuate.message, eventuate.received_messages RESTART IDENTITY;"
  truncar payment-db payment_user payment_db \
    "TRUNCATE pagamentos, etapa_pendente, eventuate.message, eventuate.received_messages RESTART IDENTITY;"
  # saga_auditoria fica de fora de propósito: toda linha já carrega run_id, e
  # é o dataset final da análise — limpar a cada rodada só faria perder o
  # histórico das rodadas anteriores sem nenhum ganho (as consultas de
  # métricas já filtram por run_id, então rodadas antigas não contaminam a
  # atual). Só o outbox/dedup do Eventuate Tram é limpo aqui.
  truncar audit-db audit_user audit_db \
    "TRUNCATE eventuate.message, eventuate.received_messages RESTART IDENTITY;"
}

esperar_saude() {
  log "aguardando os serviços ficarem saudáveis"
  local portas=(8080 8081 8082 8083 8084)
  for porta in "${portas[@]}"; do
    local tentativas=0
    until curl -sf "http://localhost:$porta/actuator/health" | grep -q '"status":"UP"'; do
      tentativas=$((tentativas + 1))
      if [ "$tentativas" -gt 90 ]; then
        erro "serviço na porta $porta não ficou saudável"
        return 1
      fi
      sleep 2
    done
  done
  log "todos os serviços estão UP"
}

# Recria apenas as aplicações: os bancos e o Kafka continuam de pé, então a
# troca de combinação custa segundos em vez de minutos.
reconfigurar_servicos() {
  log "recriando as aplicações com a nova configuração"
  docker compose up -d --force-recreate --no-deps travel approval booking payment audit >/dev/null
  esperar_saude
}

# Aquecimento (warm-up): dispara algumas sagas reais e descartáveis antes de
# começar a medir. A JVM acabou de subir (reconfigurar_servicos) e está com o
# JIT frio — as primeiras requisições são sistematicamente mais lentas só por
# isso, não pelo desenho da estratégia de recuperação sendo avaliada. Ver
# Isolamento-experimento.md, item 3 (HUF, 2025, p. 130).
#
# O tráfego de aquecimento é descartado antes da carga medida: limpar_bancos()
# apaga as linhas de negócio/outbox, e o DELETE abaixo apaga o rastro de
# auditoria correspondente (a auditoria não é truncada em limpar_bancos porque
# ela é o dataset final da análise — ver comentário lá).
aquecer_servicos() {
  local run_id="$1" n="$AQUECIMENTO"
  [ "$n" -le 0 ] && return 0

  log "aquecendo a JVM com $n saga(s) descartável(is)"
  for ((i = 0; i < n; i++)); do
    curl -sf -X POST "$TRAVEL_URL/api/v1/solicitacoes" \
      -H 'Content-Type: application/json' \
      -d '{"usuarioId":999999999,"destino":"Aquecimento","dataIda":"2026-12-01",
           "dataVolta":"2026-12-05","motivo":"aquecimento do experimento (descartado)"}' \
      >/dev/null || true
  done
  sleep "$AQUECIMENTO_ESPERA_SEGUNDOS"

  curl -sf -X DELETE "$AUDIT_URL/api/v1/auditoria?runId=$run_id" >/dev/null || true
}

# Espera a consistência eventual: a saga continua depois que o HTTP respondeu,
# então medir logo após o JMeter daria números truncados.
esperar_estabilizacao() {
  local run_id="$1" esperadas="$2"
  local inicio; inicio=$(date +%s)
  local anterior=-1 estavel=0

  while true; do
    local metricas total andamento
    metricas=$(curl -sf "$AUDIT_URL/api/v1/auditoria/metricas?runId=$run_id" || echo '{}')
    total=$(echo "$metricas" | grep -o '"totalSagas":[0-9]*' | cut -d: -f2 || echo 0)
    andamento=$(echo "$metricas" | grep -o '"sagasEmAndamento":[0-9]*' | cut -d: -f2 || echo 0)
    total=${total:-0}; andamento=${andamento:-0}

    if [ "$total" -ge "$esperadas" ] && [ "$andamento" -eq 0 ]; then
      log "estabilizou: $total sagas concluíram seu ciclo"
      return 0
    fi

    # Também para quando nada mais se move, para não travar a matriz inteira
    # por causa de uma rodada com sagas presas. total=0 nunca conta como
    # "parado": logo após o force-recreate dos serviços, o CDC pode levar bem
    # mais que alguns segundos para retomar a publicação, e 0 parado só
    # significa "ainda não chegou nada", não "a rodada terminou".
    if [ "$total" -eq "$anterior" ] && [ "$andamento" -eq 0 ] && [ "$total" -gt 0 ]; then
      estavel=$((estavel + 1))
      [ "$estavel" -ge 3 ] && { log "estabilizou com $total/$esperadas sagas"; return 0; }
    else
      estavel=0
    fi
    anterior=$total

    if [ $(($(date +%s) - inicio)) -gt "$TIMEOUT_ESTABILIZACAO" ]; then
      erro "tempo esgotado aguardando estabilização ($total/$esperadas, $andamento em andamento)"
      return 1
    fi
    sleep 3
  done
}
