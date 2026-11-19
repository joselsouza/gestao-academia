package com.academia.gestao.presenca;

import com.academia.gestao.aluno.Aluno;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "presencas")
@Getter
@Setter
public class Presenca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aluno_id")
    private Aluno aluno;

    private LocalDate data;

    @Enumerated(EnumType.STRING)
    private OrigemPresenca origem;

    /** 0..1 — só preenchido no reconhecimento facial. */
    private BigDecimal confianca;

    @Column(name = "criado_em", insertable = false, updatable = false)
    private OffsetDateTime criadoEm;
}
