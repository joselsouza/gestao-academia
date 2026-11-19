package com.academia.gestao.security;

import com.academia.gestao.config.AppProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/login", "/api/aluno/auth/login", "/api/health", "/error").permitAll()
                        // Chave pública VAPID das notificações (é pública por definição)
                        .requestMatchers(HttpMethod.GET, "/api/push/chave").permitAll()
                        // Webhook do Asaas: autenticado pelo token próprio (asaas-access-token)
                        .requestMatchers(HttpMethod.POST, "/api/asaas/webhook").permitAll()
                        // claim "scope" do JWT = perfil -> autoridade SCOPE_<perfil>
                        // Portal do aluno: só o perfil ALUNO, e o aluno só enxerga o que é dele.
                        .requestMatchers("/api/aluno/**").hasAuthority("SCOPE_ALUNO")
                        .requestMatchers("/api/usuarios/**").hasAuthority("SCOPE_ADMIN")
                        // Todo o resto é da equipe: um token de aluno NÃO entra aqui.
                        .anyRequest().hasAnyAuthority("SCOPE_ADMIN", "SCOPE_INSTRUTOR"))
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /** HS256 exige chave de 256 bits: derivamos do segredo configurado via SHA-256. */
    @Bean
    public SecretKey jwtSecretKey(AppProperties props) throws NoSuchAlgorithmException {
        String segredo = props.jwt().secret();
        if (segredo == null || segredo.length() < 32) {
            throw new IllegalStateException(
                    "Defina APP_JWT_SECRET com pelo menos 32 caracteres (gere com: openssl rand -base64 48).");
        }
        byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(props.corsOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
