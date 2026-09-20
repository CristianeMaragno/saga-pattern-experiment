#!/usr/bin/env python3
"""
Consolida a varredura de calibração de carga (run-experimento-carga.sh) numa
única tabela ordenada por volume de usuários.

Ao contrário de consolidar-resultados.py (que separa run_id em estratégia/
cenário/posição/carga), aqui o run_id tem o formato `CARGA_<n>` — um único
fator variando. A coluna importante que este script acrescenta e que
consolidado.csv não tem é a variação percentual de cada métrica em relação ao
ponto de carga anterior: é o número que sustenta a alegação de "estabilização"
na monografia (ex.: "a partir de 1000 usuários, throughput varia menos de
5% e tempo médio de conclusão menos de 3% a cada aumento de carga").

Uso:
    python3 scripts/consolidar-carga.py [resultados/calibracao-carga]
"""

import csv
import json
import re
import sys
from pathlib import Path

COLUNAS = [
    "run_id",
    "carga",
    "total_sagas",
    "taxa_sucesso",
    "taxa_sucesso_variacao_pct",
    "tempo_medio_conclusao_ms",
    "tempo_medio_conclusao_variacao_pct",
    "tempo_medio_recuperacao_ms",
    "tempo_ate_consistencia_eventual_ms",
    "throughput_sagas_por_segundo",
    "throughput_variacao_pct",
    "total_compensacoes",
    "total_retries",
    "total_eventos",
    "duracao_rodada_ms",
    "estabilizou",
]

PADRAO_RUN_ID = re.compile(r"^CARGA_0*(\d+)$")


def carga_do_run_id(run_id):
    m = PADRAO_RUN_ID.match(run_id)
    return int(m.group(1)) if m else None


def estabilizou(caminho_marcador):
    if not caminho_marcador.exists():
        return ""
    return caminho_marcador.read_text(encoding="utf-8").strip()


def variacao_pct(atual, anterior):
    """Variação percentual de `atual` em relação a `anterior`, ou "" se não dá pra calcular."""
    if anterior in (None, 0):
        return ""
    return round(100.0 * (atual - anterior) / abs(anterior), 2)


def linha(caminho_metricas):
    dados = json.loads(caminho_metricas.read_text())
    run_id = dados.get("runId") or caminho_metricas.stem.replace("-metricas", "")
    carga = carga_do_run_id(run_id)

    return {
        "run_id": run_id,
        "carga": carga if carga is not None else "",
        "total_sagas": dados.get("totalSagas", 0),
        "taxa_sucesso": round(dados.get("taxaSucesso", 0), 4),
        "tempo_medio_conclusao_ms": round(dados.get("tempoMedioConclusaoMs", 0), 2),
        "tempo_medio_recuperacao_ms": round(dados.get("tempoMedioRecuperacaoMs", 0), 2),
        "tempo_ate_consistencia_eventual_ms": round(dados.get("tempoAteConsistenciaEventualMs", 0), 2),
        "throughput_sagas_por_segundo": round(dados.get("throughputSagasPorSegundo", 0), 4),
        "total_compensacoes": dados.get("totalCompensacoes", 0),
        "total_retries": dados.get("totalRetries", 0),
        "total_eventos": dados.get("totalEventos", 0),
        "duracao_rodada_ms": dados.get("duracaoRodadaMs", 0),
        "estabilizou": estabilizou(caminho_metricas.parent / f"{run_id}.estabilizou"),
    }


def main():
    argumentos = sys.argv[1:]
    diretorio = Path(argumentos[0] if argumentos else "resultados/calibracao-carga")
    arquivos = sorted(diretorio.glob("CARGA_*-metricas.json"))

    if not arquivos:
        print(f"nenhum arquivo CARGA_*-metricas.json encontrado em {diretorio}")
        return 1

    linhas = []
    for arquivo in arquivos:
        try:
            linhas.append(linha(arquivo))
        except (json.JSONDecodeError, KeyError) as e:
            print(f"aviso: ignorando {arquivo.name} ({e})")

    # Ordena numericamente por carga (o glob já ordena por string, mas o
    # zero-padding do run_id só garante isso se todas as rodadas usarem a
    # mesma largura — ordenar de novo aqui não depende dessa suposição).
    linhas.sort(key=lambda l: l["carga"] if isinstance(l["carga"], int) else float("inf"))

    anterior = None
    for l in linhas:
        if anterior is not None:
            l["taxa_sucesso_variacao_pct"] = variacao_pct(l["taxa_sucesso"], anterior["taxa_sucesso"])
            l["tempo_medio_conclusao_variacao_pct"] = variacao_pct(
                l["tempo_medio_conclusao_ms"], anterior["tempo_medio_conclusao_ms"])
            l["throughput_variacao_pct"] = variacao_pct(
                l["throughput_sagas_por_segundo"], anterior["throughput_sagas_por_segundo"])
        else:
            l["taxa_sucesso_variacao_pct"] = ""
            l["tempo_medio_conclusao_variacao_pct"] = ""
            l["throughput_variacao_pct"] = ""
        anterior = l

    saida = diretorio / "consolidado-carga.csv"
    with saida.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=COLUNAS)
        writer.writeheader()
        writer.writerows(linhas)

    print(f"{len(linhas)} pontos de carga consolidados em {saida}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
