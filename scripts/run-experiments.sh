#!/usr/bin/env bash
#
# Roda a matriz de experimentos do início ao fim, sem intervenção manual.
#
# Para cada combinação de estratégia × cenário × posição de falha × carga:
#   1. reconfigura as variáveis de ambiente da rodada
#   2. recria os containers dos serviços para que peguem a nova configuração
#   3. limpa o estado dos bancos (rodadas não podem contaminar umas às outras)
#   4. dispara a carga com o JMeter
#   5. espera as sagas estabilizarem (consistência eventual)
#   6. exporta a linha do tempo em CSV e as métricas em JSON
#
# Uso:
#   ./scripts/run-experiments.sh                          # matriz completa
#   ./scripts/run-experiments.sh --cargas 100             # só carga baixa
#   ./scripts/run-experiments.sh --posicoes T4 --cargas 100 --estrategias BACKWARD,FORWARD
#   ./scripts/run-experiments.sh --rapido                 # verificação de fumaça
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"

# ---------------------------------------------------------------------------
# Parâmetros da matriz
# ---------------------------------------------------------------------------
ESTRATEGIAS="BACKWARD FORWARD HYBRID"
CENARIOS="TEMPORARY PERMANENT"
POSICOES="T2 T4 T6"
CARGAS="100 1000 5000"
USUARIOS="${USUARIOS:-50}"
RODAR_BASELINE="sim"

RESULTADOS="$RAIZ/resultados"
TRAVEL_URL="http://localhost:8080"
AUDIT_URL="http://localhost:8084"
TIMEOUT_ESTABILIZACAO="${TIMEOUT_ESTABILIZACAO:-300}"
JMETER="${JMETER:-jmeter}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --estrategias) ESTRATEGIAS="${2//,/ }"; shift 2 ;;
    --cenarios)    CENARIOS="${2//,/ }";    shift 2 ;;
    --posicoes)    POSICOES="${2//,/ }";    shift 2 ;;
    --cargas)      CARGAS="${2//,/ }";      shift 2 ;;
    --sem-baseline) RODAR_BASELINE="nao";   shift ;;
    --rapido)
      ESTRATEGIAS="BACKWARD FORWARD HYBRID"; CENARIOS="TEMPORARY"; POSICOES="T4"
      CARGAS="100"; RODAR_BASELINE="sim"; shift ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "opção desconhecida: $1" >&2; exit 1 ;;
  esac
done

mkdir -p "$RESULTADOS"

log() { echo -e "\033[1;34m[matriz]\033[0m $*"; }
erro() { echo -e "\033[1;31m[erro]\033[0m $*" >&2; }

# ---------------------------------------------------------------------------
# Infra
# ---------------------------------------------------------------------------

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

# ---------------------------------------------------------------------------
# Rodada
# ---------------------------------------------------------------------------

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
      return 0
    fi
    sleep 3
  done
}

executar_rodada() {
  local estrategia="$1" cenario="$2" posicao="$3" carga="$4"
  local run_id="${estrategia}_${cenario}_${posicao}_${carga}"

  log "=============================================================="
  log "rodada: $run_id"
  log "=============================================================="

  cat > "$RAIZ/.env" <<EOF
EXPERIMENT_RUN_ID=$run_id
RECOVERY_STRATEGY=$estrategia
FAULT_SCENARIO=$cenario
FAULT_POSITION=$posicao
RECOVERY_RETRY_LIMIT=${RECOVERY_RETRY_LIMIT:-3}
RECOVERY_RETRY_BACKOFF_MS=${RECOVERY_RETRY_BACKOFF_MS:-200}
RECOVERY_RETRY_BACKOFF_MAX_MS=${RECOVERY_RETRY_BACKOFF_MAX_MS:-5000}
FAULT_TEMPORARY_FAILURES=${FAULT_TEMPORARY_FAILURES:-2}
APPROVAL_DECISION_DELAY_SECONDS=${APPROVAL_DECISION_DELAY_SECONDS:-2}
EOF

  reconfigurar_servicos
  limpar_bancos

  local usuarios=$USUARIOS
  [ "$carga" -lt "$usuarios" ] && usuarios=$carga
  local loops=$((carga / usuarios))

  log "carga: $carga requisições ($usuarios usuários × $loops iterações)"
  "$JMETER" -n -t "$RAIZ/experiments/saga-load-test.jmx" \
    -Jcarga="$carga" -Jusuarios="$usuarios" -Jloops="$loops" \
    -JsaidaJtl="$RESULTADOS/${run_id}.jtl" \
    -j "$RESULTADOS/${run_id}-jmeter.log" >/dev/null

  esperar_estabilizacao "$run_id" "$carga"

  log "exportando resultados"
  curl -sf "$AUDIT_URL/api/v1/auditoria/export?runId=$run_id" -o "$RESULTADOS/${run_id}.csv"
  curl -sf "$AUDIT_URL/api/v1/auditoria/metricas?runId=$run_id" -o "$RESULTADOS/${run_id}-metricas.json"
  log "gravado: resultados/${run_id}.csv e ${run_id}-metricas.json"
}

# ---------------------------------------------------------------------------
# Execução
# ---------------------------------------------------------------------------

command -v "$JMETER" >/dev/null || { erro "jmeter não encontrado no PATH (use JMETER=/caminho/para/jmeter)"; exit 1; }
curl -sf "$TRAVEL_URL/actuator/health" >/dev/null || {
  erro "o stack não está no ar. Rode antes: docker compose up --build -d"
  exit 1
}

total=0
[ "$RODAR_BASELINE" = "sim" ] && total=$((total + $(echo "$CARGAS" | wc -w)))
total=$((total + $(echo "$ESTRATEGIAS" | wc -w) * $(echo "$CENARIOS" | wc -w) \
        * $(echo "$POSICOES" | wc -w) * $(echo "$CARGAS" | wc -w)))
log "matriz com $total rodadas"

# Baseline: sem falha não há recuperação a comparar, então roda uma vez por carga.
if [ "$RODAR_BASELINE" = "sim" ]; then
  for carga in $CARGAS; do
    executar_rodada "BACKWARD" "NONE" "T4" "$carga"
  done
fi

for cenario in $CENARIOS; do
  for posicao in $POSICOES; do
    for estrategia in $ESTRATEGIAS; do
      for carga in $CARGAS; do
        executar_rodada "$estrategia" "$cenario" "$posicao" "$carga"
      done
    done
  done
done

log "matriz concluída. Consolidando..."
python3 "$RAIZ/scripts/consolidar-resultados.py" "$RESULTADOS" || true
log "resultados em $RESULTADOS"
