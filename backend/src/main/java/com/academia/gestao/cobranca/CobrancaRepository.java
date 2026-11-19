package com.academia.gestao.cobranca;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CobrancaRepository extends JpaRepository<Cobranca, Long> {

    @Query("select c from Cobranca c join fetch c.aluno a left join fetch a.responsavel order by c.vencimento nulls last, c.aluno.nome")
    List<Cobranca> findAllComAluno();

    /** Cobranças que ainda podem mudar de situação no Asaas. */
    @Query("""
            select c from Cobranca c join fetch c.aluno a left join fetch a.responsavel
            where c.status not in (com.academia.gestao.cobranca.StatusCobranca.ENCERRADA,
                                   com.academia.gestao.cobranca.StatusCobranca.ESTORNADA)
              and not (c.tipo = com.academia.gestao.cobranca.TipoCobranca.UNICA
                       and c.status = com.academia.gestao.cobranca.StatusCobranca.PAGA)
            """)
    List<Cobranca> findSincronizaveis();

    Optional<Cobranca> findByAsaasId(String asaasId);

    /** Cobranças de um aluno (portal do aluno). */
    @Query("select c from Cobranca c where c.aluno.id = :alunoId order by c.vencimento desc nulls last, c.id desc")
    List<Cobranca> findByAluno(Long alunoId);
}
