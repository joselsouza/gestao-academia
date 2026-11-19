package com.academia.gestao.graduacao;

import com.academia.gestao.aluno.Faixa;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public final class GraduacaoDtos {

    private GraduacaoDtos() {}

    public record RegraResponse(Faixa faixa, int presencasMinimas, int mesesMinimos) {
        static RegraResponse of(RegraGraduacao r) {
            return new RegraResponse(r.getFaixa(), r.getPresencasMinimas(), r.getMesesMinimos());
        }
    }

    public record RegraRequest(@Min(0) @Max(1000) int presencasMinimas, @Min(0) @Max(240) int mesesMinimos) {}

    public record StatusGraduacao(
            Long alunoId, String nome, String foto, Faixa faixa, int graus,
            LocalDate contandoDesde, long presencas, int presencasMinimas,
            long meses, int mesesMinimos, int percentual, boolean apto,
            Faixa sugestaoFaixa, int sugestaoGraus) {}

    public record GraduarRequest(
            @NotNull Long alunoId,
            @NotNull Faixa faixaNova,
            @Min(0) @Max(6) int grausNova,
            LocalDate data,
            String observacao) {}

    public record GraduacaoResponse(
            Long id, Long alunoId, String alunoNome, Faixa faixaAnterior, int grausAnterior,
            Faixa faixaNova, int grausNova, LocalDate data, int presencasNoPeriodo, String observacao,
            String planoNovo) {

        static GraduacaoResponse of(Graduacao g) {
            return of(g, null);
        }

        /** planoNovo: nome do plano trocado automaticamente pela nova faixa (ou null). */
        static GraduacaoResponse of(Graduacao g, String planoNovo) {
            return new GraduacaoResponse(g.getId(), g.getAluno().getId(), g.getAluno().getNome(),
                    g.getFaixaAnterior(), g.getGrausAnterior(), g.getFaixaNova(), g.getGrausNova(),
                    g.getData(), g.getPresencasNoPeriodo(), g.getObservacao(), planoNovo);
        }
    }
}
