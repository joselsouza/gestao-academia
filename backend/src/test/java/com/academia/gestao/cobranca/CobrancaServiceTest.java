package com.academia.gestao.cobranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.academia.gestao.aluno.Aluno;
import com.academia.gestao.aluno.AlunoRepository;
import com.academia.gestao.aluno.Responsavel;
import com.academia.gestao.cobranca.AsaasDtos.*;
import com.academia.gestao.cobranca.CobrancaDtos.*;
import com.academia.gestao.common.NegocioException;
import com.academia.gestao.config.AppProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CobrancaServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 11);

    private final CobrancaRepository repo = mock(CobrancaRepository.class);
    private final AlunoRepository alunos = mock(AlunoRepository.class);
    private final AsaasClient asaas = mock(AsaasClient.class);
    private final com.academia.gestao.push.AvisosPush avisos = mock(com.academia.gestao.push.AvisosPush.class);
    private CobrancaService service;
    private Aluno aluno;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(HOJE.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant(),
                ZoneId.of("America/Sao_Paulo"));
        AppProperties props = new AppProperties("America/Sao_Paulo", List.of(), null, null, null,
                new AppProperties.Asaas("k", null, null), new AppProperties.Academia("Academia Teste"));
        service = new CobrancaService(repo, alunos, asaas, new AsaasCobrador(asaas), avisos, clock, props);
        aluno = new Aluno();
        aluno.setId(7L);
        aluno.setNome("Fulano de Tal");
        aluno.setTelefone("24999990000");
        when(alunos.findById(7L)).thenReturn(Optional.of(aluno));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(asaas.criarCliente(any())).thenReturn(new Cliente("cus_1"));
    }

    private CobrancaRequest req(TipoCobranca tipo, String cpf) {
        return new CobrancaRequest(7L, tipo, FormaCobranca.INDEFINIDA, tipo == TipoCobranca.RECORRENTE ? CicloCobranca.MENSAL : null,
                new BigDecimal("150.00"), HOJE.plusDays(3), null, null, cpf, null);
    }

    @Test
    void exigeCpfNaPrimeiraCobranca() {
        assertThatThrownBy(() -> service.criar(req(TipoCobranca.UNICA, null)))
                .isInstanceOf(NegocioException.class).hasMessageContaining("CPF");
        verifyNoInteractions(asaas);
    }

    @Test
    void criaCobrancaUnicaComLinkECliente() {
        when(asaas.criarPagamento(any())).thenReturn(new Pagamento("pay_1", null, "PENDING",
                new BigDecimal("150.00"), "2026-11-14", "https://sandbox.asaas.com/i/abc", null, null, false, null, null));

        CobrancaResponse r = service.criar(req(TipoCobranca.UNICA, "123.456.789-09"));

        assertThat(aluno.getCpf()).isEqualTo("12345678909");
        assertThat(aluno.getAsaasCustomerId()).isEqualTo("cus_1");
        assertThat(r.link()).isEqualTo("https://sandbox.asaas.com/i/abc");
        assertThat(r.status()).isEqualTo(StatusCobranca.PENDENTE);
        assertThat(r.diasParaVencer()).isEqualTo(3);
    }

    @Test
    void descricaoPadraoUsaNomeDaAcademia() {
        when(asaas.criarPagamento(any())).thenReturn(new Pagamento("pay_1", null, "PENDING",
                new BigDecimal("150.00"), "2026-11-14", "url", null, null, false, null, null));
        when(asaas.criarAssinatura(any())).thenReturn(new Assinatura("sub_1", "ACTIVE", "2026-11-14", false));
        when(asaas.pagamentosDaAssinatura("sub_1")).thenReturn(List.of());

        assertThat(service.criar(req(TipoCobranca.UNICA, "12345678909")).descricao()).isEqualTo("Academia Teste");
        assertThat(service.criar(req(TipoCobranca.RECORRENTE, "12345678909")).descricao())
                .isEqualTo("Mensalidade Academia Teste");
    }

    @Test
    void recorrenteMostraParcelaEmAbertoMaisAntiga() {
        when(asaas.criarAssinatura(any())).thenReturn(new Assinatura("sub_1", "ACTIVE", "2026-12-14", false));
        when(asaas.pagamentosDaAssinatura("sub_1")).thenReturn(List.of(
                new Pagamento("pay_3", "sub_1", "PENDING", new BigDecimal("150"), "2026-12-14", "url3", null, null, false, null, null),
                new Pagamento("pay_1", "sub_1", "RECEIVED", new BigDecimal("150"), "2026-10-14", "url1", "2026-10-10", null, false, null, null),
                new Pagamento("pay_2", "sub_1", "OVERDUE", new BigDecimal("150"), "2026-11-10", "url2", null, null, false, null, null)));

        CobrancaResponse r = service.criar(req(TipoCobranca.RECORRENTE, "12345678909"));

        assertThat(r.link()).isEqualTo("url2");
        assertThat(r.status()).isEqualTo(StatusCobranca.VENCIDA);
        assertThat(r.diasParaVencer()).isEqualTo(-1);
        assertThat(r.proximoVencimento()).isEqualTo(LocalDate.of(2026, 12, 14));
    }

    @Test
    void pendenteComDataPassadaApareceComoVencida() {
        Cobranca c = new Cobranca();
        c.setAluno(aluno);
        c.setStatus(StatusCobranca.PENDENTE);
        c.setVencimento(HOJE.minusDays(2));
        assertThat(CobrancaResponse.of(c, HOJE).status()).isEqualTo(StatusCobranca.VENCIDA);
    }

    @Test
    void naoExcluiCobrancaUnicaPaga() {
        Cobranca c = new Cobranca();
        c.setTipo(TipoCobranca.UNICA);
        c.setStatus(StatusCobranca.PAGA);
        when(repo.findById(1L)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.excluir(1L)).isInstanceOf(NegocioException.class);
        verify(asaas, never()).excluirPagamento(any());
    }

    @Test
    void alunoComResponsavelECobradoNoNomeECpfDoResponsavel() {
        Responsavel r = new Responsavel();
        r.setId(3L);
        r.setNome("Poliana Machado");
        r.setCpf("09022921700");
        r.setTelefone("(24) 99969-0100");
        r.setEmail("poliana@exemplo.com");
        aluno.setResponsavel(r);
        when(asaas.criarPagamento(any())).thenReturn(new Pagamento("pay_1", null, "PENDING",
                new BigDecimal("150.00"), "2026-11-14", "url", null, null, false, null, null));

        // O aluno (menor) não tem CPF: quem paga é o responsável.
        service.criar(req(TipoCobranca.UNICA, null));

        ArgumentCaptor<ClienteReq> cliente = ArgumentCaptor.forClass(ClienteReq.class);
        verify(asaas).criarCliente(cliente.capture());
        assertThat(cliente.getValue().name()).isEqualTo("Poliana Machado");
        assertThat(cliente.getValue().cpfCnpj()).isEqualTo("09022921700");
        assertThat(cliente.getValue().mobilePhone()).isEqualTo("24999690100");
        assertThat(cliente.getValue().externalReference()).isEqualTo("responsavel-3");
        assertThat(r.getAsaasCustomerId()).isEqualTo("cus_1");
        assertThat(aluno.getAsaasCustomerId()).isNull();

        ArgumentCaptor<PagamentoReq> pagamento = ArgumentCaptor.forClass(PagamentoReq.class);
        verify(asaas).criarPagamento(pagamento.capture());
        assertThat(pagamento.getValue().customer()).isEqualTo("cus_1");
        assertThat(pagamento.getValue().description()).endsWith(" - aluno(a): Fulano de Tal");
    }

    @Test
    void irmaosComOMesmoResponsavelUsamOMesmoClienteNoAsaas() {
        Responsavel r = new Responsavel();
        r.setId(3L);
        r.setNome("Poliana Machado");
        r.setCpf("09022921700");
        r.setAsaasCustomerId("cus_resp");
        aluno.setResponsavel(r);
        when(asaas.criarPagamento(any())).thenReturn(new Pagamento("pay_1", null, "PENDING",
                new BigDecimal("150.00"), "2026-11-14", "url", null, null, false, null, null));

        service.criar(req(TipoCobranca.UNICA, null));

        verify(asaas, never()).criarCliente(any());
        verify(asaas).atualizarCliente(eq("cus_resp"), any());
        assertThat(r.getAsaasCustomerId()).isEqualTo("cus_resp");
    }

    @Test
    void novaCobrancaAvisaOAluno() {
        when(asaas.criarPagamento(any())).thenReturn(new Pagamento("pay_1", null, "PENDING",
                new BigDecimal("150.00"), "2026-11-14", "url", null, null, false, null, null));

        service.criar(req(TipoCobranca.UNICA, "12345678909"));

        verify(avisos).novaCobranca("pay_1", 7L, "Academia Teste", new BigDecimal("150.00"), LocalDate.of(2026, 11, 14));
        verify(avisos, never()).pagamentoConfirmado(any(), any(), any(), any(), any());
    }

    @Test
    void pagamentoConfirmadoPeloWebhookAvisaAEquipe() {
        Cobranca c = new Cobranca();
        c.setAluno(aluno);
        c.setTipo(TipoCobranca.UNICA);
        c.setDescricao("Kimono - aluno(a): Fulano de Tal");
        c.setStatus(StatusCobranca.PENDENTE);
        when(repo.findByAsaasId("pay_1")).thenReturn(Optional.of(c));

        service.aoReceberEvento(new Evento("evt_1", "PAYMENT_RECEIVED", new Pagamento("pay_1", null, "RECEIVED",
                new BigDecimal("320.00"), "2026-11-14", "url", "2026-11-11", null, false, "PIX", null)));

        verify(avisos).pagamentoConfirmado("pay_1", "Fulano de Tal", new BigDecimal("320.00"), "Kimono", "/financeiro/cobrancas");
    }

    @Test
    void pagamentoAntigoNaoGeraAviso() {
        Cobranca c = new Cobranca();
        c.setAluno(aluno);
        c.setTipo(TipoCobranca.UNICA);
        c.setAsaasId("pay_1");
        when(repo.findById(1L)).thenReturn(Optional.of(c));
        when(asaas.pagamento("pay_1")).thenReturn(new Pagamento("pay_1", null, "RECEIVED",
                new BigDecimal("150"), "2026-09-14", "url", "2026-09-10", null, false, "PIX", null));

        service.sincronizar(1L);

        verify(avisos, never()).pagamentoConfirmado(any(), any(), any(), any(), any());
    }
}
