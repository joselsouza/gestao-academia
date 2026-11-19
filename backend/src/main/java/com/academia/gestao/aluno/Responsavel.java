package com.academia.gestao.aluno;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/** Responsável legal por alunos menores de idade. Pode estar ligado a mais de um aluno (irmãos). */
@Entity
@Table(name = "responsaveis")
@Getter
@Setter
public class Responsavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    /** Só dígitos. */
    private String cpf;
    private String telefone;
    private String documento;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    private String email;

    @Enumerated(EnumType.STRING)
    private Sexo sexo;

    // As colunas de endereço (cep, logradouro...) existem na tabela, mas não são mais usadas.

    /** Id do cliente no Asaas (cus_...), criado na primeira cobrança de um aluno dele. */
    @Column(name = "asaas_customer_id")
    private String asaasCustomerId;

    @Column(name = "criado_em", insertable = false, updatable = false)
    private OffsetDateTime criadoEm;
}
