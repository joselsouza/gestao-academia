package com.academia.gestao.aluno;

/** Faixas do Jiu-Jitsu (infantis + adultas), na ordem de progressão. */
public enum Faixa {
    BRANCA, CINZA, AMARELA, LARANJA, VERDE, AZUL, ROXA, MARROM, PRETA;

    public static final int GRAUS_MAXIMOS = 4;
    public static final int GRAUS_MAXIMOS_PRETA = 6;

    public int grausMaximos() {
        return this == PRETA ? GRAUS_MAXIMOS_PRETA : GRAUS_MAXIMOS;
    }

    public Faixa proxima() {
        return this == PRETA ? PRETA : values()[ordinal() + 1];
    }
}
