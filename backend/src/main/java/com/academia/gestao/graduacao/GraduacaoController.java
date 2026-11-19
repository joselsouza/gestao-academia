package com.academia.gestao.graduacao;

import com.academia.gestao.aluno.Faixa;
import com.academia.gestao.graduacao.GraduacaoDtos.*;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/graduacao")
@RequiredArgsConstructor
public class GraduacaoController {

    private final GraduacaoService service;

    @GetMapping("/regras")
    public List<RegraResponse> regras() {
        return service.listarRegras();
    }

    @PutMapping("/regras/{faixa}")
    public RegraResponse salvarRegra(@PathVariable Faixa faixa, @Valid @RequestBody RegraRequest req) {
        return service.salvarRegra(faixa, req);
    }

    @GetMapping("/status")
    public List<StatusGraduacao> status(@RequestParam(defaultValue = "false") boolean somenteAptos) {
        return service.status(somenteAptos);
    }

    @GetMapping("/status/aluno/{alunoId}")
    public StatusGraduacao statusDoAluno(@PathVariable Long alunoId) {
        return service.statusDoAluno(alunoId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GraduacaoResponse graduar(@Valid @RequestBody GraduarRequest req) {
        return service.graduar(req);
    }

    @GetMapping("/historico")
    public List<GraduacaoResponse> historico(@RequestParam(required = false) Long alunoId) {
        return service.historico(alunoId);
    }
}
