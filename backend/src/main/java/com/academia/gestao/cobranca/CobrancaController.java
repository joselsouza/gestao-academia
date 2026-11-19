package com.academia.gestao.cobranca;

import com.academia.gestao.cobranca.CobrancaDtos.*;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cobrancas")
@RequiredArgsConstructor
public class CobrancaController {

    private final CobrancaService service;

    @GetMapping
    public List<CobrancaResponse> listar() {
        return service.listar();
    }

    @GetMapping("/config")
    public ConfigResponse config() {
        return service.config();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CobrancaResponse criar(@Valid @RequestBody CobrancaRequest req) {
        return service.criar(req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }

    @PostMapping("/{id}/sincronizar")
    public CobrancaResponse sincronizar(@PathVariable Long id) {
        return service.sincronizar(id);
    }

    @PostMapping("/sincronizar")
    public SincronizacaoResponse sincronizarTodas() {
        return service.sincronizarTodas();
    }
}
