package com.academia.gestao.presenca;

import com.academia.gestao.aluno.Aluno;
import com.academia.gestao.aluno.AlunoMidiaRepository;
import com.academia.gestao.aluno.AlunoRepository;
import com.academia.gestao.common.NaoEncontradoException;
import com.academia.gestao.common.NegocioException;
import com.academia.gestao.config.AppProperties;
import com.academia.gestao.presenca.PresencaDtos.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PresencaService {

    private final PresencaRepository presencas;
    private final AlunoRepository alunos;
    private final AlunoMidiaRepository midias;
    private final AppProperties props;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PresencaResponse> listar(LocalDate inicio, LocalDate fim) {
        LocalDate hoje = LocalDate.now(clock);
        LocalDate i = inicio != null ? inicio : hoje;
        LocalDate f = fim != null ? fim : i;
        if (f.isBefore(i)) {
            throw new NegocioException("Data final anterior à inicial.");
        }
        return presencas.findPeriodo(i, f).stream().map(PresencaResponse::of).toList();
    }

    @Transactional
    public SalvarPresencaResponse salvar(SalvarPresencaRequest req) {
        LocalDate data = req.data();
        if (data.isAfter(LocalDate.now(clock))) {
            throw new NegocioException("Não é possível salvar presença em data futura.");
        }
        Map<Long, Presenca> existentes = presencas.findPeriodo(data, data).stream()
                .collect(Collectors.toMap(p -> p.getAluno().getId(), Function.identity()));
        Map<Long, ItemPresenca> enviados = new java.util.LinkedHashMap<>();
        req.itens().forEach(i -> enviados.putIfAbsent(i.alunoId(), i));

        int adicionadas = 0;
        for (ItemPresenca item : enviados.values()) {
            if (existentes.containsKey(item.alunoId())) {
                continue;
            }
            Aluno aluno = alunos.findById(item.alunoId())
                    .orElseThrow(() -> new NaoEncontradoException("Aluno", item.alunoId()));
            Presenca nova = new Presenca();
            nova.setAluno(aluno);
            nova.setData(data);
            boolean facial = item.origem() == OrigemPresenca.RECONHECIMENTO_FACIAL;
            nova.setOrigem(facial ? OrigemPresenca.RECONHECIMENTO_FACIAL : OrigemPresenca.MANUAL);
            if (facial && item.confianca() != null) {
                nova.setConfianca(item.confianca().setScale(4, RoundingMode.HALF_UP));
            }
            presencas.save(nova);
            adicionadas++;
        }

        int removidas = 0;
        if (req.removerAusentes()) {
            for (Presenca p : existentes.values()) {
                if (!enviados.containsKey(p.getAluno().getId())) {
                    presencas.delete(p);
                    removidas++;
                }
            }
        }
        int total = existentes.size() + adicionadas - removidas;
        return new SalvarPresencaResponse(data, adicionadas, removidas, total);
    }

    @Transactional
    public void remover(Long id) {
        if (!presencas.existsById(id)) {
            throw new NaoEncontradoException("Presença", id);
        }
        presencas.deleteById(id);
    }

    /**
     * Reconhecimento facial: recebe os vetores dos rostos detectados na foto da turma e diz
     * quem é quem. NÃO grava nada: a presença só é registrada no "Salvar presença".
     */
    @Transactional(readOnly = true)
    public ReconhecimentoResponse reconhecer(ReconhecimentoRequest req) {
        LocalDate data = req.data() != null ? req.data() : LocalDate.now(clock);
        double limite = props.reconhecimento().distanciaMaxima();

        // Só [id, vetor] dos alunos com consentimento — as fotos não são carregadas.
        List<FaceMatcher.Candidato> candidatos = midias.findDescritoresReconheciveis().stream()
                .map(r -> new FaceMatcher.Candidato((Long) r[0], (double[]) r[1]))
                .toList();

        FaceMatcher.Resultado resultado = FaceMatcher.casar(req.rostos(), candidatos, limite);

        Map<Long, Aluno> porId = alunos.findAllById(resultado.matches().stream().map(FaceMatcher.Match::alunoId).toList())
                .stream().collect(Collectors.toMap(Aluno::getId, Function.identity()));
        java.util.Set<Long> jaPresentes = presencas.findPeriodo(data, data).stream()
                .map(p -> p.getAluno().getId()).collect(Collectors.toSet());

        List<RostoReconhecido> reconhecidos = new ArrayList<>();
        for (FaceMatcher.Match m : resultado.matches()) {
            Aluno aluno = porId.get(m.alunoId());
            reconhecidos.add(new RostoReconhecido(m.indiceRosto(), aluno.getId(), aluno.getNome(), aluno.getFaixa(),
                    aluno.getGraus(), aluno.getFotoMiniatura(), round(m.distancia()), round(m.confianca(limite)),
                    jaPresentes.contains(aluno.getId())));
        }
        return new ReconhecimentoResponse(data, req.rostos().size(), candidatos.size(),
                reconhecidos, resultado.rostosNaoReconhecidos());
    }

    @Transactional(readOnly = true)
    public List<TotalDia> totaisPorDia(LocalDate inicio, LocalDate fim) {
        return presencas.totaisPorDia(inicio, fim).stream()
                .map(r -> new TotalDia(toLocalDate(r[0]), ((Number) r[1]).longValue()))
                .toList();
    }

    private static LocalDate toLocalDate(Object o) {
        return o instanceof LocalDate ld ? ld : ((Date) o).toLocalDate();
    }

    private static double round(double v) {
        return Math.round(v * 10_000d) / 10_000d;
    }
}
