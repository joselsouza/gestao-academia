package com.academia.gestao.plano;

import jakarta.persistence.*;
import java.math.BigDecimal;
import com.academia.gestao.aluno.Faixa;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "planos")
@Getter
@Setter
public class Plano {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    @Enumerated(EnumType.STRING)
    private TipoPlano tipo;

    private BigDecimal valor = BigDecimal.ZERO;
    private String descricao;
    private boolean ativo = true;

    /** Faixas que usam este plano automaticamente (cadastro e graduação). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plano_faixas", joinColumns = @JoinColumn(name = "plano_id"))
    @Column(name = "faixa")
    @Enumerated(EnumType.STRING)
    private Set<Faixa> faixas = new HashSet<>();

    @Column(name = "criado_em", insertable = false, updatable = false)
    private OffsetDateTime criadoEm;

    /** Faixas em ordem de progressão (para exibir). */
    public Set<Faixa> faixasOrdenadas() {
        return faixas.isEmpty() ? EnumSet.noneOf(Faixa.class) : EnumSet.copyOf(faixas);
    }

    public boolean isIsento() {
        return tipo == TipoPlano.BOLSISTA;
    }
}
