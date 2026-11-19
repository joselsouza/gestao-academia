package com.academia.gestao.keepalive;

import java.time.OffsetDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final KeepAliveService keepAlive;

    /** Público, sem tocar no banco: usado por monitores de uptime. */
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    /** Ping manual (autenticado) — útil para testar a conexão com o Supabase. */
    @PostMapping("/api/keep-alive/ping")
    public Map<String, OffsetDateTime> ping() {
        return Map.of("pingedAt", keepAlive.ping("manual"));
    }
}
