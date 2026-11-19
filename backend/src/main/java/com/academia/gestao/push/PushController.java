package com.academia.gestao.push;

import com.academia.gestao.push.PushService.Inscricao;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Ativar/desativar notificações no aparelho. Equipe em /api/push/**, aluno em /api/aluno/push/**
 * (cada um só mexe nos próprios aparelhos: o dono vem do token).
 */
@RestController
@RequiredArgsConstructor
public class PushController {

    private final PushService push;

    public record EndpointRequest(String endpoint) {}

    @GetMapping("/api/push/chave")
    public Map<String, String> chave() {
        return Map.of("chavePublica", push.chavePublica());
    }

    // ---------------------------------------------------------------- equipe

    @PostMapping("/api/push/inscricao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void inscreverEquipe(@AuthenticationPrincipal Jwt jwt, @RequestBody Inscricao req) {
        push.inscrever(req, usuarioId(jwt), null);
    }

    @PostMapping("/api/push/inscricao/remover")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerEquipe(@AuthenticationPrincipal Jwt jwt, @RequestBody EndpointRequest req) {
        push.remover(req.endpoint(), usuarioId(jwt), null);
    }

    @PostMapping("/api/push/teste")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void testeEquipe(@AuthenticationPrincipal Jwt jwt) {
        push.paraUsuario(usuarioId(jwt), new Notificacao("Notificações ativadas",
                "Você será avisado quando um pagamento for confirmado.", "/", "teste"));
    }

    // ---------------------------------------------------------------- aluno

    @PostMapping("/api/aluno/push/inscricao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void inscreverAluno(@AuthenticationPrincipal Jwt jwt, @RequestBody Inscricao req) {
        push.inscrever(req, null, alunoId(jwt));
    }

    @PostMapping("/api/aluno/push/inscricao/remover")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerAluno(@AuthenticationPrincipal Jwt jwt, @RequestBody EndpointRequest req) {
        push.remover(req.endpoint(), null, alunoId(jwt));
    }

    @PostMapping("/api/aluno/push/teste")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void testeAluno(@AuthenticationPrincipal Jwt jwt) {
        push.paraAluno(alunoId(jwt), new Notificacao("Notificações ativadas",
                "Você será avisado quando houver uma nova mensalidade ou cobrança.", "/financeiro", "teste"));
    }

    private static Long usuarioId(Jwt jwt) {
        return ((Number) jwt.getClaims().get("uid")).longValue();
    }

    private static Long alunoId(Jwt jwt) {
        return ((Number) jwt.getClaims().get("aid")).longValue();
    }
}
