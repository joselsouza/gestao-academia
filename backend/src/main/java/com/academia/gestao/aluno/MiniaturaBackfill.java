package com.academia.gestao.aluno;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Gera, na subida, as miniaturas que ainda faltam (alunos cadastrados antes da otimização). */
@Slf4j
@Component
@RequiredArgsConstructor
public class MiniaturaBackfill {

    private final JdbcTemplate jdbc;

    @EventListener(ApplicationReadyEvent.class)
    public void gerarFaltantes() {
        try {
            List<Map<String, Object>> pendentes = jdbc.queryForList(
                    "SELECT id, foto FROM alunos WHERE foto IS NOT NULL AND foto_miniatura IS NULL");
            int geradas = 0;
            for (Map<String, Object> linha : pendentes) {
                String mini = Miniaturas.gerar((String) linha.get("foto"));
                if (mini != null) {
                    jdbc.update("UPDATE alunos SET foto_miniatura = ? WHERE id = ?", mini, linha.get("id"));
                    geradas++;
                }
            }
            if (!pendentes.isEmpty()) {
                log.info("Miniaturas geradas: {} de {}", geradas, pendentes.size());
            }
        } catch (Exception e) {
            log.error("Falha ao gerar miniaturas", e);
        }
    }
}
