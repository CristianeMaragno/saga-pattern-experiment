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
#
# Controle de isolamento experimental (ver Isolamento-experimento.md e a seção
# correspondente no README): entre rodadas, o script (1) recria os containers
# das aplicações — JVM nova a cada configuração testada —, (2) aquece cada JVM
# com sagas descartáveis antes de começar a medir, para não contaminar as
# métricas com o tempo de JIT ainda "frio", (3) limpa bancos e o rastro de
# auditoria desse aquecimento, (4) só então dispara a carga medida pelo
# JMeter, e (5) faz uma pausa de resfriamento antes da rodada seguinte.
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

# Isolamento: aquecimento (warm-up) descartado antes de cada rodada medida, e
# pausa de resfriamento entre rodadas. Ver Isolamento-experimento.md, itens 3 e 6.
AQUECIMENTO="${AQUECIMENTO:-5}"
AQUECIMENTO_ESPERA_SEGUNDOS="${AQUECIMENTO_ESPERA_SEGUNDOS:-10}"
PAUSA_ENTRE_RODADAS="${PAUSA_ENTRE_RODADAS:-15}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --estrategias) ESTRATEGIAS="${2//,/ }"; shift 2 ;;
    --cenarios)    CENARIOS="${2//,/ }";    shift 2 ;;
    --posicoes)    POSICOES="${2//,/ }";    shift 2 ;;
    --cargas)      CARGAS="${2//,/ }";      shift 2 ;;
    --sem-baseline) RODAR_BASELINE="nao";   shift ;;
    --aquecimento) AQUECIMENTO="$2";        shift 2 ;;
    --sem-aquecimento) AQUECIMENTO="0";     shift ;;
    --pausa)       PAUSA_ENTRE_RODADAS="$2"; shift 2 ;;
    --rapido)
      ESTRATEGIAS="BACKWARD FORWARD HYBRID"; CENARIOS="TEMPORARY"; POSICOES="T4"
      CARGAS="100"; RODAR_BASELINE="sim"; shift ;;
    -h|--help) sed -n '2,26p' "$0"; exit 0 ;;
    *) echo "opção desconhecida: $1" >&2; exit 1 ;;
  esac
done

mkdir -p "$RESULTADOS"

# Infra compartilhada com run-experimento-carga.sh (log/erro, truncar,
# limpar_bancos, esperar_saude, reconfigurar_servicos, aquecer_servicos,
# esperar_estabilizacao) — ver scripts/experimento-lib.sh.
source "$RAIZ/scripts/experimento-lib.sh"

# ---------------------------------------------------------------------------
# Rodada
# ---------------------------------------------------------------------------

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
  aquecer_servicos "$run_id"
  limpar_bancos

  local usuarios=$USUARIOS
  [ "$carga" -lt "$usuarios" ] && usuarios=$carga
  local loops=$((carga / usuarios))

  log "carga: $carga requisições ($usuarios usuários × $loops iterações)"
  "$JMETER" -n -t "$RAIZ/experiments/saga-load-test.jmx" \
    -Jcarga="$carga" -Jusuarios="$usuarios" -Jloops="$loops" \
    -JsaidaJtl="$RESULTADOS/${run_id}.jtl" \
    -j "$RESULTADOS/${run_id}-jmeter.log" >/dev/null

  # Registrado à parte (não aborta a matriz via set -e) para que a análise
  # consiga distinguir uma rodada que terminou de verdade de uma que só
  # esgotou o timeout — consolidar-resultados.py lê esse marcador.
  local estabilizou="sim"
  esperar_estabilizacao "$run_id" "$carga" || estabilizou="nao"
  echo "$estabilizou" > "$RESULTADOS/${run_id}.estabilizou"

  log "exportando resultados"
  curl -sf "$AUDIT_URL/api/v1/auditoria/export?runId=$run_id" -o "$RESULTADOS/${run_id}.csv"
  curl -sf "$AUDIT_URL/api/v1/auditoria/metricas?runId=$run_id" -o "$RESULTADOS/${run_id}-metricas.json"
  log "gravado: resultados/${run_id}.csv e ${run_id}-metricas.json"

  # Isolamento: pausa de resfriamento antes da próxima rodada, para que carga
  # térmica/uso de CPU acumulados numa rodada não vazem para a próxima e
  # virem uma variável de confusão sistemática nas rodadas tardias da matriz.
  # Ver Isolamento-experimento.md, item 6 (HUF, 2025, pp. 131-132).
  if [ "$PAUSA_ENTRE_RODADAS" -gt 0 ]; then
    log "pausa de resfriamento de ${PAUSA_ENTRE_RODADAS}s"
    sleep "$PAUSA_ENTRE_RODADAS"
  fi
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
