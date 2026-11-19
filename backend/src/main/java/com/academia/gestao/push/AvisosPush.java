package com.academia.gestao.push;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Textos das notificações do financeiro.
 * Aluno (portal): nova mensalidade e nova cobrança. Equipe: pagamento confirmado, com o nome do aluno.
 * Cada aviso tem uma chave: o mesmo aviso nunca é enviado duas vezes.
 */
@Component
@RequiredArgsConstructor
public class AvisosPush {

    private static final Locale BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PushService push;

    /** Mensalidade gerada para o aluno. */
    public void novaMensalidade(Long mensalidadeId, Long alunoId, String descricao, BigDecimal valor, LocalDate vencimento) {
        if (alunoId == null || !push.primeiraVez("mensalidade:" + mensalidadeId)) {
            return;
        }
        push.paraAluno(alunoId, new Notificacao("Nova mensalidade",
                descricao + " · " + real(valor) + " · vence em " + DATA.format(vencimento),
                "/financeiro", "mensalidade-" + mensalidadeId));
    }

    /** Cobrança avulsa (ou nova parcela de uma recorrente) para o aluno. */
    public void novaCobranca(String chave, Long alunoId, String descricao, BigDecimal valor, LocalDate vencimento) {
        if (alunoId == null || chave == null || !push.primeiraVez("cobranca:" + chave)) {
            return;
        }
        push.paraAluno(alunoId, new Notificacao("Nova cobrança",
                descricao + " · " + real(valor) + (vencimento != null ? " · vence em " + DATA.format(vencimento) : ""),
                "/financeiro", "cobranca-" + chave));
    }

    /** Pagamento confirmado (Asaas), para a equipe. */
    public void pagamentoConfirmado(String chave, String nomeAluno, BigDecimal valor, String descricao, String url) {
        if (chave == null || !push.primeiraVez("pagamento:" + chave)) {
            return;
        }
        push.paraEquipe(new Notificacao("Pagamento confirmado: " + nomeAluno,
                real(valor) + (descricao == null || descricao.isBlank() ? "" : " · " + descricao),
                url, "pagamento-" + chave));
    }

    private static String real(BigDecimal v) {
        return v == null ? "" : NumberFormat.getCurrencyInstance(BR).format(v).replace(' ', ' ');
    }
}
