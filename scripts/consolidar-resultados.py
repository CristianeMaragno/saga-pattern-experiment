#!/usr/bin/env python3
"""
Consolida as métricas de todas as rodadas em um único CSV.

Cada rodada da matriz gera um `<runId>-metricas.json`. Este script junta todos
em `resultados/consolidado.csv`, com uma linha por rodada e os fatores
experimentais já separados em colunas — que é o formato que a análise
estatística do capítulo de resultados espera.

Uso:
    python3 scripts/consolidar-resultados.py [diretorio-de-resultados]
"""

import csv
import json
import sys
from pathlib import Path

COLUNAS = [
    # Fatores experimentais
    "run_id",
    "estrategia",
    "cenario_falha",
    "posicao_falha",
    "carga",
    # Métricas
    "total_sagas",
    "sagas_concluidas",
    "sagas_compensadas",
    "sagas_abortadas",
    "sagas_rejeitadas",
    "sagas_em_andamento",
    "taxa_sucesso",
    "tempo_medio_conclusao_ms",
    "tempo_medio_recuperacao_ms",
    "tempo_ate_consistencia_eventual_ms",
    "total_compensacoes",
    "total_retries",
    "total_eventos",
    "sagas_com_falha",
    "sagas_recuperadas_sem_compensacao",
    "percentual_recuperadas_sem_compensacao",
    "throughput_sagas_por_segundo",
    "duracao_rodada_ms",
]


def fatores(run_id):
    """run_id tem o formato ESTRATEGIA_CENARIO_POSICAO_CARGA."""
    partes = run_id.split("_")
    if len(partes) != 4:
        return {"estrategia": "", "cenario_falha": "", "posicao_falha": "", "carga": ""}
    return {
        "estrategia": partes[0],
        "cenario_falha": partes[1],
        "posicao_falha": partes[2] if partes[1] != "NONE" else "",
        "carga": partes[3],
    }


def linha(caminho):
    dados = json.loads(caminho.read_text())
    run_id = dados.get("runId") or caminho.stem.replace("-metricas", "")
    registro = {"run_id": run_id, **fatores(run_id)}
    registro.update({
        "total_sagas": dados.get("totalSagas", 0),
        "sagas_concluidas": dados.get("sagasConcluidas", 0),
        "sagas_compensadas": dados.get("sagasCompensadas", 0),
        "sagas_abortadas": dados.get("sagasAbortadas", 0),
        "sagas_rejeitadas": dados.get("sagasRejeitadas", 0),
        "sagas_em_andamento": dados.get("sagasEmAndamento", 0),
        "taxa_sucesso": round(dados.get("taxaSucesso", 0), 4),
        "tempo_medio_conclusao_ms": round(dados.get("tempoMedioConclusaoMs", 0), 2),
        "tempo_medio_recuperacao_ms": round(dados.get("tempoMedioRecuperacaoMs", 0), 2),
        "tempo_ate_consistencia_eventual_ms": round(dados.get("tempoAteConsistenciaEventualMs", 0), 2),
        "total_compensacoes": dados.get("totalCompensacoes", 0),
        "total_retries": dados.get("totalRetries", 0),
        "total_eventos": dados.get("totalEventos", 0),
        "sagas_com_falha": dados.get("sagasComFalha", 0),
        "sagas_recuperadas_sem_compensacao": dados.get("sagasRecuperadasSemCompensacao", 0),
        "percentual_recuperadas_sem_compensacao": round(
            dados.get("percentualRecuperadasSemCompensacao", 0), 2),
        "throughput_sagas_por_segundo": round(dados.get("throughputSagasPorSegundo", 0), 4),
        "duracao_rodada_ms": dados.get("duracaoRodadaMs", 0),
    })
    return registro


def main():
    diretorio = Path(sys.argv[1] if len(sys.argv) > 1 else "resultados")
    arquivos = sorted(diretorio.glob("*-metricas.json"))

    if not arquivos:
        print(f"nenhum arquivo *-metricas.json encontrado em {diretorio}")
        return 1

    saida = diretorio / "consolidado.csv"
    with saida.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=COLUNAS)
        writer.writeheader()
        for arquivo in arquivos:
            try:
                writer.writerow(linha(arquivo))
            except (json.JSONDecodeError, KeyError) as e:
                print(f"aviso: ignorando {arquivo.name} ({e})")

    print(f"{len(arquivos)} rodadas consolidadas em {saida}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
