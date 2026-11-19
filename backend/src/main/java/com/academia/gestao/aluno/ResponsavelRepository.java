package com.academia.gestao.aluno;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResponsavelRepository extends JpaRepository<Responsavel, Long> {

    Optional<Responsavel> findFirstByCpf(String cpf);

}
