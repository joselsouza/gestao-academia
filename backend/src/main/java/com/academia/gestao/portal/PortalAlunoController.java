package com.academia.gestao.portal;

import com.academia.gestao.portal.PortalDtos.*;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * API do portal do aluno. Só tokens com perfil ALUNO entram aqui (ver SecurityConfig), e o
 * aluno é sempre o do token (claim "aid"): não há como pedir dados de outro aluno.
 */
@RestController
@RequestMapping("/api/aluno")
@RequiredArgsConstructor
public class PortalAlunoController {

    private final PortalAlunoService service;

    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return service.login(req);
    }

    @GetMapping("/me")
    public MeuPerfil perfil(@AuthenticationPrincipal Jwt jwt) {
        return service.perfil(alunoId(jwt));
    }

    @PutMapping("/me")
    public MeuPerfil atualizar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AtualizarPerfil req) {
        return service.atualizarPerfil(alunoId(jwt), req);
    }

    @PutMapping("/me/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trocarSenha(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TrocarSenha req) {
        service.trocarSenha(alunoId(jwt), req);
    }

    @GetMapping("/me/mensalidades")
    public List<MinhaMensalidade> mensalidades(@AuthenticationPrincipal Jwt jwt) {
        return service.mensalidades(alunoId(jwt));
    }

    @GetMapping("/me/cobrancas")
    public List<MinhaCobranca> cobrancas(@AuthenticationPrincipal Jwt jwt) {
        return service.cobrancas(alunoId(jwt));
    }

    static Long alunoId(Jwt jwt) {
        return ((Number) jwt.getClaims().get("aid")).longValue();
    }
}
