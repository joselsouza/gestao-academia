package com.academia.gestao.financeiro;

import com.academia.gestao.aluno.Aluno;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "mensalidades")
@Getter
@Setter
public class Mensalidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aluno_id")
    private Aluno aluno;

    /** Sempre o dia 1 do mês de referência. */
    private LocalDate competencia;

    private BigDecimal valor;
    private LocalDate vencimento;

    @Enumerated(EnumType.STRING)
    private StatusMensalidade status = StatusMensalidade.PENDENTE;

    @Column(name = "data_pagamento")
    private LocalDate dataPagamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento")
    private FormaPagamento formaPagamento;

    private String observacao;

    /** Pagamento no Asaas (pay_...) com o link enviado ao aluno. */
    @Column(name = "asaas_pagamento_id")
    private String asaasPagamentoId;

    @Column(name = "link_pagamento")
    private String link;

    @Column(name = "criado_em", insertable = false, updatable = false)
    private OffsetDateTime criadoEm;

    public boolean isAtrasada(LocalDate hoje) {
        return status == StatusMensalidade.PENDENTE && vencimento.isBefore(hoje);
    }
}
