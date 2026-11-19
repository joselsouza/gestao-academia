package com.academia.gestao.presenca;

import com.academia.gestao.aluno.Faixa;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class PresencaDtos {

    private PresencaDtos() {}

    public record PresencaResponse(Long id, Long alunoId, String alunoNome, Faixa faixa, int graus,
                                   LocalDate data, OrigemPresenca origem, BigDecimal confianca) {

        static PresencaResponse of(Presenca p) {
            return new PresencaResponse(p.getId(), p.getAluno().getId(), p.getAluno().getNome(),
                    p.getAluno().getFaixa(), p.getAluno().getGraus(), p.getData(), p.getOrigem(), p.getConfianca());
        }
    }

    public record ItemPresenca(@NotNull Long alunoId, OrigemPresenca origem, BigDecimal confianca) {}

    /**
     * Botão "Salvar presença". Só aqui a presença é gravada.
     * removerAusentes=true (chamada): a lista enviada passa a ser a lista completa do dia.
     * removerAusentes=false (reconhecimento): apenas acrescenta, sem apagar quem já estava.
     */
    public record SalvarPresencaRequest(
            @NotNull LocalDate data,
            @NotNull @Size(max = 500) List<@jakarta.validation.Valid ItemPresenca> itens,
            boolean removerAusentes) {}

    public record SalvarPresencaResponse(LocalDate data, int adicionadas, int removidas, int totalNoDia) {}

    /** Vetores faciais extraídos no navegador da foto da turma (um por rosto detectado). */
    public record ReconhecimentoRequest(
            LocalDate data,
            @NotEmpty @Size(max = 100) List<double[]> rostos) {}

    public record RostoReconhecido(int indiceRosto, Long alunoId, String alunoNome, Faixa faixa, int graus, String foto,
                                   double distancia, double confianca, boolean jaEstavaPresente) {}

    public record ReconhecimentoResponse(LocalDate data, int totalRostos, int alunosCadastradosComBiometria,
                                         List<RostoReconhecido> reconhecidos, List<Integer> rostosNaoReconhecidos) {}

    public record TotalDia(LocalDate data, long total) {}
}
