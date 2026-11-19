package com.academia.gestao.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String timezone,
        List<String> corsOrigins,
        Jwt jwt,
        Admin admin,
        Reconhecimento reconhecimento,
        Asaas asaas,
        Academia academia) {

    public record Jwt(String secret, long expirationHours) {}

    public record Admin(String nome, String email, String password) {}

    public record Reconhecimento(double distanciaMaxima) {}

    /**
     * Integração com o Asaas. Sem apiKey a aba Cobranças avisa que falta configurar.
     * baseUrl: sandbox (https://api-sandbox.asaas.com/v3) ou produção (https://api.asaas.com/v3).
     * webhookToken: o mesmo "token de autenticação" cadastrado no webhook do painel do Asaas.
     */
    public record Asaas(String apiKey, String baseUrl, String webhookToken) {}

    /** Identidade da academia desta instalação (usada em textos enviados ao aluno, como as cobranças). */
    public record Academia(String nome) {}

    /** Nome da academia, ou "Academia" quando não configurado. */
    public String nomeAcademia() {
        return academia != null && academia.nome() != null && !academia.nome().isBlank()
                ? academia.nome().trim() : "Academia";
    }
}
