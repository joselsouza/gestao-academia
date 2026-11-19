package com.academia.gestao.portal;

import com.academia.gestao.portal.PortalDtos.SituacaoAcesso;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Área da equipe: situação do acesso do aluno ao portal e redefinição da senha. */
@RestController
@RequestMapping("/api/alunos/{id}/acesso")
@RequiredArgsConstructor
public class AcessoAlunoController {

    private final PortalAlunoService service;

    @GetMapping
    public SituacaoAcesso situacao(@PathVariable Long id) {
        return service.situacao(id);
    }

    @PostMapping("/redefinir")
    public SituacaoAcesso redefinir(@PathVariable Long id) {
        return service.redefinirSenha(id);
    }
}
