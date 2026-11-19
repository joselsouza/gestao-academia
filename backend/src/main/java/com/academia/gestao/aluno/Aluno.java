package com.academia.gestao.aluno;

import com.academia.gestao.plano.Plano;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Formula;

/**
 * Cadastro "leve" do aluno, usado em todas as listas e relatórios.
 * A foto completa e o vetor facial ficam em {@link AlunoMidia} (mesma tabela) e só são
 * carregados quando realmente necessários — isso deixa as telas bem mais rápidas.
 */
@Entity
@Table(name = "alunos")
@Getter
@Setter
public class Aluno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private String telefone;

    /** Só dígitos. Exigido pelo Asaas para emitir cobranças. */
    private String cpf;
    private String email;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Enumerated(EnumType.STRING)
    private Sexo sexo;

    /** Responsável legal (menores de 18 anos). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_id")
    private Responsavel responsavel;

    /** Id do cliente no Asaas (cus_...), criado na primeira cobrança. */
    @Column(name = "asaas_customer_id")
    private String asaasCustomerId;

    /** Miniatura (~96px) para avatares nas listas. */
    @Column(name = "foto_miniatura", columnDefinition = "text")
    private String fotoMiniatura;

    @Column(name = "consentimento_biometria")
    private boolean consentimentoBiometria;

    /** Somente leitura: indica se existe vetor facial, sem carregar o vetor. */
    @Formula("(face_descriptor is not null)")
    private boolean rostoCadastrado;

    @Enumerated(EnumType.STRING)
    private Faixa faixa = Faixa.BRANCA;

    private int graus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plano_id")
    private Plano plano;

    @Column(name = "dia_vencimento")
    private int diaVencimento = 10;

    @Column(name = "data_inicio")
    private LocalDate dataInicio;

    @Column(name = "data_ultima_graduacao")
    private LocalDate dataUltimaGraduacao;

    private boolean ativo = true;

    @Column(name = "criado_em", insertable = false, updatable = false)
    private OffsetDateTime criadoEm;

    /** Início da contagem de frequência para a próxima graduação. */
    public LocalDate inicioPeriodoGraduacao() {
        return dataUltimaGraduacao != null ? dataUltimaGraduacao : dataInicio;
    }

    /** Idade completa em {@code hoje}, ou null se a data de nascimento não foi informada. */
    public Integer idade(LocalDate hoje) {
        return dataNascimento == null ? null : java.time.Period.between(dataNascimento, hoje).getYears();
    }

    /** Com responsável cadastrado, as cobranças (Asaas) saem no nome e CPF do responsável. */
    public boolean isCobradoDoResponsavel() {
        return responsavel != null;
    }

    /** CPF de quem paga: do responsável, se houver; senão, do próprio aluno. */
    public String cpfDoPagador() {
        return responsavel != null ? responsavel.getCpf() : cpf;
    }

    public boolean isBolsista() {
        return plano != null && plano.isIsento();
    }

    public boolean isReconhecivel() {
        return ativo && consentimentoBiometria && rostoCadastrado;
    }
}
