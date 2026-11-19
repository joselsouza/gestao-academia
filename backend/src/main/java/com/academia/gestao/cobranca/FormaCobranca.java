package com.academia.gestao.cobranca;

/** Forma de pagamento oferecida ao aluno; INDEFINIDA = o aluno escolhe (PIX, cartão ou boleto). */
public enum FormaCobranca {
    INDEFINIDA("UNDEFINED"), PIX("PIX"), CARTAO("CREDIT_CARD"), BOLETO("BOLETO");

    final String asaas;

    FormaCobranca(String asaas) {
        this.asaas = asaas;
    }
}
