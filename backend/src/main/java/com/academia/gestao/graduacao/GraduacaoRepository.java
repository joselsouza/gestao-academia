package com.academia.gestao.graduacao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GraduacaoRepository extends JpaRepository<Graduacao, Long> {

    @Query("select g from Graduacao g join fetch g.aluno order by g.data desc, g.id desc")
    List<Graduacao> findHistorico();

    @Query("select g from Graduacao g join fetch g.aluno where g.aluno.id = :alunoId order by g.data desc, g.id desc")
    List<Graduacao> findHistoricoDoAluno(Long alunoId);
}
