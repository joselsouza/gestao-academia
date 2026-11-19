package com.academia.gestao.common;

public class NaoEncontradoException extends RuntimeException {
    public NaoEncontradoException(String recurso, Object id) {
        super(recurso + " não encontrado(a): " + id);
    }
}
