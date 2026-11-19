package com.academia.gestao.financeiro;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class FinanceiroDtos {

    private FinanceiroDtos() {}

    public record MensalidadeResponse(
            Long id, Long alunoId, String alunoNome, String alunoTelefone, LocalDate competencia,
            BigDecimal valor, LocalDate vencimento, StatusMensalidade status, boolean atrasada,
            LocalDate dataPagamento, FormaPagamento formaPagamento, String observacao,
            /* Link de pagamento do Asaas (null = ainda não gerado). */
            String link,
            /* O Asaas exige CPF (do aluno ou, se houver, do responsável): sem ele, o link não pode ser gerado. */
            boolean alunoComCpf,
            /* Responsável (aluno menor): é quem paga e quem recebe o link no WhatsApp. null = o próprio aluno. */
            String responsavelNome, String responsavelTelefone) {

        static MensalidadeResponse of(Mensalidade m, LocalDate hoje) {
            return new MensalidadeResponse(m.getId(), m.getAluno().getId(), m.getAluno().getNome(),
                    m.getAluno().getTelefone(), m.getCompetencia(), m.getValor(), m.getVencimento(), m.getStatus(),
                    m.isAtrasada(hoje), m.getDataPagamento(), m.getFormaPagamento(), m.getObservacao(),
                    m.getLink(), m.getAluno().cpfDoPagador() != null,
                    m.getAluno().getResponsavel() == null ? null : m.getAluno().getResponsavel().getNome(),
                    m.getAluno().getResponsavel() == null ? null : m.getAluno().getResponsavel().getTelefone());
        }
    }

    public record PagamentoRequest(
            @NotNull FormaPagamento formaPagamento,
            LocalDate dataPagamento,
            @DecimalMin("0.00") BigDecimal valor,
            String observacao) {}

    public record GeracaoResponse(String competencia, int criadas, int jaExistentes, int bolsistasIsentos,
                                  LinksResponse links) {}

    /** Resultado da emissão de links no Asaas. */
    public record LinksResponse(int gerados, int semCpf, List<String> falhas) {
        static LinksResponse vazio() {
            return new LinksResponse(0, 0, List.of());
        }
    }

    public record LinkRequest(String cpf) {}

    public record SincronizacaoAsaas(int verificadas, int pagas, List<String> falhas) {}

    public record ResumoFinanceiro(
            String competencia,
            BigDecimal previsto,
            BigDecimal recebido,
            BigDecimal pendente,
            BigDecimal atrasado,
            long qtdPagas,
            long qtdPendentes,
            long qtdAtrasadas,
            long bolsistasAtivos) {}
}
