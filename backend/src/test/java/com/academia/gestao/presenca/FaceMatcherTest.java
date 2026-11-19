package com.academia.gestao.presenca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FaceMatcherTest {

    private static double[] vetor(double base) {
        double[] v = new double[128];
        java.util.Arrays.fill(v, base);
        return v;
    }

    @Test
    void casaCadaRostoComOAlunoMaisProximo() {
        var candidatos = List.of(
                new FaceMatcher.Candidato(1L, vetor(0.10)),
                new FaceMatcher.Candidato(2L, vetor(0.30)));
        // rosto 0 ~ aluno 2, rosto 1 ~ aluno 1, rosto 2 é desconhecido
        var rostos = List.of(vetor(0.301), vetor(0.102), vetor(0.90));

        var r = FaceMatcher.casar(rostos, candidatos, 0.5);

        assertEquals(2, r.matches().size());
        assertEquals(2L, r.matches().get(0).alunoId());
        assertEquals(1L, r.matches().get(1).alunoId());
        assertEquals(List.of(2), r.rostosNaoReconhecidos());
    }

    @Test
    void alunoNaoEhMarcadoPorDoisRostos() {
        var candidatos = List.of(new FaceMatcher.Candidato(1L, vetor(0.10)));
        var rostos = List.of(vetor(0.105), vetor(0.101));

        var r = FaceMatcher.casar(rostos, candidatos, 0.5);

        assertEquals(1, r.matches().size());
        assertEquals(1, r.matches().get(0).indiceRosto()); // o mais próximo vence
        assertEquals(List.of(0), r.rostosNaoReconhecidos());
        assertTrue(r.matches().get(0).confianca(0.5) > 0.9);
    }
}
