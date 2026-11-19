package com.academia.gestao.presenca;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PresencaRepository extends JpaRepository<Presenca, Long> {

    @Query("""
            select p from Presenca p join fetch p.aluno
            where p.data between :inicio and :fim
            order by p.data desc, p.aluno.nome
            """)
    List<Presenca> findPeriodo(LocalDate inicio, LocalDate fim);

    Optional<Presenca> findByAlunoIdAndData(Long alunoId, LocalDate data);

    long countByData(LocalDate data);

    long countByAlunoIdAndDataGreaterThanEqual(Long alunoId, LocalDate desde);

    /** Presenças por aluno a partir de cada data inicial — usado no cálculo de aptidão à graduação. */
    @Query(value = """
            SELECT a.id AS aluno_id, count(p.id) AS total
            FROM alunos a
            LEFT JOIN presencas p
              ON p.aluno_id = a.id AND p.data >= COALESCE(a.data_ultima_graduacao, a.data_inicio)
            WHERE a.ativo
            GROUP BY a.id
            """, nativeQuery = true)
    List<Object[]> contarDesdeUltimaGraduacao();

    @Query(value = """
            SELECT d::date AS dia, count(p.id) AS total
            FROM generate_series(CAST(:inicio AS date), CAST(:fim AS date), interval '1 day') d
            LEFT JOIN presencas p ON p.data = d::date
            GROUP BY d ORDER BY d
            """, nativeQuery = true)
    List<Object[]> totaisPorDia(LocalDate inicio, LocalDate fim);
}
