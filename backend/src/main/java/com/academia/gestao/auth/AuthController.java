package com.academia.gestao.auth;

import com.academia.gestao.security.TokenService;
import com.academia.gestao.usuario.Usuario;
import com.academia.gestao.usuario.UsuarioRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final TokenService tokens;

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String senha) {}

    public record LoginResponse(String token, Instant expiraEm, String nome, String email, String perfil) {}

    public record Me(String nome, String email, String perfil) {}

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        Usuario u = usuarios.findByEmailIgnoreCase(req.email().trim())
                .filter(Usuario::isAtivo)
                .filter(x -> encoder.matches(req.senha(), x.getSenhaHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos"));
        var token = tokens.gerar(u);
        return new LoginResponse(token.valor(), token.expiraEm(), u.getNome(), u.getEmail(), u.getPerfil());
    }

    @GetMapping("/me")
    public Me me(@AuthenticationPrincipal Jwt jwt) {
        return new Me(jwt.getClaimAsString("nome"), jwt.getSubject(), jwt.getClaimAsString("scope"));
    }
}
