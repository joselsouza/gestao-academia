package com.academia.gestao.cobranca;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class CobrancaDtos {

    private CobrancaDtos() {}

    public record CobrancaRequest(
            @NotNull Long alunoId,
            @NotNull TipoCobranca tipo,
            @NotNull FormaCobranca forma,
            CicloCobranca ciclo,
            @NotNull @DecimalMin(value = "5.00", message = "valor mínimo do Asaas é R$ 5,00") BigDecimal valor,
            @NotNull LocalDate vencimento,
            LocalDate dataFim,
            @Size(max = 255) String descricao,
            /* Opcionais: se informados, atualizam o cadastro do aluno. */
            String cpf,
            @Email @Size(max = 160) String email) {}

    public record CobrancaResponse(
            Long id, Long alunoId, String alunoNome, String alunoTelefone,
            TipoCobranca tipo, FormaCobranca forma, CicloCobranca ciclo, String descricao, BigDecimal valor,
            LocalDate vencimento, LocalDate proximoVencimento, LocalDate dataFim,
            /* VENCIDA também quando o Asaas ainda não marcou, mas a data já passou. */
            StatusCobranca status,
            /* Dias até o vencimento (negativo = vencida há N dias). */
            Long diasParaVencer,
            String link, LocalDate dataPagamento, OffsetDateTime atualizadoEm,
            /* Responsável (aluno menor): é quem paga e quem recebe o link no WhatsApp. null = o próprio aluno. */
            String responsavelNome, String responsavelTelefone) {

        static CobrancaResponse of(Cobranca c, LocalDate hoje) {
            Long dias = c.getVencimento() == null ? null : ChronoUnit.DAYS.between(hoje, c.getVencimento());
            StatusCobranca status = c.getStatus() == StatusCobranca.PENDENTE && dias != null && dias < 0
                    ? StatusCobranca.VENCIDA : c.getStatus();
            return new CobrancaResponse(c.getId(), c.getAluno().getId(), c.getAluno().getNome(),
                    c.getAluno().getTelefone(), c.getTipo(), c.getForma(), c.getCiclo(), c.getDescricao(),
                    c.getValor(), c.getVencimento(), c.getProximoVencimento(), c.getDataFim(), status, dias,
                    c.getLink(), c.getDataPagamento(), c.getAtualizadoEm(),
                    c.getAluno().getResponsavel() == null ? null : c.getAluno().getResponsavel().getNome(),
                    c.getAluno().getResponsavel() == null ? null : c.getAluno().getResponsavel().getTelefone());
        }
    }

    public record ConfigResponse(boolean configurado, boolean sandbox, boolean webhook) {}

    public record SincronizacaoResponse(int atualizadas, List<String> falhas) {}
}
