package com.academia.gestao.portal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.academia.gestao.aluno.AlunoController;
import com.academia.gestao.aluno.AlunoService;
import com.academia.gestao.config.AppProperties;
import com.academia.gestao.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Um token de aluno não pode abrir a API da equipe, e vice-versa. */
@WebMvcTest(controllers = {AlunoController.class, PortalAlunoController.class})
@Import(SecurityConfig.class)
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(properties = "app.jwt.secret=segredo-de-teste-com-mais-de-32-caracteres")
class PortalSegurancaTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AlunoService alunoService;

    @MockitoBean
    PortalAlunoService portalService;

    private static SimpleGrantedAuthority scope(String perfil) {
        return new SimpleGrantedAuthority("SCOPE_" + perfil);
    }

    @Test
    void alunoNaoAcessaApiDaEquipe() throws Exception {
        mvc.perform(get("/api/alunos").with(jwt().authorities(scope("ALUNO")).jwt(j -> j.claim("aid", 7))))
                .andExpect(status().isForbidden());
    }

    @Test
    void alunoAcessaOProprioPortal() throws Exception {
        mvc.perform(get("/api/aluno/me/mensalidades").with(jwt().authorities(scope("ALUNO")).jwt(j -> j.claim("aid", 7))))
                .andExpect(status().isOk());
    }

    @Test
    void equipeNaoUsaOPortalDoAluno() throws Exception {
        mvc.perform(get("/api/aluno/me").with(jwt().authorities(scope("ADMIN"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/alunos").with(jwt().authorities(scope("INSTRUTOR"))))
                .andExpect(status().isOk());
    }

    @Test
    void semTokenNaoEntra() throws Exception {
        mvc.perform(get("/api/aluno/me")).andExpect(status().isUnauthorized());
    }
}
