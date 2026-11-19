package com.academia.gestao.usuario;

import com.academia.gestao.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Garante que o administrador configurado exista (a senha nunca vai em texto puro para o banco). */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private final UsuarioRepository repo;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var admin = props.admin();
        if (repo.findByEmailIgnoreCase(admin.email()).isPresent()) {
            return;
        }
        if (admin.password() == null || admin.password().isBlank()) {
            log.warn("Administrador {} não existe e APP_ADMIN_PASSWORD não foi definida: nada foi criado.", admin.email());
            return;
        }
        Usuario u = new Usuario();
        u.setNome(admin.nome());
        u.setEmail(admin.email().toLowerCase());
        u.setSenhaHash(encoder.encode(admin.password()));
        u.setPerfil("ADMIN");
        repo.save(u);
        log.info("Usuário administrador criado: {}", u.getEmail());
    }
}
