package com.academia.gestao.graduacao;

import com.academia.gestao.aluno.Aluno;
import com.academia.gestao.aluno.AlunoRepository;
import com.academia.gestao.aluno.Faixa;
import com.academia.gestao.common.NaoEncontradoException;
import com.academia.gestao.common.NegocioException;
import com.academia.gestao.graduacao.GraduacaoDtos.*;
import com.academia.gestao.plano.Plano;
import com.academia.gestao.plano.PlanoRepository;
import com.academia.gestao.presenca.PresencaRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GraduacaoService {

    private final RegraGraduacaoRepository regras;
    private final GraduacaoRepository graduacoes;
    private final AlunoRepository alunos;
    private final PresencaRepository presencas;
    private final PlanoRepository planos;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<RegraResponse> listarRegras() {
        Map<Faixa, RegraGraduacao> porFaixa = mapaRegras();
        return Arrays.stream(Faixa.values())
                .map(f -> porFaixa.containsKey(f) ? RegraResponse.of(porFaixa.get(f)) : new RegraResponse(f, 0, 0))
                .toList();
    }

    @Transactional
    public RegraResponse salvarRegra(Faixa faixa, RegraRequest req) {
        RegraGraduacao r = regras.findByFaixa(faixa).orElseGet(() -> {
            RegraGraduacao nova = new RegraGraduacao();
            nova.setFaixa(faixa);
            return nova;
        });
        r.setPresencasMinimas(req.presencasMinimas());
        r.setMesesMinimos(req.mesesMinimos());
        r.setAtualizadoEm(OffsetDateTime.now(clock));
        return RegraResponse.of(regras.save(r));
    }

    /**
     * Situação de cada aluno ativo: presenças desde a última graduação (ou matrícula) contra a
     * frequência mínima da faixa atual. "Apto" = atingiu presenças E tempo mínimos.
     */
    @Transactional(readOnly = true)
    public List<StatusGraduacao> status(boolean somenteAptos) {
        LocalDate hoje = LocalDate.now(clock);
        Map<Faixa, RegraGraduacao> porFaixa = mapaRegras();
        Map<Long, Long> contagem = new HashMap<>();
        for (Object[] linha : presencas.contarDesdeUltimaGraduacao()) {
            contagem.put(((Number) linha[0]).longValue(), ((Number) linha[1]).longValue());
        }
        return alunos.findAtivosComPlano().stream()
                .map(a -> calcular(a, porFaixa.get(a.getFaixa()), contagem.getOrDefault(a.getId(), 0L), hoje))
                .filter(s -> !somenteAptos || s.apto())
                .sorted(Comparator.comparing(StatusGraduacao::apto).reversed()
                        .thenComparing(Comparator.comparingInt(StatusGraduacao::percentual).reversed())
                        .thenComparing(StatusGraduacao::nome))
                .toList();
    }

    /** Progresso de um aluno para a próxima graduação (ficha do aluno). */
    @Transactional(readOnly = true)
    public StatusGraduacao statusDoAluno(Long alunoId) {
        Aluno a = alunos.findById(alunoId).orElseThrow(() -> new NaoEncontradoException("Aluno", alunoId));
        LocalDate desde = a.inicioPeriodoGraduacao();
        long noPeriodo = desde == null ? 0 : presencas.countByAlunoIdAndDataGreaterThanEqual(a.getId(), desde);
        return calcular(a, regras.findByFaixa(a.getFaixa()).orElse(null), noPeriodo, LocalDate.now(clock));
    }

    @Transactional
    public GraduacaoResponse graduar(GraduarRequest req) {
        Aluno a = alunos.findById(req.alunoId()).orElseThrow(() -> new NaoEncontradoException("Aluno", req.alunoId()));
        if (req.grausNova() > req.faixaNova().grausMaximos()) {
            throw new NegocioException("A faixa " + req.faixaNova() + " permite no máximo "
                    + req.faixaNova().grausMaximos() + " graus.");
        }
        if (req.faixaNova() == a.getFaixa() && req.grausNova() == a.getGraus()) {
            throw new NegocioException("A nova graduação é igual à atual.");
        }
        LocalDate data = req.data() != null ? req.data() : LocalDate.now(clock);
        long noPeriodo = presencas.countByAlunoIdAndDataGreaterThanEqual(a.getId(), a.inicioPeriodoGraduacao());

        Graduacao g = new Graduacao();
        g.setAluno(a);
        g.setFaixaAnterior(a.getFaixa());
        g.setGrausAnterior(a.getGraus());
        g.setFaixaNova(req.faixaNova());
        g.setGrausNova(req.grausNova());
        g.setData(data);
        g.setPresencasNoPeriodo((int) noPeriodo);
        g.setObservacao(req.observacao());
        graduacoes.save(g);

        String planoNovo = trocarPlanoPelaFaixa(a, req.faixaNova());
        a.setFaixa(req.faixaNova());
        a.setGraus(req.grausNova());
        a.setDataUltimaGraduacao(data); // zera a contagem de frequência
        return GraduacaoResponse.of(g, planoNovo);
    }

    /**
     * Ao mudar de faixa, troca para o plano vinculado à nova faixa — só se o aluno estiver sem
     * plano ou num plano "de faixa". Planos sem faixa (ex.: bolsista individual) são mantidos.
     */
    private String trocarPlanoPelaFaixa(Aluno a, Faixa novaFaixa) {
        if (novaFaixa == a.getFaixa()) {
            return null;
        }
        Plano atual = a.getPlano();
        if (atual != null && atual.getFaixas().isEmpty()) {
            return null;
        }
        return planos.findAtivoDaFaixa(novaFaixa)
                .filter(p -> atual == null || !p.getId().equals(atual.getId()))
                .map(p -> {
                    a.setPlano(p);
                    return p.getNome();
                })
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<GraduacaoResponse> historico(Long alunoId) {
        var lista = alunoId == null ? graduacoes.findHistorico() : graduacoes.findHistoricoDoAluno(alunoId);
        return lista.stream().map(GraduacaoResponse::of).toList();
    }

    static StatusGraduacao calcular(Aluno a, RegraGraduacao regra, long presencasNoPeriodo, LocalDate hoje) {
        int minPresencas = regra != null ? regra.getPresencasMinimas() : 0;
        int minMeses = regra != null ? regra.getMesesMinimos() : 0;
        LocalDate desde = a.inicioPeriodoGraduacao();
        long meses = desde == null ? 0 : ChronoUnit.MONTHS.between(desde, hoje);

        double pPresencas = minPresencas == 0 ? 1 : Math.min(1, (double) presencasNoPeriodo / minPresencas);
        double pMeses = minMeses == 0 ? 1 : Math.min(1, (double) meses / minMeses);
        int percentual = (int) Math.floor(Math.min(pPresencas, pMeses) * 100);
        boolean apto = presencasNoPeriodo >= minPresencas && meses >= minMeses;

        // Sugestão: próximo grau; com graus completos, próxima faixa.
        Faixa sugFaixa = a.getFaixa();
        int sugGraus = a.getGraus() + 1;
        if (sugGraus > a.getFaixa().grausMaximos()) {
            sugFaixa = a.getFaixa().proxima();
            sugGraus = 0;
        }
        return new StatusGraduacao(a.getId(), a.getNome(), a.getFotoMiniatura(), a.getFaixa(), a.getGraus(), desde,
                presencasNoPeriodo, minPresencas, meses, minMeses, percentual, apto, sugFaixa, sugGraus);
    }

    private Map<Faixa, RegraGraduacao> mapaRegras() {
        Map<Faixa, RegraGraduacao> mapa = new EnumMap<>(Faixa.class);
        regras.findAll().forEach(r -> mapa.put(r.getFaixa(), r));
        return mapa;
    }
}
