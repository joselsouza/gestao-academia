package com.academia.gestao.portal;

import com.academia.gestao.aluno.Aluno;
import com.academia.gestao.aluno.Faixa;
import com.academia.gestao.cobranca.CicloCobranca;
import com.academia.gestao.cobranca.Cobranca;
import com.academia.gestao.cobranca.StatusCobranca;
import com.academia.gestao.cobranca.TipoCobranca;
import com.academia.gestao.financeiro.FormaPagamento;
import com.academia.gestao.financeiro.Mensalidade;
import com.academia.gestao.financeiro.StatusMensalidade;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** DTOs do portal do aluno: só o que é do próprio aluno, sem dados internos da academia. */
public final class PortalDtos {

    private PortalDtos() {}

    public record LoginRequest(@NotBlank String cpf, @NotBlank String senha) {}

    public record LoginResponse(String token, Instant expiraEm, String nome, String perfil, boolean senhaPadrao) {}

    public record MeuPerfil(String nome, String cpf, String telefone, String email, Faixa faixa, int graus,
                            String planoNome, boolean senhaPadrao) {
        static MeuPerfil of(Aluno a, boolean senhaPadrao) {
            return new MeuPerfil(a.getNome(), a.getCpf(), a.getTelefone(), a.getEmail(), a.getFaixa(), a.getGraus(),
                    a.getPlano() != null ? a.getPlano().getNome() : null, senhaPadrao);
        }
    }

    /** O que o aluno pode mudar sozinho. CPF (login e cobranças), faixa e plano ficam com a academia. */
    public record AtualizarPerfil(
            @NotBlank @Size(max = 120) String nome,
            @Pattern(regexp = "^[0-9()+\\-\\s]{0,20}$", message = "telefone inválido") String telefone,
            @Email @Size(max = 160) String email) {}

    /** senhaAtual só é exigida depois do primeiro acesso (na senha padrão, o login já a confirmou). */
    public record TrocarSenha(
            String senhaAtual,
            @NotBlank @Size(min = 6, max = 72, message = "deve ter entre 6 e 72 caracteres") String novaSenha) {}

    public record MinhaMensalidade(Long id, LocalDate competencia, BigDecimal valor, LocalDate vencimento,
                                   StatusMensalidade status, boolean atrasada, LocalDate dataPagamento,
                                   FormaPagamento formaPagamento,
                                   /* Link de pagamento do Asaas, só enquanto a mensalidade está em aberto. */
                                   String link) {
        static MinhaMensalidade of(Mensalidade m, LocalDate hoje) {
            boolean aberta = m.getStatus() == StatusMensalidade.PENDENTE;
            return new MinhaMensalidade(m.getId(), m.getCompetencia(), m.getValor(), m.getVencimento(), m.getStatus(),
                    m.isAtrasada(hoje), m.getDataPagamento(), m.getFormaPagamento(), aberta ? m.getLink() : null);
        }
    }

    /**
     * Cobrança avulsa (única ou recorrente) do aluno. Na recorrente, vencimento/status/link são os da
     * parcela "da vez". status vira VENCIDA quando a data já passou e o Asaas ainda não marcou.
     */
    public record MinhaCobranca(Long id, TipoCobranca tipo, CicloCobranca ciclo, String descricao, BigDecimal valor,
                                LocalDate vencimento, StatusCobranca status, LocalDate dataPagamento,
                                /* Link de pagamento do Asaas, só enquanto está em aberto. */
                                String link) {
        static MinhaCobranca of(Cobranca c, LocalDate hoje) {
            StatusCobranca status = c.getStatus() == StatusCobranca.PENDENTE && c.getVencimento() != null
                    && c.getVencimento().isBefore(hoje) ? StatusCobranca.VENCIDA : c.getStatus();
            boolean aberta = status == StatusCobranca.PENDENTE || status == StatusCobranca.VENCIDA;
            return new MinhaCobranca(c.getId(), c.getTipo(), c.getCiclo(), c.getDescricao(), c.getValor(),
                    c.getVencimento(), status, c.getDataPagamento(), aberta ? c.getLink() : null);
        }
    }

    /** Situação do acesso ao portal, para a ficha do aluno na área da equipe. */
    public record SituacaoAcesso(boolean senhaPadrao, OffsetDateTime senhaAlteradaEm, OffsetDateTime ultimoLogin) {}
}
