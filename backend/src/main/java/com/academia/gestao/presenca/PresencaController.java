package com.academia.gestao.presenca;

import com.academia.gestao.presenca.PresencaDtos.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/presencas")
@RequiredArgsConstructor
public class PresencaController {

    private final PresencaService service;

    @GetMapping
    public List<PresencaResponse> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return service.listar(inicio, fim);
    }

    /** Único ponto que grava presença (botão "Salvar presença"). */
    @PostMapping("/salvar")
    public SalvarPresencaResponse salvar(@Valid @RequestBody SalvarPresencaRequest req) {
        return service.salvar(req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Long id) {
        service.remover(id);
    }

    @PostMapping("/reconhecimento")
    public ReconhecimentoResponse reconhecer(@Valid @RequestBody ReconhecimentoRequest req) {
        return service.reconhecer(req);
    }
}
