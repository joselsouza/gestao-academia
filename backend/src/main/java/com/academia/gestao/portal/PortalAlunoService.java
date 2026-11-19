package com.academia.gestao.portal;

import com.academia.gestao.aluno.Aluno;
import com.academia.gestao.aluno.AlunoRepository;
import com.academia.gestao.cobranca.CobrancaRepository;
import com.academia.gestao.cobranca.StatusCobranca;
import com.academia.gestao.common.NaoEncontradoException;
import com.academia.gestao.common.NegocioException;
import com.academia.gestao.financeiro.MensalidadeRepository;
import com.academia.gestao.financeiro.StatusMensalidade;
import com.academia.gestao.portal.PortalDtos.*;
import com.academia.gestao.security.TokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Portal do aluno: login por CPF, financeiro (as próprias mensalidades) e edição do perfil.
 * Todo método recebe o id do aluno já tirado do token — nunca de um parâmetro da requisição.
 */
@Service
public class PortalAlunoService {

    private static final String LOGIN_INVALIDO = "CPF ou senha inválidos.";

    private final AlunoRepository alunos;
    private final AlunoAcessoRepository acessos;
    private final MensalidadeRepository mensalidades;
    private final CobrancaRepository cobrancas;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final TentativasLogin tentativas;
    private final Clock clock;
    private final String senhaPadrao;
    /** Hash descartável: o login gasta o mesmo tempo exista ou não o CPF. */
    private final String hashFalso;

    public PortalAlunoService(AlunoRepository alunos, AlunoAcessoRepository acessos, MensalidadeRepository mensalidades,
                              CobrancaRepository cobrancas, PasswordEncoder encoder, TokenService tokens, TentativasLogin tentativas, Clock clock,
                              @Value("${app.portal-aluno.senha-padrao:123456}") String senhaPadrao) {
        this.alunos = alunos;
        this.acessos = acessos;
        this.mensalidades = mensalidades;
        this.cobrancas = cobrancas;
        this.encoder = encoder;
        this.tokens = tokens;
        this.tentativas = tentativas;
        this.clock = clock;
        this.senhaPadrao = senhaPadrao == null || senhaPadrao.isBlank() ? "123456" : senhaPadrao;
        this.hashFalso = encoder.encode("portal-aluno-hash-falso");
    }

    @Transactional
    public LoginResponse login(LoginRequest req) {
        String cpf = req.cpf().replaceAll("\\D", "");
        if (cpf.length() != 11 && cpf.length() != 14) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, LOGIN_INVALIDO);
        }
        tentativas.verificar(cpf);

        List<Aluno> ativos = alunos.findByCpf(cpf).stream().filter(Aluno::isAtivo).toList();
        if (ativos.size() > 1) {
            throw new NegocioException("Há mais de um cadastro com este CPF. Procure a recepção da academia.");
        }
        Aluno aluno = ativos.isEmpty() ? null : ativos.get(0);
        AlunoAcesso acesso = aluno == null ? null
                : acessos.findById(aluno.getId()).orElseGet(() -> new AlunoAcesso(aluno.getId()));

        if (aluno == null) {
            encoder.matches(req.senha(), hashFalso);
        }
        if (aluno == null || !senhaConfere(acesso, req.senha())) {
            tentativas.falhou(cpf);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, LOGIN_INVALIDO);
        }

        tentativas.limpar(cpf);
        acesso.setUltimoLogin(OffsetDateTime.now(clock));
        acessos.save(acesso);
        var token = tokens.gerarAluno(aluno);
        return new LoginResponse(token.valor(), token.expiraEm(), aluno.getNome(), "ALUNO", acesso.isSenhaPadrao());
    }

    @Transactional(readOnly = true)
    public MeuPerfil perfil(Long alunoId) {
        return MeuPerfil.of(aluno(alunoId), senhaPadrao(alunoId));
    }

    @Transactional
    public MeuPerfil atualizarPerfil(Long alunoId, AtualizarPerfil req) {
        Aluno a = aluno(alunoId);
        exigirSenhaPropria(alunoId);
        a.setNome(req.nome().trim());
        String tel = req.telefone() == null ? "" : req.telefone().replaceAll("\\D", "");
        a.setTelefone(tel.isEmpty() ? null : tel);
        a.setEmail(req.email() == null || req.email().isBlank() ? null : req.email().trim().toLowerCase());
        return MeuPerfil.of(a, senhaPadrao(alunoId));
    }

    @Transactional
    public void trocarSenha(Long alunoId, TrocarSenha req) {
        aluno(alunoId);
        AlunoAcesso acesso = acessos.findById(alunoId).orElseGet(() -> new AlunoAcesso(alunoId));
        // Primeiro acesso: o aluno acabou de entrar com a senha padrão, então não a pedimos de novo.
        if (!acesso.isSenhaPadrao() && (req.senhaAtual() == null || !senhaConfere(acesso, req.senhaAtual()))) {
            throw new NegocioException("A senha atual não confere.");
        }
        if (req.novaSenha().equals(senhaPadrao)) {
            throw new NegocioException("Escolha uma senha diferente da senha padrão.");
        }
        acesso.setSenhaHash(encoder.encode(req.novaSenha()));
        acesso.setSenhaAlteradaEm(OffsetDateTime.now(clock));
        acessos.save(acesso);
    }

    @Transactional(readOnly = true)
    public List<MinhaMensalidade> mensalidades(Long alunoId) {
        aluno(alunoId);
        exigirSenhaPropria(alunoId);
        LocalDate hoje = LocalDate.now(clock);
        return mensalidades.findByAluno(alunoId).stream()
                .filter(m -> m.getStatus() != StatusMensalidade.CANCELADO)
                .map(m -> MinhaMensalidade.of(m, hoje))
                .toList();
    }

    /** Cobranças avulsas (únicas e recorrentes). Estornadas e encerradas não aparecem. */
    @Transactional(readOnly = true)
    public List<MinhaCobranca> cobrancas(Long alunoId) {
        aluno(alunoId);
        exigirSenhaPropria(alunoId);
        LocalDate hoje = LocalDate.now(clock);
        return cobrancas.findByAluno(alunoId).stream()
                .filter(c -> c.getStatus() != StatusCobranca.ESTORNADA && c.getStatus() != StatusCobranca.ENCERRADA)
                .map(c -> MinhaCobranca.of(c, hoje))
                .toList();
    }

    // ---- Área da equipe ----

    @Transactional(readOnly = true)
    public SituacaoAcesso situacao(Long alunoId) {
        alunos.findById(alunoId).orElseThrow(() -> new NaoEncontradoException("Aluno", alunoId));
        return acessos.findById(alunoId)
                .map(x -> new SituacaoAcesso(x.isSenhaPadrao(), x.getSenhaAlteradaEm(), x.getUltimoLogin()))
                .orElseGet(() -> new SituacaoAcesso(true, null, null));
    }

    /** Aluno esqueceu a senha: volta para a senha padrão. */
    @Transactional
    public SituacaoAcesso redefinirSenha(Long alunoId) {
        alunos.findById(alunoId).orElseThrow(() -> new NaoEncontradoException("Aluno", alunoId));
        acessos.findById(alunoId).ifPresent(x -> {
            x.setSenhaHash(null);
            x.setSenhaAlteradaEm(null);
        });
        return situacao(alunoId);
    }

    /** Aluno do token. Desativado ou removido = sessão encerrada. */
    private Aluno aluno(Long alunoId) {
        return alunos.findById(alunoId).filter(Aluno::isAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Acesso encerrado. Procure a academia."));
    }

    /** Primeiro acesso: com a senha padrão, o aluno só consegue criar a própria senha. */
    private void exigirSenhaPropria(Long alunoId) {
        if (senhaPadrao(alunoId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Crie a sua senha antes de continuar.");
        }
    }

    private boolean senhaPadrao(Long alunoId) {
        return acessos.findById(alunoId).map(AlunoAcesso::isSenhaPadrao).orElse(true);
    }

    private boolean senhaConfere(AlunoAcesso acesso, String senha) {
        if (acesso.isSenhaPadrao()) {
            return MessageDigest.isEqual(senha.getBytes(StandardCharsets.UTF_8), senhaPadrao.getBytes(StandardCharsets.UTF_8));
        }
        return encoder.matches(senha, acesso.getSenhaHash());
    }
}
