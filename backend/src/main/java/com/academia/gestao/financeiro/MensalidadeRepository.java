package com.academia.gestao.financeiro;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MensalidadeRepository extends JpaRepository<Mensalidade, Long> {

    @Query("select m from Mensalidade m join fetch m.aluno a left join fetch a.responsavel where m.competencia = :competencia order by m.aluno.nome")
    List<Mensalidade> findByCompetencia(LocalDate competencia);

    @Query("""
            select m from Mensalidade m join fetch m.aluno a left join fetch a.responsavel
            where m.status = com.academia.gestao.financeiro.StatusMensalidade.PENDENTE and m.vencimento < :hoje
            order by m.vencimento, m.aluno.nome
            """)
    List<Mensalidade> findAtrasadas(LocalDate hoje);

    @Query("select m from Mensalidade m join fetch m.aluno a left join fetch a.responsavel where m.aluno.id = :alunoId order by m.competencia desc")
    List<Mensalidade> findByAluno(Long alunoId);

    @Query("""
            select count(distinct m.aluno.id) from Mensalidade m
            where m.status = com.academia.gestao.financeiro.StatusMensalidade.PENDENTE and m.vencimento < :hoje
            """)
    long countAlunosInadimplentes(LocalDate hoje);

    @Query("select m from Mensalidade m join fetch m.aluno a left join fetch a.responsavel where m.asaasPagamentoId = :id")
    Optional<Mensalidade> findByAsaasPagamentoId(String id);

    /** Pendentes com link no Asaas: as que ainda podem ser pagas por lá. */
    @Query("""
            select m from Mensalidade m join fetch m.aluno a left join fetch a.responsavel
            where m.status = com.academia.gestao.financeiro.StatusMensalidade.PENDENTE
              and m.asaasPagamentoId is not null
            """)
    List<Mensalidade> findPendentesNoAsaas();

    boolean existsByAlunoIdAndCompetencia(Long alunoId, LocalDate competencia);
}
