#!/usr/bin/env bash
#
# Experimento de calibração de carga — totalmente separado da matriz de
# estratégia × cenário × posição de falha (run-experiments.sh). A pergunta
# aqui não é "qual estratégia recupera melhor", é "que tamanhos de carga
# usar" nos demais experimentos: dispara a mesma configuração de saga (sem
# falha injetada, por padrão) sob uma varredura fina de volumes de usuários e
# registra as métricas de cada ponto. Rodando do menor ao maior volume,
# dá para observar em que faixa taxa de sucesso/tempo médio/throughput páram
# de mudar de forma relevante — a "estabilização" que justifica, na
# monografia, os volumes escolhidos para a matriz principal (hoje 100/1000/
# 5000): eles devem representar carga baixa, a faixa de transição e a faixa
# já estabilizada, não pontos arbitrários.
#
# Reaproveita a mesma infraestrutura de isolamento da matriz principal (JVM
# nova por rodada, aquecimento, limpeza de bancos, espera de consistência
# eventual, pausa de resfriamento) — ver scripts/experimento-lib.sh e
# Isolamento-experimento.md.
#
# Uso:
#   ./scripts/run-experimento-carga.sh
#   ./scripts/run-experimento-carga.sh --cargas 10,50,100,500,1000,5000,10000
#   ./scripts/run-experimento-carga.sh --estrategia FORWARD --cenario TEMPORARY --posicao T4
#
# Saída em resultados/calibracao-carga/ (separada de resultados/ da matriz
# principal, para não misturar os dois experimentos em consolidado.csv):
#   - CARGA_<carga>.csv / -metricas.json / .jtl / .estabilizou — por rodada
#   - consolidado-carga.csv — uma linha por carga, ordenada, com a variação
#     percentual em relação ao ponto anterior (ver scripts/consolidar-carga.py)
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"

# ---------------------------------------------------------------------------
# Parâmetros da varredura
# ---------------------------------------------------------------------------
# Sem falha injetada por padrão: o objetivo é isolar o efeito do volume de
# usuários, não misturá-lo com o custo de compensação/retry de uma estratégia
# de recuperação (esse é o objetivo da matriz principal). Estrategia/cenário/
# posição continuam configuráveis para quem quiser repetir a calibração sob
# uma condição de falha específica.
ESTRATEGIA="${ESTRATEGIA:-BACKWARD}"
CENARIO="${CENARIO:-NONE}"
POSICAO="${POSICAO:-T4}"
CARGAS="10 25 50 100 250 500 1000 2500 5000 7500 10000"
USUARIOS="${USUARIOS:-50}"

RESULTADOS="$RAIZ/resultados/calibracao-carga"
TRAVEL_URL="http://localhost:8080"
AUDIT_URL="http://localhost:8084"
TIMEOUT_ESTABILIZACAO="${TIMEOUT_ESTABILIZACAO:-300}"
JMETER="${JMETER:-jmeter}"

AQUECIMENTO="${AQUECIMENTO:-5}"
AQUECIMENTO_ESPERA_SEGUNDOS="${AQUECIMENTO_ESPERA_SEGUNDOS:-10}"
PAUSA_ENTRE_RODADAS="${PAUSA_ENTRE_RODADAS:-15}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --cargas)    CARGAS="${2//,/ }"; shift 2 ;;
    --estrategia) ESTRATEGIA="$2";   shift 2 ;;
    --cenario)   CENARIO="$2";       shift 2 ;;
    --posicao)   POSICAO="$2";       shift 2 ;;
    --pausa)     PAUSA_ENTRE_RODADAS="$2"; shift 2 ;;
    -h|--help) sed -n '2,29p' "$0"; exit 0 ;;
    *) echo "opção desconhecida: $1" >&2; exit 1 ;;
  esac
done

mkdir -p "$RESULTADOS"

source "$RAIZ/scripts/experimento-lib.sh"

# ---------------------------------------------------------------------------
# Rodada
# ---------------------------------------------------------------------------

executar_rodada_carga() {
  local carga="$1"
  # Zero-padded para ordenar corretamente tanto como texto (Grafana, `sort`)
  # quanto numericamente — sem isso "CARGA_1000" viria antes de "CARGA_500".
  local run_id; run_id="CARGA_$(printf '%06d' "$carga")"

  log "=============================================================="
  log "rodada: $run_id (carga=$carga usuarios=$ESTRATEGIA/$CENARIO/$POSICAO)"
  log "=============================================================="

  cat > "$RAIZ/.env" <<EOF
EXPERIMENT_RUN_ID=$run_id
RECOVERY_STRATEGY=$ESTRATEGIA
FAULT_SCENARIO=$CENARIO
FAULT_POSITION=$POSICAO
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

  local estabilizou="sim"
  esperar_estabilizacao "$run_id" "$carga" || estabilizou="nao"
  echo "$estabilizou" > "$RESULTADOS/${run_id}.estabilizou"

  log "exportando resultados"
  curl -sf "$AUDIT_URL/api/v1/auditoria/export?runId=$run_id" -o "$RESULTADOS/${run_id}.csv"
  curl -sf "$AUDIT_URL/api/v1/auditoria/metricas?runId=$run_id" -o "$RESULTADOS/${run_id}-metricas.json"
  log "gravado: resultados/calibracao-carga/${run_id}.csv e ${run_id}-metricas.json"

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

log "calibração de carga com $(echo "$CARGAS" | wc -w) pontos, config fixa $ESTRATEGIA/$CENARIO/$POSICAO"

for carga in $CARGAS; do
  executar_rodada_carga "$carga"
done

log "varredura concluída. Consolidando..."
python3 "$RAIZ/scripts/consolidar-carga.py" "$RESULTADOS" || true
log "resultados em $RESULTADOS"
