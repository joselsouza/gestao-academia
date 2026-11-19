package com.academia.gestao.presenca;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Casa os rostos encontrados na foto da turma com os vetores faciais cadastrados.
 *
 * <p>Cada rosto é um vetor de 128 números (face-api / FaceRecognitionNet). Rostos da mesma pessoa
 * ficam próximos no espaço euclidiano (distância &lt; ~0.6). A atribuição é gulosa e 1-para-1:
 * os pares mais próximos são fixados primeiro, então um aluno nunca é marcado por dois rostos
 * e um rosto nunca marca dois alunos.
 */
public final class FaceMatcher {

    public record Candidato(Long alunoId, double[] descritor) {}

    public record Match(int indiceRosto, Long alunoId, double distancia) {
        /** Confiança aproximada em 0..1 (1 = idêntico; 0 = no limite de aceitação). */
        public double confianca(double distanciaMaxima) {
            return Math.max(0, Math.min(1, 1 - distancia / distanciaMaxima));
        }
    }

    public record Resultado(List<Match> matches, List<Integer> rostosNaoReconhecidos) {}

    private FaceMatcher() {}

    public static Resultado casar(List<double[]> rostos, List<Candidato> candidatos, double distanciaMaxima) {
        record Par(int rosto, int candidato, double distancia) {}

        List<Par> pares = new ArrayList<>();
        for (int r = 0; r < rostos.size(); r++) {
            for (int c = 0; c < candidatos.size(); c++) {
                double d = distancia(rostos.get(r), candidatos.get(c).descritor());
                if (d <= distanciaMaxima) {
                    pares.add(new Par(r, c, d));
                }
            }
        }
        pares.sort(Comparator.comparingDouble(Par::distancia));

        Set<Integer> rostosUsados = new HashSet<>();
        Set<Integer> candidatosUsados = new HashSet<>();
        List<Match> matches = new ArrayList<>();
        for (Par p : pares) {
            if (rostosUsados.contains(p.rosto()) || candidatosUsados.contains(p.candidato())) {
                continue;
            }
            rostosUsados.add(p.rosto());
            candidatosUsados.add(p.candidato());
            matches.add(new Match(p.rosto(), candidatos.get(p.candidato()).alunoId(), p.distancia()));
        }
        matches.sort(Comparator.comparingInt(Match::indiceRosto));

        List<Integer> naoReconhecidos = new ArrayList<>();
        for (int r = 0; r < rostos.size(); r++) {
            if (!rostosUsados.contains(r)) {
                naoReconhecidos.add(r);
            }
        }
        return new Resultado(matches, naoReconhecidos);
    }

    static double distancia(double[] a, double[] b) {
        if (a.length != b.length) {
            return Double.MAX_VALUE;
        }
        double soma = 0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            soma += diff * diff;
        }
        return Math.sqrt(soma);
    }
}
