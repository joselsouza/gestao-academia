package com.academia.gestao.push;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.academia.gestao.config.AppProperties;
import com.academia.gestao.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Cada um só inscreve o próprio aparelho, e só no seu lado (equipe x aluno). */
@WebMvcTest(controllers = PushController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(properties = "app.jwt.secret=segredo-de-teste-com-mais-de-32-caracteres")
class PushSegurancaTest {

    private static final String INSCRICAO = "{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/x\",\"p256dh\":\"a\",\"auth\":\"b\"}";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PushService push;

    @Test
    void chavePublicaNaoPrecisaDeLogin() throws Exception {
        when(push.chavePublica()).thenReturn("BChave");
        mvc.perform(get("/api/push/chave")).andExpect(status().isOk()).andExpect(jsonPath("$.chavePublica").value("BChave"));
    }

    @Test
    void alunoNaoUsaAsRotasDaEquipe() throws Exception {
        mvc.perform(post("/api/push/inscricao").contentType(MediaType.APPLICATION_JSON).content(INSCRICAO)
                        .with(jwt().jwt(j -> j.claim("aid", 7)).authorities(new SimpleGrantedAuthority("SCOPE_ALUNO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void equipeNaoUsaAsRotasDoAluno() throws Exception {
        mvc.perform(post("/api/aluno/push/inscricao").contentType(MediaType.APPLICATION_JSON).content(INSCRICAO)
                        .with(jwt().jwt(j -> j.claim("uid", 1)).authorities(new SimpleGrantedAuthority("SCOPE_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void alunoInscreveOProprioAparelho() throws Exception {
        mvc.perform(post("/api/aluno/push/inscricao").contentType(MediaType.APPLICATION_JSON).content(INSCRICAO)
                        .with(jwt().jwt(j -> j.claim("aid", 7)).authorities(new SimpleGrantedAuthority("SCOPE_ALUNO"))))
                .andExpect(status().isNoContent());
        verify(push).inscrever(any(), isNull(), eq(7L));
    }

    @Test
    void equipeInscreveOProprioAparelho() throws Exception {
        mvc.perform(post("/api/push/inscricao").contentType(MediaType.APPLICATION_JSON).content(INSCRICAO)
                        .with(jwt().jwt(j -> j.claim("uid", 3)).authorities(new SimpleGrantedAuthority("SCOPE_INSTRUTOR"))))
                .andExpect(status().isNoContent());
        verify(push).inscrever(any(), eq(3L), isNull());
    }
}
