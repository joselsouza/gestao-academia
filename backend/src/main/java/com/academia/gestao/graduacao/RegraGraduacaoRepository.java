package com.academia.gestao.graduacao;

import com.academia.gestao.aluno.Faixa;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegraGraduacaoRepository extends JpaRepository<RegraGraduacao, Long> {
    Optional<RegraGraduacao> findByFaixa(Faixa faixa);
}
