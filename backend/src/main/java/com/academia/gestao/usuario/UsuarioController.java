package com.academia.gestao.usuario;

import com.academia.gestao.common.NaoEncontradoException;
import com.academia.gestao.common.NegocioException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Usuários de acesso ao sistema. Só perfil ADMIN acessa (ver SecurityConfig). */
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private static final String PERFIS = "ADMIN|INSTRUTOR";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    public record UsuarioResponse(Long id, String nome, String email, String perfil, boolean ativo,
                                  OffsetDateTime criadoEm) {
        static UsuarioResponse of(Usuario u) {
            return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(), u.isAtivo(), u.getCriadoEm());
        }
    }

    public record NovoUsuario(
            @NotBlank @Size(max = 120) String nome,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(min = 6, max = 72, message = "deve ter entre 6 e 72 caracteres") String senha,
            @Pattern(regexp = PERFIS) String perfil) {}

    /** Senha opcional: em branco mantém a atual. */
    public record AtualizarUsuario(
            @NotBlank @Size(max = 120) String nome,
            @Pattern(regexp = PERFIS) String perfil,
            Boolean ativo,
            @Size(min = 6, max = 72, message = "deve ter entre 6 e 72 caracteres") String senha) {}

    @GetMapping
    public List<UsuarioResponse> listar() {
        return usuarios.findAll().stream()
                .sorted((a, b) -> a.getNome().compareToIgnoreCase(b.getNome()))
                .map(UsuarioResponse::of)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UsuarioResponse criar(@Valid @RequestBody NovoUsuario req) {
        String email = req.email().trim().toLowerCase();
        if (usuarios.findByEmailIgnoreCase(email).isPresent()) {
            throw new NegocioException("Já existe um usuário com o e-mail " + email + ".");
        }
        Usuario u = new Usuario();
        u.setNome(req.nome().trim());
        u.setEmail(email);
        u.setSenhaHash(encoder.encode(req.senha()));
        u.setPerfil(req.perfil() == null ? "INSTRUTOR" : req.perfil());
        return UsuarioResponse.of(usuarios.saveAndFlush(u));
    }

    @PutMapping("/{id}")
    @Transactional
    public UsuarioResponse atualizar(@PathVariable Long id, @Valid @RequestBody AtualizarUsuario req,
                                     @AuthenticationPrincipal Jwt jwt) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> new NaoEncontradoException("Usuário", id));
        boolean ehVoceMesmo = u.getEmail().equalsIgnoreCase(jwt.getSubject());
        if (ehVoceMesmo && Boolean.FALSE.equals(req.ativo())) {
            throw new NegocioException("Você não pode desativar o seu próprio usuário.");
        }
        if (ehVoceMesmo && req.perfil() != null && !"ADMIN".equals(req.perfil())) {
            throw new NegocioException("Você não pode remover o seu próprio perfil de administrador.");
        }
        u.setNome(req.nome().trim());
        if (req.perfil() != null) {
            u.setPerfil(req.perfil());
        }
        if (req.ativo() != null) {
            u.setAtivo(req.ativo());
        }
        if (req.senha() != null && !req.senha().isBlank()) {
            u.setSenhaHash(encoder.encode(req.senha()));
        }
        return UsuarioResponse.of(u);
    }
}
