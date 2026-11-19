package com.academia.gestao.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AvisosPushTest {

    private final PushService push = mock(PushService.class);
    private final AvisosPush avisos = new AvisosPush(push);

    @Test
    void pagamentoConfirmadoVaiParaAEquipeComONomeDoAluno() {
        when(push.primeiraVez("pagamento:pay_1")).thenReturn(true);

        avisos.pagamentoConfirmado("pay_1", "João Silva", new BigDecimal("150.00"), "Mensalidade de dezembro/2026", "/financeiro/mensalidades");

        ArgumentCaptor<Notificacao> n = ArgumentCaptor.forClass(Notificacao.class);
        verify(push).paraEquipe(n.capture());
        assertThat(n.getValue().titulo()).isEqualTo("Pagamento confirmado: João Silva");
        assertThat(n.getValue().corpo()).isEqualTo("R$ 150,00 · Mensalidade de dezembro/2026");
        assertThat(n.getValue().url()).isEqualTo("/financeiro/mensalidades");
        verify(push, never()).paraAluno(any(), any());
    }

    @Test
    void mesmoPagamentoNaoAvisaDuasVezes() {
        when(push.primeiraVez(anyString())).thenReturn(false);
        avisos.pagamentoConfirmado("pay_1", "João Silva", BigDecimal.TEN, null, "/");
        verify(push, never()).paraEquipe(any());
    }

    @Test
    void novaMensalidadeVaiSoParaOAluno() {
        when(push.primeiraVez("mensalidade:9")).thenReturn(true);

        avisos.novaMensalidade(9L, 7L, "Mensalidade de dezembro/2026", new BigDecimal("150"), LocalDate.of(2026, 12, 10));

        ArgumentCaptor<Notificacao> n = ArgumentCaptor.forClass(Notificacao.class);
        verify(push).paraAluno(eq(7L), n.capture());
        assertThat(n.getValue().titulo()).isEqualTo("Nova mensalidade");
        assertThat(n.getValue().corpo()).isEqualTo("Mensalidade de dezembro/2026 · R$ 150,00 · vence em 10/12/2026");
        assertThat(n.getValue().url()).isEqualTo("/financeiro");
        verify(push, never()).paraEquipe(any());
    }

    @Test
    void novaCobrancaVaiParaOAluno() {
        when(push.primeiraVez("cobranca:pay_9")).thenReturn(true);
        avisos.novaCobranca("pay_9", 7L, "Kimono", new BigDecimal("320.5"), LocalDate.of(2026, 11, 20));
        ArgumentCaptor<Notificacao> n = ArgumentCaptor.forClass(Notificacao.class);
        verify(push).paraAluno(eq(7L), n.capture());
        assertThat(n.getValue().corpo()).isEqualTo("Kimono · R$ 320,50 · vence em 20/11/2026");
    }

    @Test
    void jsonEscapaAspasEQuebras() {
        assertThat(new Notificacao("A \"b\"", "c\nd", "/", null).json())
                .isEqualTo("{\"titulo\":\"A \\\"b\\\"\",\"corpo\":\"c\\nd\",\"url\":\"/\",\"tag\":null}");
    }
}
