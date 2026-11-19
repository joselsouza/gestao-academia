package com.academia.gestao.financeiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

import com.academia.gestao.aluno.Aluno;
import com.academia.gestao.aluno.AlunoRepository;
import com.academia.gestao.cobranca.AsaasCobrador;
import com.academia.gestao.cobranca.AsaasCobrador.Situacao;
import com.academia.gestao.cobranca.StatusCobranca;
import com.academia.gestao.common.NegocioException;
import com.academia.gestao.financeiro.FinanceiroDtos.*;
import com.academia.gestao.plano.Plano;
import com.academia.gestao.plano.TipoPlano;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FinanceiroAsaasTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate HOJE = LocalDate.of(2026, 12, 1);

    private final MensalidadeRepository repo = mock(MensalidadeRepository.class);
    private final AlunoRepository alunos = mock(AlunoRepository.class);
    private final AsaasCobrador asaas = mock(AsaasCobrador.class);
    private final com.academia.gestao.push.AvisosPush avisos = mock(com.academia.gestao.push.AvisosPush.class);
    private FinanceiroService service;
    private Aluno comCpf;
    private Aluno semCpf;

    @BeforeEach
    void setUp() {
        service = new FinanceiroService(repo, alunos, asaas, avisos,
                Clock.fixed(HOJE.atStartOfDay(SP).toInstant(), SP), 10, "Academia Teste", "2026-12");
        Plano plano = new Plano();
        plano.setTipo(TipoPlano.MENSALIDADE);
        plano.setValor(new BigDecimal("150.00"));
        comCpf = aluno(1L, "Ana", "12345678909", plano);
        semCpf = aluno(2L, "Bruno", null, plano);
        semCpf.setDiaVencimento(25);
        when(asaas.isConfigurado()).thenReturn(true);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(asaas.emitir(any(), any(), any(), any(), anyString(), anyString()))
                .thenReturn(new AsaasCobrador.Emitido("pay_9", "https://asaas/i/9"));
    }

    private static Aluno aluno(Long id, String nome, String cpf, Plano plano) {
        Aluno a = new Aluno();
        a.setId(id);
        a.setNome(nome);
        a.setCpf(cpf);
        a.setPlano(plano);
        return a;
    }

    @Test
    void geraNoDia10EEmiteLinkSoParaQuemTemCpf() {
        when(alunos.findAtivosComPlano()).thenReturn(List.of(comCpf, semCpf));

        GeracaoResponse r = service.gerar(YearMonth.of(2026, 12));

        assertThat(r.criadas()).isEqualTo(2);
        assertThat(r.links().gerados()).isEqualTo(1);
        assertThat(r.links().semCpf()).isEqualTo(1);
        verify(asaas).emitir(eq(comCpf), any(), eq(LocalDate.of(2026, 12, 10)), any(), startsWith("Mensalidade Academia Teste - "), anyString());
        verify(repo, times(2)).save(argThat(m -> m.getVencimento().equals(LocalDate.of(2026, 12, 10))));
    }

    @Test
    void naoGeraMensalidadeAntesDoInicioDaCobranca() {
        assertThat(service.cobravel(YearMonth.of(2026, 11))).isFalse();
        assertThat(service.cobravel(YearMonth.of(2026, 12))).isTrue();
        assertThatThrownBy(() -> service.gerar(YearMonth.of(2026, 11)))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("dezembro/2026");
        verify(repo, never()).save(any());
    }

    @Test
    void pagamentoNoAsaasDaBaixaNaMensalidade() {
        Mensalidade m = pendente();
        when(repo.findByAsaasPagamentoId("pay_9")).thenReturn(Optional.of(m));

        boolean tratado = service.aoReceberPagamentoAsaas("pay_9", new Situacao("pay_9", StatusCobranca.PAGA,
                LocalDate.of(2026, 12, 5), new BigDecimal("150.00"), "CREDIT_CARD", "https://asaas/i/9", false));

        assertThat(tratado).isTrue();
        assertThat(m.getStatus()).isEqualTo(StatusMensalidade.PAGO);
        assertThat(m.getFormaPagamento()).isEqualTo(FormaPagamento.CARTAO);
        assertThat(m.getDataPagamento()).isEqualTo(LocalDate.of(2026, 12, 5));
    }

    @Test
    void receberEmDinheiroCancelaOLinkNoAsaas() {
        Mensalidade m = pendente();
        when(repo.findById(5L)).thenReturn(Optional.of(m));

        service.pagar(5L, new PagamentoRequest(FormaPagamento.DINHEIRO, null, null, null));

        verify(asaas).excluir("pay_9");
        assertThat(m.getLink()).isNull();
        assertThat(m.getStatus()).isEqualTo(StatusMensalidade.PAGO);
    }

    @Test
    void avisaOAlunoDeCadaMensalidadeNova() {
        when(alunos.findAtivosComPlano()).thenReturn(List.of(comCpf, semCpf));

        service.gerar(YearMonth.of(2026, 12));

        verify(avisos).novaMensalidade(any(), eq(1L), eq("Mensalidade de dezembro/2026"), eq(new BigDecimal("150.00")),
                eq(LocalDate.of(2026, 12, 10)));
        verify(avisos).novaMensalidade(any(), eq(2L), anyString(), any(), any());
    }

    @Test
    void pagamentoConfirmadoNoAsaasAvisaAEquipeComONomeDoAluno() {
        when(repo.findByAsaasPagamentoId("pay_9")).thenReturn(Optional.of(pendente()));

        service.aoReceberPagamentoAsaas("pay_9", new Situacao("pay_9", StatusCobranca.PAGA,
                LocalDate.of(2026, 12, 5), new BigDecimal("150.00"), "PIX", null, false));

        verify(avisos).pagamentoConfirmado("pay_9", "Ana", new BigDecimal("150.00"), "Mensalidade de dezembro/2026",
                "/financeiro/mensalidades");
    }

    @Test
    void mensalidadeJaPagaOuRecebidaNoBalcaoNaoAvisa() {
        Mensalidade paga = pendente();
        paga.setStatus(StatusMensalidade.PAGO);
        when(repo.findByAsaasPagamentoId("pay_9")).thenReturn(Optional.of(paga));
        service.aoReceberPagamentoAsaas("pay_9", new Situacao("pay_9", StatusCobranca.PAGA,
                LocalDate.of(2026, 12, 5), new BigDecimal("150.00"), "PIX", null, false));

        Mensalidade outra = pendente();
        when(repo.findById(5L)).thenReturn(Optional.of(outra));
        service.pagar(5L, new PagamentoRequest(FormaPagamento.DINHEIRO, null, null, null));

        verify(avisos, never()).pagamentoConfirmado(any(), any(), any(), any(), any());
    }

    private Mensalidade pendente() {
        Mensalidade m = new Mensalidade();
        m.setId(5L);
        m.setAluno(comCpf);
        m.setCompetencia(LocalDate.of(2026, 12, 1));
        m.setValor(new BigDecimal("150.00"));
        m.setVencimento(LocalDate.of(2026, 12, 10));
        m.setAsaasPagamentoId("pay_9");
        m.setLink("https://asaas/i/9");
        return m;
    }
}
