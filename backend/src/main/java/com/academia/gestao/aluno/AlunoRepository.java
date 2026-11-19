package com.academia.gestao.aluno;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AlunoRepository extends JpaRepository<Aluno, Long> {

    @Query("select a from Aluno a left join fetch a.plano left join fetch a.responsavel order by a.nome")
    List<Aluno> findAllComPlano();

    @Query("select a from Aluno a left join fetch a.plano where a.ativo = true order by a.nome")
    List<Aluno> findAtivosComPlano();

    List<Aluno> findByCpf(String cpf);

    /** Outro aluno ativo (diferente de :id; :id nulo = aluno novo) já usa este CPF? */
    @Query("select a from Aluno a where a.ativo = true and a.cpf = :cpf and (:id is null or a.id <> :id)")
    List<Aluno> findAtivosComCpf(String cpf, Long id);

    long countByPlanoId(Long planoId);

    long countByPlanoIdAndAtivoTrue(Long planoId);

    long countByAtivoTrue();

    @Query("select count(a) from Aluno a where a.ativo = true and a.plano.tipo = com.academia.gestao.plano.TipoPlano.BOLSISTA")
    long countBolsistasAtivos();
}
