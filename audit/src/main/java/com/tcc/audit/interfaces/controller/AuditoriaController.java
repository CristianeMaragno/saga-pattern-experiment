package com.tcc.audit.interfaces.controller;

import com.tcc.audit.application.dto.MetricasResponseDTO;
import com.tcc.audit.application.usecase.AuditoriaUseCase;
import com.tcc.audit.infrastructure.entity.RegistroAuditoriaJpaEntity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints usados pelo script da matriz de experimentos ao final de cada rodada.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private static final String CABECALHO_CSV =
            "run_id,solicitacao_id,servico,etapa,acao,tentativa,estrategia,cenario_falha,posicao_falha,"
                    + "timestamp_millis,mensagem";

    private final AuditoriaUseCase auditoriaUseCase;

    /**
     * GET /api/v1/auditoria/metricas?runId=... — métricas agregadas da rodada.
     */
    @GetMapping("/metricas")
    public ResponseEntity<MetricasResponseDTO> metricas(@RequestParam String runId) {
        return ResponseEntity.ok(auditoriaUseCase.calcularMetricas(runId));
    }

    /**
     * GET /api/v1/auditoria/export?runId=... — linha do tempo completa em CSV,
     * pronta para a análise estatística.
     */
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> exportar(@RequestParam String runId) {
        List<RegistroAuditoriaJpaEntity> registros = auditoriaUseCase.listarPorRodada(runId);

        StringBuilder csv = new StringBuilder(CABECALHO_CSV).append('\n');
        for (RegistroAuditoriaJpaEntity r : registros) {
            csv.append(escapar(r.getRunId())).append(',')
                    .append(r.getSolicitacaoId()).append(',')
                    .append(escapar(r.getServico())).append(',')
                    .append(escapar(r.getEtapa())).append(',')
                    .append(escapar(r.getAcao())).append(',')
                    .append(r.getTentativa()).append(',')
                    .append(escapar(r.getEstrategia())).append(',')
                    .append(escapar(r.getCenarioFalha())).append(',')
                    .append(escapar(r.getPosicaoFalha())).append(',')
                    .append(r.getTimestampMillis()).append(',')
                    .append(escapar(r.getMensagem())).append('\n');
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + runId + ".csv\"")
                .body(csv.toString());
    }

    /**
     * DELETE /api/v1/auditoria?runId=... — descarta os dados de uma rodada,
     * para reexecutá-la do zero.
     */
    @DeleteMapping
    public ResponseEntity<Void> limpar(@RequestParam String runId) {
        int removidos = auditoriaUseCase.limparRodada(runId);
        log.info("Rodada {} limpa: {} registros removidos", runId, removidos);
        return ResponseEntity.noContent().build();
    }

    /**
     * Escapa o valor conforme RFC 4180: campos com vírgula, aspas ou quebra de
     * linha (mensagens de erro têm todos os três) precisam vir entre aspas.
     */
    private static String escapar(String valor) {
        if (valor == null) {
            return "";
        }
        if (valor.contains(",") || valor.contains("\"") || valor.contains("\n") || valor.contains("\r")) {
            return '"' + valor.replace("\"", "\"\"") + '"';
        }
        return valor;
    }
}
