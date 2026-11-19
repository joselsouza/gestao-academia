package com.academia.gestao.common;

/** Regra de negócio violada: vira HTTP 400 com a mensagem para o usuário. */
public class NegocioException extends RuntimeException {
    public NegocioException(String mensagem) {
        super(mensagem);
    }
}
