package com.academia.gestao.portal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Freio contra adivinhação de senha no portal: depois de {@value #MAX_FALHAS} erros seguidos
 * para o mesmo CPF, o login desse CPF fica bloqueado por {@link #BLOQUEIO}. Fica em memória
 * (uma instância do back-end por academia), então zera quando o servidor reinicia.
 */
@Component
public class TentativasLogin {

    static final int MAX_FALHAS = 5;
    static final Duration BLOQUEIO = Duration.ofMinutes(15);
    private static final int LIMITE_ENTRADAS = 10_000;

    private record Janela(int falhas, Instant inicio) {}

    private final Map<String, Janela> porCpf = new ConcurrentHashMap<>();
    private final Clock clock;

    public TentativasLogin(Clock clock) {
        this.clock = clock;
    }

    public void verificar(String cpf) {
        Janela j = porCpf.get(cpf);
        if (j != null && j.falhas() >= MAX_FALHAS && !expirou(j)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas com este CPF. Aguarde 15 minutos e tente de novo.");
        }
    }

    public void falhou(String cpf) {
        if (porCpf.size() > LIMITE_ENTRADAS) {
            porCpf.values().removeIf(this::expirou);
        }
        porCpf.compute(cpf, (k, j) -> j == null || expirou(j)
                ? new Janela(1, clock.instant())
                : new Janela(j.falhas() + 1, j.inicio()));
    }

    public void limpar(String cpf) {
        porCpf.remove(cpf);
    }

    private boolean expirou(Janela j) {
        return j.inicio().plus(BLOQUEIO).isBefore(clock.instant());
    }
}
