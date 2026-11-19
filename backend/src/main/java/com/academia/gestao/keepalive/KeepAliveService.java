package com.academia.gestao.keepalive;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Ping diário no banco para o projeto Supabase (plano free) não ser pausado por inatividade.
 * Redundância: o workflow .github/workflows/supabase-keep-alive.yml faz o mesmo via API REST,
 * cobrindo os dias em que o back-end estiver desligado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeepAliveService {

    private final JdbcTemplate jdbc;

    @Scheduled(cron = "${app.keep-alive.cron}", zone = "${app.timezone}")
    public void pingDiario() {
        try {
            OffsetDateTime quando = ping("backend");
            log.info("Keep-alive Supabase OK em {}", quando);
        } catch (Exception e) {
            log.error("Falha no keep-alive do Supabase", e);
        }
    }

    public OffsetDateTime ping(String origem) {
        return jdbc.queryForObject("SELECT keep_alive_ping(?)", OffsetDateTime.class, origem);
    }

    public OffsetDateTime ultimoPing() {
        return jdbc.queryForObject("SELECT max(pinged_at) FROM keep_alive", OffsetDateTime.class);
    }
}
