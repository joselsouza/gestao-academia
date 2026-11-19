package com.academia.gestao.aluno;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AlunoMidiaRepository extends JpaRepository<AlunoMidia, Long> {

    /** [id, vetor facial] dos alunos aptos ao reconhecimento — sem trazer as fotos. */
    @Query("""
            select m.id, m.faceDescriptor from AlunoMidia m
            where m.faceDescriptor is not null
              and m.id in (select a.id from Aluno a where a.ativo = true and a.consentimentoBiometria = true)
            """)
    List<Object[]> findDescritoresReconheciveis();
}
