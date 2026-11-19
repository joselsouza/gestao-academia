package com.academia.gestao.portal;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Acesso do aluno ao portal. senhaHash nula = o aluno ainda usa a senha padrão. */
@Entity
@Table(name = "aluno_acessos")
@Getter
@Setter
@NoArgsConstructor
public class AlunoAcesso {

    @Id
    @Column(name = "aluno_id")
    private Long alunoId;

    @Column(name = "senha_hash")
    private String senhaHash;

    @Column(name = "senha_alterada_em")
    private OffsetDateTime senhaAlteradaEm;

    @Column(name = "ultimo_login")
    private OffsetDateTime ultimoLogin;

    public AlunoAcesso(Long alunoId) {
        this.alunoId = alunoId;
    }

    public boolean isSenhaPadrao() {
        return senhaHash == null;
    }
}
