package com.academia.gestao.push;

/**
 * O que aparece na notificação. {@code url} é a tela aberta ao tocar; {@code tag} agrupa avisos
 * do mesmo assunto (um novo substitui o anterior com a mesma tag).
 */
public record Notificacao(String titulo, String corpo, String url, String tag) {

    String json() {
        return "{\"titulo\":" + texto(titulo) + ",\"corpo\":" + texto(corpo)
                + ",\"url\":" + texto(url) + ",\"tag\":" + texto(tag) + "}";
    }

    private static String texto(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }
}
