#!/usr/bin/env python3
"""
Consolida as métricas de todas as rodadas em um único CSV.

Cada rodada da matriz gera um `<runId>-metricas.json`. Este script junta todos
em `resultados/consolidado.csv`, com uma linha por rodada e os fatores
experimentais já separados em colunas — que é o formato que a análise
estatística do capítulo de resultados espera.

Também calcula, por rodada, o intervalo de confiança de 95% do tempo médio de
conclusão via bootstrap BCa (bias-corrected and accelerated), a partir dos
tempos de conclusão individuais reconstruídos de `<runId>.csv`. Preferido a um
teste-t clássico porque não assume que os tempos seguem distribuição normal —
ver Isolamento-experimento.md, item 10 (HUF, 2025, p. 133), e a seção
"Controle de isolamento experimental" no README. Sem dependências externas de
propósito (nem numpy nem scipy): só a biblioteca padrão do Python.

Uso:
    python3 scripts/consolidar-resultados.py [diretorio-de-resultados] [--sem-ic]
"""

import csv
import json
import math
import random
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
    "tempo_conclusao_ic95_inferior_ms",
    "tempo_conclusao_ic95_superior_ms",
    "tempo_conclusao_n_amostras",
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
    "estabilizou",
]

TERMINAIS = {"SAGA_CONCLUIDA", "SAGA_COMPENSADA", "SAGA_ABORTADA", "SAGA_REJEITADA"}

BOOTSTRAP_REPETICOES = 1000
BOOTSTRAP_SEMENTE = 42


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


def tempos_conclusao(caminho_csv):
    """Reconstrói, por saga, o tempo entre SAGA_INICIADA e SAGA_CONCLUIDA.

    Mesma semântica de RegistroAuditoriaJpaRepository.resumirSagas (a query
    que gera tempo_medio_conclusao_ms no serviço audit), refeita aqui a partir
    do CSV bruto porque a API de métricas só expõe o agregado, não a amostra.
    """
    if not caminho_csv.exists():
        return []

    inicio = {}
    fim = {}
    concluida = set()
    with caminho_csv.open(newline="", encoding="utf-8") as f:
        for linha in csv.DictReader(f):
            sid = linha.get("solicitacao_id")
            acao = linha.get("acao")
            bruto = linha.get("timestamp_millis")
            if not sid or not acao or not bruto:
                continue
            ts = int(bruto)
            if acao == "SAGA_INICIADA":
                if sid not in inicio or ts < inicio[sid]:
                    inicio[sid] = ts
            elif acao in TERMINAIS:
                if sid not in fim or ts > fim[sid]:
                    fim[sid] = ts
                if acao == "SAGA_CONCLUIDA":
                    concluida.add(sid)

    return [
        float(fim[sid] - inicio[sid])
        for sid in concluida
        if sid in inicio and sid in fim and fim[sid] >= inicio[sid]
    ]


def _phi(x):
    """CDF da normal padrão."""
    return 0.5 * (1.0 + math.erf(x / math.sqrt(2)))


def _phi_inv(p):
    """Inversa da CDF da normal padrão (aproximação racional de Acklam)."""
    if p <= 0.0:
        return -math.inf
    if p >= 1.0:
        return math.inf

    a = [-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02,
         1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00]
    b = [-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02,
         6.680131188771972e+01, -1.328068155288572e+01]
    c = [-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00,
         -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00]
    d = [7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00,
         3.754408661907416e+00]
    p_baixo, p_alto = 0.02425, 1 - 0.02425

    if p < p_baixo:
        q = math.sqrt(-2 * math.log(p))
        return (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / \
               ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1)
    if p <= p_alto:
        q = p - 0.5
        r = q * q
        return (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q / \
               (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1)
    q = math.sqrt(-2 * math.log(1 - p))
    return -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / \
            ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1)


def bootstrap_bca(amostras, confianca=0.95, repeticoes=BOOTSTRAP_REPETICOES, semente=BOOTSTRAP_SEMENTE):
    """IC bootstrap BCa (Efron; Tibshirani, 1986) para a média da amostra."""
    n = len(amostras)
    if n < 2:
        return None

    rng = random.Random(semente)
    theta_hat = sum(amostras) / n

    reamostras = []
    for _ in range(repeticoes):
        soma = 0.0
        for _ in range(n):
            soma += amostras[rng.randrange(n)]
        reamostras.append(soma / n)
    reamostras.sort()

    limite = 1.0 / (2 * repeticoes)
    proporcao = sum(1 for m in reamostras if m < theta_hat) / repeticoes
    proporcao = min(max(proporcao, limite), 1 - limite)
    z0 = _phi_inv(proporcao)

    total = sum(amostras)
    medias_jackknife = [(total - x) / (n - 1) for x in amostras]
    media_jackknife = sum(medias_jackknife) / n
    numerador = sum((media_jackknife - m) ** 3 for m in medias_jackknife)
    denominador = 6 * (sum((media_jackknife - m) ** 2 for m in medias_jackknife) ** 1.5)
    a = numerador / denominador if denominador != 0 else 0.0

    alfa = (1 - confianca) / 2

    def ajustar(z_alfa):
        z = z0 + (z0 + z_alfa) / (1 - a * (z0 + z_alfa))
        return min(max(_phi(z), 0.0), 1.0)

    p_inferior = ajustar(_phi_inv(alfa))
    p_superior = ajustar(_phi_inv(1 - alfa))

    idx_inferior = max(0, min(repeticoes - 1, round(p_inferior * (repeticoes - 1))))
    idx_superior = max(0, min(repeticoes - 1, round(p_superior * (repeticoes - 1))))

    return reamostras[idx_inferior], reamostras[idx_superior]


def estabilizou(caminho_marcador):
    if not caminho_marcador.exists():
        return ""  # rodada anterior à introdução do marcador: desconhecido, não "sim"
    return caminho_marcador.read_text(encoding="utf-8").strip()


def linha(caminho_metricas, calcular_ic):
    dados = json.loads(caminho_metricas.read_text())
    run_id = dados.get("runId") or caminho_metricas.stem.replace("-metricas", "")
    registro = {"run_id": run_id, **fatores(run_id)}

    ic_inferior, ic_superior, n_amostras = "", "", 0
    if calcular_ic:
        amostras = tempos_conclusao(caminho_metricas.parent / f"{run_id}.csv")
        n_amostras = len(amostras)
        intervalo = bootstrap_bca(amostras)
        if intervalo is not None:
            ic_inferior, ic_superior = round(intervalo[0], 2), round(intervalo[1], 2)

    registro.update({
        "total_sagas": dados.get("totalSagas", 0),
        "sagas_concluidas": dados.get("sagasConcluidas", 0),
        "sagas_compensadas": dados.get("sagasCompensadas", 0),
        "sagas_abortadas": dados.get("sagasAbortadas", 0),
        "sagas_rejeitadas": dados.get("sagasRejeitadas", 0),
        "sagas_em_andamento": dados.get("sagasEmAndamento", 0),
        "taxa_sucesso": round(dados.get("taxaSucesso", 0), 4),
        "tempo_medio_conclusao_ms": round(dados.get("tempoMedioConclusaoMs", 0), 2),
        "tempo_conclusao_ic95_inferior_ms": ic_inferior,
        "tempo_conclusao_ic95_superior_ms": ic_superior,
        "tempo_conclusao_n_amostras": n_amostras,
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
        "estabilizou": estabilizou(caminho_metricas.parent / f"{run_id}.estabilizou"),
    })
    return registro


def main():
    argumentos = sys.argv[1:]
    calcular_ic = "--sem-ic" not in argumentos
    posicionais = [a for a in argumentos if not a.startswith("--")]

    diretorio = Path(posicionais[0] if posicionais else "resultados")
    arquivos = sorted(diretorio.glob("*-metricas.json"))

    if not arquivos:
        print(f"nenhum arquivo *-metricas.json encontrado em {diretorio}")
        return 1

    if calcular_ic:
        print(f"calculando IC95% via bootstrap BCa ({BOOTSTRAP_REPETICOES} reamostragens/rodada)...")

    saida = diretorio / "consolidado.csv"
    with saida.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=COLUNAS)
        writer.writeheader()
        for arquivo in arquivos:
            try:
                writer.writerow(linha(arquivo, calcular_ic))
            except (json.JSONDecodeError, KeyError) as e:
                print(f"aviso: ignorando {arquivo.name} ({e})")

    print(f"{len(arquivos)} rodadas consolidadas em {saida}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
