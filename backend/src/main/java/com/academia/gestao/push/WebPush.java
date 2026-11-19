package com.academia.gestao.push;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Criptografia do Web Push sem bibliotecas extras: conteúdo "aes128gcm" (RFC 8291 / RFC 8188)
 * e cabeçalho de autorização VAPID (RFC 8292, JWT ES256).
 */
public final class WebPush {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    /** Tamanho do registro (rs): a notificação cabe num registro só. */
    private static final int RS = 4096;
    static final ECParameterSpec P256 = parametrosP256();

    private WebPush() {}

    public record ChavesVapid(ECPublicKey publica, ECPrivateKey privada) {
        /** Chave pública no formato que o navegador espera (applicationServerKey). */
        public String publicaBase64() {
            return b64(pontoBruto(publica));
        }

        public String privadaBase64() {
            return b64(inteiro32(privada.getS()));
        }
    }

    public static ChavesVapid gerarChaves() {
        KeyPair par = novoPar();
        return new ChavesVapid((ECPublicKey) par.getPublic(), (ECPrivateKey) par.getPrivate());
    }

    public static ChavesVapid carregarChaves(String publicaB64, String privadaB64) {
        try {
            KeyFactory kf = KeyFactory.getInstance("EC");
            ECPrivateKey privada = (ECPrivateKey) kf.generatePrivate(
                    new ECPrivateKeySpec(new BigInteger(1, b64d(privadaB64)), P256));
            return new ChavesVapid(chavePublica(b64d(publicaB64)), privada);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Chaves VAPID inválidas", e);
        }
    }

    /** Cifra a mensagem para a inscrição do navegador (p256dh e auth em base64url). */
    public static byte[] cifrar(byte[] mensagem, String p256dhB64, String authB64) {
        return cifrar(mensagem, p256dhB64, authB64, novoPar(), aleatorio(16));
    }

    static byte[] cifrar(byte[] mensagem, String p256dhB64, String authB64, KeyPair efemera, byte[] salt) {
        try {
            byte[] uaPublica = b64d(p256dhB64);
            byte[] auth = b64d(authB64);
            byte[] asPublica = pontoBruto((ECPublicKey) efemera.getPublic());

            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(efemera.getPrivate());
            ka.doPhase(chavePublica(uaPublica), true);
            byte[] segredoEcdh = ka.generateSecret();

            // IKM = HKDF(auth, ecdh, "WebPush: info" || 0x00 || ua_public || as_public, 32)
            byte[] prkChave = hmac(auth, segredoEcdh);
            byte[] info = concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[] {0}, uaPublica, asPublica);
            byte[] ikm = hmac(prkChave, concat(info, new byte[] {1}));

            byte[] prk = hmac(salt, ikm);
            byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII), new byte[] {0, 1})), 16);
            byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII), new byte[] {0, 1})), 12);

            if (mensagem.length + 1 + 16 > RS) {
                throw new IllegalArgumentException("Mensagem grande demais para uma notificação");
            }
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] cifrado = aes.doFinal(concat(mensagem, new byte[] {2})); // 0x02 = último registro

            ByteBuffer corpo = ByteBuffer.allocate(16 + 4 + 1 + asPublica.length + cifrado.length);
            corpo.put(salt).putInt(RS).put((byte) asPublica.length).put(asPublica).put(cifrado);
            return corpo.array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar a notificação", e);
        }
    }

    /** Valor do cabeçalho Authorization: "vapid t=<jwt>, k=<chave pública>". */
    public static String autorizacao(String endpoint, ChavesVapid chaves, String contato, Instant agora) {
        URI uri = URI.create(endpoint);
        String audiencia = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).type(com.nimbusds.jose.JOSEObjectType.JWT).build(),
                    new JWTClaimsSet.Builder()
                            .audience(audiencia)
                            .expirationTime(Date.from(agora.plusSeconds(12 * 3600)))
                            .subject(contato)
                            .build());
            jwt.sign(new ECDSASigner(chaves.privada()));
            return "vapid t=" + jwt.serialize() + ", k=" + chaves.publicaBase64();
        } catch (JOSEException e) {
            throw new IllegalStateException("Falha ao assinar o VAPID", e);
        }
    }

    // ------------------------------------------------------------------ utilitários

    static KeyPair novoPar() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            return g.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static ECPublicKey chavePublica(byte[] ponto) throws GeneralSecurityException {
        if (ponto.length != 65 || ponto[0] != 4) {
            throw new GeneralSecurityException("Chave pública P-256 inválida");
        }
        ECPoint w = new ECPoint(new BigInteger(1, Arrays.copyOfRange(ponto, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(ponto, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, P256));
    }

    static byte[] pontoBruto(ECPublicKey k) {
        return concat(new byte[] {4}, inteiro32(k.getW().getAffineX()), inteiro32(k.getW().getAffineY()));
    }

    private static byte[] inteiro32(BigInteger v) {
        byte[] b = v.toByteArray();
        if (b.length == 32) {
            return b;
        }
        byte[] r = new byte[32];
        int n = Math.min(b.length, 32);
        System.arraycopy(b, b.length - n, r, 32 - n, n);
        return r;
    }

    private static byte[] hmac(byte[] chave, byte[] dados) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(chave, "HmacSHA256"));
        return mac.doFinal(dados);
    }

    static byte[] concat(byte[]... partes) {
        int total = 0;
        for (byte[] p : partes) {
            total += p.length;
        }
        ByteBuffer b = ByteBuffer.allocate(total);
        for (byte[] p : partes) {
            b.put(p);
        }
        return b.array();
    }

    private static byte[] aleatorio(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    static String b64(byte[] b) {
        return B64.encodeToString(b);
    }

    static byte[] b64d(String s) {
        return B64D.decode(s.trim().replace('+', '-').replace('/', '_').replace("=", ""));
    }

    private static ECParameterSpec parametrosP256() {
        try {
            AlgorithmParameters p = AlgorithmParameters.getInstance("EC");
            p.init(new ECGenParameterSpec("secp256r1"));
            return p.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
