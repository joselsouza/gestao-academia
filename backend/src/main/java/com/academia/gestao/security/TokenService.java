package com.academia.gestao.security;

import com.academia.gestao.aluno.Aluno;
import com.academia.gestao.config.AppProperties;
import com.academia.gestao.usuario.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder encoder;
    private final AppProperties props;
    private final Clock clock;

    public record Token(String valor, Instant expiraEm) {}

    /** Token da equipe: scope = ADMIN ou INSTRUTOR. */
    public Token gerar(Usuario usuario) {
        return assinar(base()
                .subject(usuario.getEmail())
                .claim("uid", usuario.getId())
                .claim("nome", usuario.getNome())
                .claim("scope", usuario.getPerfil()));
    }

    /** Token do portal do aluno: scope = ALUNO e o id do aluno em "aid". */
    public Token gerarAluno(Aluno aluno) {
        return assinar(base()
                .subject("aluno:" + aluno.getId())
                .claim("aid", aluno.getId())
                .claim("nome", aluno.getNome())
                .claim("scope", "ALUNO"));
    }

    private JwtClaimsSet.Builder base() {
        return JwtClaimsSet.builder().issuer("gestao-academia");
    }

    private Token assinar(JwtClaimsSet.Builder claims) {
        Instant agora = clock.instant();
        Instant expira = agora.plus(props.jwt().expirationHours(), ChronoUnit.HOURS);
        JwtClaimsSet c = claims.issuedAt(agora).expiresAt(expira).build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String valor = encoder.encode(JwtEncoderParameters.from(header, c)).getTokenValue();
        return new Token(valor, expira);
    }
}
