package com.academia.gestao.aluno;

import com.academia.gestao.common.NegocioException;

/** Normalização de CPF/CNPJ: guarda só os dígitos. */
public final class Documentos {

    private Documentos() {}

    /** Devolve só os dígitos (11 = CPF, 14 = CNPJ) ou null se vazio. */
    public static String cpfOuNull(String valor) {
        if (valor == null) {
            return null;
        }
        String d = valor.replaceAll("\\D", "");
        if (d.isEmpty()) {
            return null;
        }
        if (d.length() != 11 && d.length() != 14) {
            throw new NegocioException("CPF deve ter 11 dígitos (ou CNPJ com 14).");
        }
        return d;
    }
}
