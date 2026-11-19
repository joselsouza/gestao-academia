package com.academia.gestao.cobranca;

import com.academia.gestao.aluno.Aluno;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/** Cobrança emitida no Asaas: pagamento único ou assinatura (recorrente). */
@Entity
@Table(name = "cobrancas")
@Getter
@Setter
public class Cobranca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aluno_id")
    private Aluno aluno;

    @Enumerated(EnumType.STRING)
    private TipoCobranca tipo;

    @Enumerated(EnumType.STRING)
    private FormaCobranca forma;

    @Enumerated(EnumType.STRING)
    private CicloCobranca ciclo;

    private String descricao;
    private BigDecimal valor;

    /** UNICA: id do pagamento. RECORRENTE: id da assinatura. */
    @Column(name = "asaas_id")
    private String asaasId;

    /** Parcela atual (a próxima em aberto ou, se não houver, a última). */
    @Column(name = "asaas_pagamento_id")
    private String asaasPagamentoId;

    private LocalDate vencimento;

    @Column(name = "proximo_vencimento")
    private LocalDate proximoVencimento;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Enumerated(EnumType.STRING)
    private StatusCobranca status = StatusCobranca.PENDENTE;

    @Column(name = "link_pagamento")
    private String link;

    @Column(name = "data_pagamento")
    private LocalDate dataPagamento;

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;

    @Column(name = "criado_em", insertable = false, updatable = false)
    private OffsetDateTime criadoEm;
}
