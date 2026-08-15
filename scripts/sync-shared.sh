#!/usr/bin/env bash
#
# Copia o contrato compartilhado da saga (shared/java/com/tcc/saga) para dentro
# de cada serviço.
#
# Por que copiar em vez de criar um módulo Maven compartilhado: o Eventuate Tram
# casa produtor e consumidor pelo nome totalmente qualificado da classe do
# evento, então os cinco serviços precisam das MESMAS classes em com.tcc.saga.
# Ao mesmo tempo, cada serviço é um projeto Maven independente que se constrói
# sozinho no seu Dockerfile. As cópias ficam versionadas no git justamente para
# que "docker compose up --build" funcione sem rodar este script antes.
#
# Rode este script sempre que alterar qualquer coisa em shared/ e faça commit
# do resultado.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ORIGEM="$RAIZ/shared/java/com/tcc/saga"

# O serviço de auditoria só consome eventos: não injeta falha nem executa etapas.
declare -A PACOTES=(
  [travel]="event experimento"
  [approval]="event experimento recuperacao"
  [booking]="event experimento recuperacao"
  [payment]="event experimento recuperacao"
  [audit]="event"
)

for servico in "${!PACOTES[@]}"; do
  destino="$RAIZ/$servico/src/main/java/com/tcc/saga"
  if [ ! -d "$RAIZ/$servico/src/main/java" ]; then
    echo "aviso: serviço '$servico' não encontrado, pulando"
    continue
  fi
  rm -rf "$destino"
  mkdir -p "$destino"
  for pacote in ${PACOTES[$servico]}; do
    cp -r "$ORIGEM/$pacote" "$destino/"
  done
  echo "sincronizado: $servico  <- ${PACOTES[$servico]}"
done

echo
echo "Contrato compartilhado sincronizado. Lembre de commitar as cópias."
