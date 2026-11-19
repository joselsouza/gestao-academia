package com.academia.gestao.cobranca;

/** Periodicidade da cobrança recorrente (assinatura no Asaas). */
public enum CicloCobranca {
    MENSAL("MONTHLY"), TRIMESTRAL("QUARTERLY"), SEMESTRAL("SEMIANNUALLY"), ANUAL("YEARLY");

    final String asaas;

    CicloCobranca(String asaas) {
        this.asaas = asaas;
    }
}
