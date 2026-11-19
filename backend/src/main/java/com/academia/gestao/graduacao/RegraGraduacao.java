package com.academia.gestao.graduacao;

import com.academia.gestao.aluno.Faixa;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/** Frequência mínima exigida, por faixa atual, para o aluno ficar "apto" ao próximo grau/faixa. */
@Entity
@Table(name = "regras_graduacao")
@Getter
@Setter
public class RegraGraduacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Faixa faixa;

    @Column(name = "presencas_minimas")
    private int presencasMinimas;

    @Column(name = "meses_minimos")
    private int mesesMinimos;

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;
}
