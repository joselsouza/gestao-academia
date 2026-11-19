package com.academia.gestao.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** Cifra como o servidor e decifra como o navegador (RFC 8291), com chaves geradas na hora. */
class WebPushTest {

    @Test
    void navegadorConsegueDecifrar() throws Exception {
        KeyPair navegador = WebPush.novoPar();
        byte[] auth = new byte[16];
        new java.security.SecureRandom().nextBytes(auth);
        String p256dh = WebPush.b64(WebPush.pontoBruto((ECPublicKey) navegador.getPublic()));
        String mensagem = "{\"titulo\":\"Pagamento confirmado: João Silva\"}";

        byte[] corpo = WebPush.cifrar(mensagem.getBytes(StandardCharsets.UTF_8), p256dh, WebPush.b64(auth));

        assertThat(new String(decifrar(corpo, navegador, auth), StandardCharsets.UTF_8)).isEqualTo(mensagem);
    }

    @Test
    void chavesVapidSobrevivemAoBanco() {
        WebPush.ChavesVapid c = WebPush.gerarChaves();
        WebPush.ChavesVapid lidas = WebPush.carregarChaves(c.publicaBase64(), c.privadaBase64());
        assertThat(lidas.publicaBase64()).isEqualTo(c.publicaBase64()).hasSize(87);
        assertThat(lidas.privada().getS()).isEqualTo(c.privada().getS());
    }

    @Test
    void autorizacaoTemJwtEChavePublica() {
        WebPush.ChavesVapid c = WebPush.gerarChaves();
        String a = WebPush.autorizacao("https://fcm.googleapis.com/fcm/send/abc", c, "https://academia.pages.dev",
                java.time.Instant.parse("2026-11-12T12:00:00Z"));
        assertThat(a).startsWith("vapid t=ey").endsWith(", k=" + c.publicaBase64());
        String claims = new String(java.util.Base64.getUrlDecoder().decode(a.substring(8).split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"aud\":\"https://fcm.googleapis.com\"").contains("\"sub\":\"https://academia.pages.dev\"");
    }

    /** O que o navegador faz ao receber (RFC 8291, lado do "user agent"). */
    private static byte[] decifrar(byte[] corpo, KeyPair navegador, byte[] auth) throws Exception {
        ByteBuffer b = ByteBuffer.wrap(corpo);
        byte[] salt = new byte[16];
        b.get(salt);
        assertThat(b.getInt()).isEqualTo(4096);
        byte[] asPublica = new byte[b.get()];
        b.get(asPublica);
        byte[] cifrado = new byte[b.remaining()];
        b.get(cifrado);

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(navegador.getPrivate());
        ka.doPhase(WebPush.chavePublica(asPublica), true);
        byte[] uaPublica = WebPush.pontoBruto((ECPublicKey) navegador.getPublic());
        byte[] prkChave = hmac(auth, ka.generateSecret());
        byte[] ikm = hmac(prkChave, WebPush.concat("WebPush: info".getBytes(), new byte[] {0}, uaPublica, asPublica, new byte[] {1}));
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, WebPush.concat("Content-Encoding: aes128gcm".getBytes(), new byte[] {0, 1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, WebPush.concat("Content-Encoding: nonce".getBytes(), new byte[] {0, 1})), 12);
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] claro = aes.doFinal(cifrado);
        assertThat(claro[claro.length - 1]).isEqualTo((byte) 2);
        return Arrays.copyOf(claro, claro.length - 1);
    }

    private static byte[] hmac(byte[] chave, byte[] dados) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(chave, "HmacSHA256"));
        return mac.doFinal(dados);
    }
}
