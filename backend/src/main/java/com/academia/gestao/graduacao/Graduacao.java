package com.academia.gestao.graduacao;

import com.academia.gestao.aluno.Aluno;
import com.academia.gestao.aluno.Faixa;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "graduacoes")
@Getter
@Setter
public class Graduacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aluno_id")
    private Aluno aluno;

    @Enumerated(EnumType.STRING)
    @Column(name = "faixa_anterior")
    private Faixa faixaAnterior;

    @Column(name = "graus_anterior")
    private int grausAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "faixa_nova")
    private Faixa faixaNova;

    @Column(name = "graus_nova")
    private int grausNova;

    private LocalDate data;

    @Column(name = "presencas_no_periodo")
    private int presencasNoPeriodo;

    private String observacao;

    @Column(name = "criado_em", insertable = false, updatable = false)
    private OffsetDateTime criadoEm;
}
