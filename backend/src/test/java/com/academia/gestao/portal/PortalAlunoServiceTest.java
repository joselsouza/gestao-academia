package com.academia.gestao.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.academia.gestao.aluno.Aluno;
import com.academia.gestao.aluno.AlunoRepository;
import com.academia.gestao.cobranca.Cobranca;
import com.academia.gestao.cobranca.CobrancaRepository;
import com.academia.gestao.cobranca.StatusCobranca;
import com.academia.gestao.cobranca.TipoCobranca;
import com.academia.gestao.common.NegocioException;
import com.academia.gestao.financeiro.Mensalidade;
import com.academia.gestao.financeiro.MensalidadeRepository;
import com.academia.gestao.financeiro.StatusMensalidade;
import com.academia.gestao.portal.PortalDtos.*;
import com.academia.gestao.security.TokenService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class PortalAlunoServiceTest {

    private static final String CPF = "12345678901";

    private final AlunoRepository alunos = mock(AlunoRepository.class);
    private final MensalidadeRepository mensalidades = mock(MensalidadeRepository.class);
    private final CobrancaRepository cobrancas = mock(CobrancaRepository.class);
    private final TokenService tokens = mock(TokenService.class);
    private final Map<Long, AlunoAcesso> banco = new HashMap<>();
    private final AlunoAcessoRepository acessos = mock(AlunoAcessoRepository.class);
    private PortalAlunoService service;
    private Aluno aluno;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        when(acessos.findById(any())).thenAnswer(i -> Optional.ofNullable(banco.get(i.<Long>getArgument(0))));
        when(acessos.save(any())).thenAnswer(i -> {
            AlunoAcesso a = i.getArgument(0);
            banco.put(a.getAlunoId(), a);
            return a;
        });
        when(tokens.gerarAluno(any())).thenReturn(new TokenService.Token("tok", Instant.now()));
        service = new PortalAlunoService(alunos, acessos, mensalidades, cobrancas, new BCryptPasswordEncoder(4), tokens,
                new TentativasLogin(clock), clock, "123456");

        aluno = new Aluno();
        aluno.setId(7L);
        aluno.setNome("Fulano");
        aluno.setCpf(CPF);
        when(alunos.findByCpf(CPF)).thenReturn(List.of(aluno));
        when(alunos.findById(7L)).thenReturn(Optional.of(aluno));
    }

    private LoginResponse login(String senha) {
        return service.login(new LoginRequest("123.456.789-01", senha));
    }

    @Test
    void entraComCpfFormatadoESenhaPadrao() {
        LoginResponse r = login("123456");
        assertThat(r.perfil()).isEqualTo("ALUNO");
        assertThat(r.senhaPadrao()).isTrue();
        assertThat(banco.get(7L).getUltimoLogin()).isNotNull();
    }

    @Test
    void recusaSenhaErradaECpfInexistente() {
        assertThatThrownBy(() -> login("errada")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.login(new LoginRequest("99999999999", "123456")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void alunoDesativadoNaoEntra() {
        aluno.setAtivo(false);
        assertThatThrownBy(() -> login("123456")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void bloqueiaDepoisDeCincoErros() {
        for (int i = 0; i < TentativasLogin.MAX_FALHAS; i++) {
            assertThatThrownBy(() -> login("errada")).isInstanceOf(ResponseStatusException.class);
        }
        assertThatThrownBy(() -> login("123456"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void trocaSenhaEPassaAUsarANova() {
        service.trocarSenha(7L, new TrocarSenha("123456", "minhaSenha9"));
        assertThatThrownBy(() -> login("123456")).isInstanceOf(ResponseStatusException.class);
        assertThat(login("minhaSenha9").senhaPadrao()).isFalse();

        service.redefinirSenha(7L);
        assertThat(login("123456").senhaPadrao()).isTrue();
    }

    @Test
    void primeiroAcessoCriaSenhaSemPedirAAtualMasNaoAceitaAPadrao() {
        assertThatThrownBy(() -> service.trocarSenha(7L, new TrocarSenha(null, "123456")))
                .isInstanceOf(NegocioException.class);
        service.trocarSenha(7L, new TrocarSenha(null, "minhaSenha9"));
        assertThat(login("minhaSenha9").senhaPadrao()).isFalse();
    }

    @Test
    void depoisDoPrimeiroAcessoATrocaExigeASenhaAtual() {
        service.trocarSenha(7L, new TrocarSenha(null, "minhaSenha9"));
        assertThatThrownBy(() -> service.trocarSenha(7L, new TrocarSenha(null, "outra123")))
                .isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> service.trocarSenha(7L, new TrocarSenha("errada", "outra123")))
                .isInstanceOf(NegocioException.class);
        service.trocarSenha(7L, new TrocarSenha("minhaSenha9", "outra123"));
        assertThat(login("outra123").senhaPadrao()).isFalse();
    }

    @Test
    void comSenhaPadraoNaoVeMensalidadesNemEditaPerfil() {
        assertThatThrownBy(() -> service.mensalidades(7L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.atualizarPerfil(7L, new AtualizarPerfil("X", null, null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void cpfDuplicadoEntreAtivosPedeParaProcurarARecepcao() {
        Aluno outro = new Aluno();
        outro.setId(8L);
        when(alunos.findByCpf(CPF)).thenReturn(List.of(aluno, outro));
        assertThatThrownBy(() -> login("123456")).isInstanceOf(NegocioException.class);
    }

    @Test
    void perfilAtualizaSoOsDadosPermitidos() {
        service.trocarSenha(7L, new TrocarSenha(null, "minhaSenha9"));
        MeuPerfil p = service.atualizarPerfil(7L, new AtualizarPerfil(" Fulano Silva ", "(24) 99999-0000", "F@X.COM"));
        assertThat(p.nome()).isEqualTo("Fulano Silva");
        assertThat(p.telefone()).isEqualTo("24999990000");
        assertThat(p.email()).isEqualTo("f@x.com");
        assertThat(p.cpf()).isEqualTo(CPF);
    }

    @Test
    void mensalidadesEscondemCanceladasELinkDasPagas() {
        service.trocarSenha(7L, new TrocarSenha(null, "minhaSenha9"));
        when(mensalidades.findByAluno(7L)).thenReturn(List.of(
                mensalidade(StatusMensalidade.PENDENTE, "https://pagar/1"),
                mensalidade(StatusMensalidade.PAGO, "https://pagar/2"),
                mensalidade(StatusMensalidade.CANCELADO, null)));
        List<MinhaMensalidade> lista = service.mensalidades(7L);
        assertThat(lista).extracting(MinhaMensalidade::status)
                .containsExactly(StatusMensalidade.PENDENTE, StatusMensalidade.PAGO);
        assertThat(lista).extracting(MinhaMensalidade::link).containsExactly("https://pagar/1", null);
        assertThat(lista.get(0).atrasada()).isTrue();
    }

    @Test
    void cobrancasAvulsasEscondemEstornadasEEncerradasEMarcamVencidas() {
        service.trocarSenha(7L, new TrocarSenha(null, "minhaSenha9"));
        when(cobrancas.findByAluno(7L)).thenReturn(List.of(
                cobranca(StatusCobranca.PENDENTE, LocalDate.of(2026, 11, 5), "https://pagar/a"),
                cobranca(StatusCobranca.PENDENTE, LocalDate.of(2026, 11, 20), "https://pagar/b"),
                cobranca(StatusCobranca.PAGA, LocalDate.of(2026, 10, 5), "https://pagar/c"),
                cobranca(StatusCobranca.ESTORNADA, LocalDate.of(2026, 9, 5), null),
                cobranca(StatusCobranca.ENCERRADA, LocalDate.of(2026, 8, 5), null)));
        List<MinhaCobranca> lista = service.cobrancas(7L);
        assertThat(lista).extracting(MinhaCobranca::status)
                .containsExactly(StatusCobranca.VENCIDA, StatusCobranca.PENDENTE, StatusCobranca.PAGA);
        assertThat(lista).extracting(MinhaCobranca::link).containsExactly("https://pagar/a", "https://pagar/b", null);
    }

    @Test
    void comSenhaPadraoNaoVeCobrancas() {
        assertThatThrownBy(() -> service.cobrancas(7L)).isInstanceOf(ResponseStatusException.class);
    }

    private Cobranca cobranca(StatusCobranca status, LocalDate vencimento, String link) {
        Cobranca c = new Cobranca();
        c.setAluno(aluno);
        c.setTipo(TipoCobranca.UNICA);
        c.setStatus(status);
        c.setVencimento(vencimento);
        c.setLink(link);
        c.setValor(new BigDecimal("80.00"));
        c.setDescricao("Kimono");
        return c;
    }

    private Mensalidade mensalidade(StatusMensalidade status, String link) {
        Mensalidade m = new Mensalidade();
        m.setAluno(aluno);
        m.setStatus(status);
        m.setLink(link);
        m.setValor(new BigDecimal("150.00"));
        m.setCompetencia(LocalDate.of(2026, 10, 1));
        m.setVencimento(LocalDate.of(2026, 10, 10));
        return m;
    }
}
