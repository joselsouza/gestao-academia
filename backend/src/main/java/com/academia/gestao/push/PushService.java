package com.academia.gestao.push;

import com.academia.gestao.common.NegocioException;
import com.academia.gestao.config.AppProperties;
import com.academia.gestao.push.WebPush.ChavesVapid;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Notificações push (Web Push). Guarda os aparelhos inscritos e envia as mensagens em segundo
 * plano, sempre DEPOIS que a operação que gerou o aviso foi gravada no banco.
 */
@Slf4j
@Service
public class PushService {

    /** Endpoints aceitos: só os serviços de push dos navegadores (evita usar o servidor como proxy). */
    private static final List<String> SERVICOS = List.of(
            "fcm.googleapis.com", "updates.push.services.mozilla.com", "push.services.mozilla.com",
            "web.push.apple.com", "notify.windows.com", "push.apple.com");

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final String contato;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService fila = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "push");
        t.setDaemon(true);
        return t;
    });
    private volatile ChavesVapid chaves;

    public PushService(JdbcTemplate jdbc, Clock clock, AppProperties props) {
        this.jdbc = jdbc;
        this.clock = clock;
        // "sub" do VAPID: endereço do site da academia (exigido pela Apple).
        this.contato = props.corsOrigins() == null ? "https://localhost" : props.corsOrigins().stream()
                .filter(o -> o.startsWith("https://")).findFirst().orElse("https://localhost");
    }

    @PreDestroy
    void encerrar() {
        fila.shutdown();
    }

    /** Chave pública VAPID (gera e guarda o par na primeira vez). */
    public String chavePublica() {
        return chaves().publicaBase64();
    }

    ChavesVapid chaves() {
        ChavesVapid c = chaves;
        if (c != null) {
            return c;
        }
        synchronized (this) {
            if (chaves == null) {
                List<ChavesVapid> salvas = jdbc.query("SELECT publica, privada FROM push_chaves WHERE id = 1",
                        (rs, i) -> WebPush.carregarChaves(rs.getString(1), rs.getString(2)));
                if (salvas.isEmpty()) {
                    ChavesVapid nova = WebPush.gerarChaves();
                    jdbc.update("INSERT INTO push_chaves (id, publica, privada) VALUES (1, ?, ?) ON CONFLICT (id) DO NOTHING",
                            nova.publicaBase64(), nova.privadaBase64());
                    salvas = jdbc.query("SELECT publica, privada FROM push_chaves WHERE id = 1",
                            (rs, i) -> WebPush.carregarChaves(rs.getString(1), rs.getString(2)));
                }
                chaves = salvas.get(0);
            }
            return chaves;
        }
    }

    // ------------------------------------------------------------------ inscrições

    public record Inscricao(String endpoint, String p256dh, String auth) {}

    /** Liga o aparelho a um usuário da equipe (alunoId nulo) ou a um aluno (usuarioId nulo). */
    public void inscrever(Inscricao i, Long usuarioId, Long alunoId) {
        validar(i);
        jdbc.update("""
                INSERT INTO push_inscricoes (endpoint, p256dh, auth, usuario_id, aluno_id) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (endpoint) DO UPDATE SET p256dh = EXCLUDED.p256dh, auth = EXCLUDED.auth,
                    usuario_id = EXCLUDED.usuario_id, aluno_id = EXCLUDED.aluno_id""",
                i.endpoint(), i.p256dh(), i.auth(), usuarioId, alunoId);
    }

    /** Remove o aparelho, só se ele for de quem está pedindo. */
    public void remover(String endpoint, Long usuarioId, Long alunoId) {
        jdbc.update("DELETE FROM push_inscricoes WHERE endpoint = ? AND (usuario_id = ? OR aluno_id = ?)",
                endpoint, usuarioId, alunoId);
    }

    private static void validar(Inscricao i) {
        if (i == null || i.endpoint() == null || i.p256dh() == null || i.auth() == null) {
            throw new NegocioException("Inscrição incompleta");
        }
        URI uri;
        try {
            uri = URI.create(i.endpoint());
        } catch (IllegalArgumentException e) {
            throw new NegocioException("Endereço de notificação inválido");
        }
        String host = uri.getHost() == null ? "" : uri.getHost();
        boolean conhecido = SERVICOS.stream().anyMatch(s -> host.equals(s) || host.endsWith("." + s));
        if (!"https".equals(uri.getScheme()) || !conhecido) {
            throw new NegocioException("Serviço de notificação não reconhecido");
        }
        try {
            WebPush.chavePublica(WebPush.b64d(i.p256dh()));
            if (WebPush.b64d(i.auth()).length != 16) {
                throw new NegocioException("Chave auth inválida");
            }
        } catch (java.security.GeneralSecurityException | IllegalArgumentException e) {
            throw new NegocioException("Chaves da inscrição inválidas");
        }
    }

    // ------------------------------------------------------------------ envio

    /** Para todos os aparelhos do aluno. */
    public void paraAluno(Long alunoId, Notificacao n) {
        agendar(() -> enviar(destinos("aluno_id = ?", alunoId), n));
    }

    /** Para todos os aparelhos da equipe (usuários ativos). */
    public void paraEquipe(Notificacao n) {
        agendar(() -> enviar(destinos("usuario_id IN (SELECT id FROM usuarios WHERE ativo)"), n));
    }

    /** Para os aparelhos de um usuário da equipe (ex.: teste). */
    public void paraUsuario(Long usuarioId, Notificacao n) {
        agendar(() -> enviar(destinos("usuario_id = ?", usuarioId), n));
    }

    /**
     * Registra que o aviso {@code chave} foi dado; devolve false se já tinha sido.
     * Roda na transação de quem chamou: se ela for desfeita, o registro também é.
     */
    public boolean primeiraVez(String chave) {
        return jdbc.update("INSERT INTO push_avisos_enviados (chave) VALUES (?) ON CONFLICT (chave) DO NOTHING", chave) == 1;
    }

    private record Destino(long id, String endpoint, String p256dh, String auth) {}

    private List<Destino> destinos(String filtro, Object... args) {
        return jdbc.query("SELECT id, endpoint, p256dh, auth FROM push_inscricoes WHERE " + filtro,
                (rs, i) -> new Destino(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)), args);
    }

    /** Depois do commit (se houver transação), na fila de envio. */
    private void agendar(Runnable tarefa) {
        Runnable seguro = () -> {
            try {
                tarefa.run();
            } catch (RuntimeException e) {
                log.warn("Falha ao enviar notificações push", e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fila.execute(seguro);
                }
            });
        } else {
            fila.execute(seguro);
        }
    }

    private void enviar(List<Destino> destinos, Notificacao n) {
        if (destinos.isEmpty()) {
            return;
        }
        byte[] mensagem = n.json().getBytes(StandardCharsets.UTF_8);
        ChavesVapid vapid = chaves();
        for (Destino d : destinos) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(d.endpoint()))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", WebPush.autorizacao(d.endpoint(), vapid, contato, clock.instant()))
                        .header("Content-Encoding", "aes128gcm")
                        .header("Content-Type", "application/octet-stream")
                        .header("TTL", String.valueOf(Duration.ofDays(2).toSeconds()))
                        .header("Urgency", "high")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(WebPush.cifrar(mensagem, d.p256dh(), d.auth())))
                        .build();
                HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 404 || r.statusCode() == 410
                        || (r.statusCode() == 400 && r.body() != null && r.body().contains("BadWebPushToken"))) {
                    // Aparelho desinstalou, desativou ou a inscrição não vale mais: some da lista.
                    jdbc.update("DELETE FROM push_inscricoes WHERE id = ?", d.id());
                } else if (r.statusCode() >= 300) {
                    log.warn("Push recusado ({}) por {}: {}", r.statusCode(), URI.create(d.endpoint()).getHost(), r.body());
                } else {
                    jdbc.update("UPDATE push_inscricoes SET ultimo_envio = now() WHERE id = ?", d.id());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("Falha ao enviar push para {}", URI.create(d.endpoint()).getHost(), e);
            }
        }
    }
}
