package com.academia.gestao.aluno;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Colunas pesadas do aluno (mesma tabela "alunos"): foto completa e vetor facial.
 * Nunca é criada por aqui — a linha nasce com {@link Aluno}; esta entidade só lê e atualiza.
 */
@Entity
@Table(name = "alunos")
@Getter
@Setter
public class AlunoMidia {

    @Id
    private Long id;

    /** Foto em data URL (JPEG já redimensionado no front). */
    @Column(columnDefinition = "text")
    private String foto;

    /** Vetor facial de 128 posições gerado no navegador (face-api) a partir da foto. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "face_descriptor", columnDefinition = "float8[]")
    private double[] faceDescriptor;
}
