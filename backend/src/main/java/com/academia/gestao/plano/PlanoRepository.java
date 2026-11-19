package com.academia.gestao.plano;

import com.academia.gestao.aluno.Faixa;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlanoRepository extends JpaRepository<Plano, Long> {
    List<Plano> findAllByOrderByAtivoDescNomeAsc();

    /** Plano ativo vinculado à faixa (no máximo um, pela chave da tabela plano_faixas). */
    @Query("select p from Plano p join p.faixas f where f = :faixa and p.ativo = true")
    Optional<Plano> findAtivoDaFaixa(Faixa faixa);

    /** Outro plano que já usa a faixa (para validar duplicidade). */
    @Query("select p from Plano p join p.faixas f where f = :faixa and (:id is null or p.id <> :id)")
    List<Plano> findOutrosComFaixa(Faixa faixa, Long id);
}
