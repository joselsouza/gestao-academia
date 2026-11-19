package com.academia.gestao.financeiro;

import com.academia.gestao.financeiro.FinanceiroDtos.*;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/financeiro")
@RequiredArgsConstructor
public class FinanceiroController {

    private final FinanceiroService service;

    /** competencia no formato yyyy-MM (padrão: mês atual). */
    @GetMapping("/mensalidades")
    public List<MensalidadeResponse> listar(@RequestParam(required = false) String competencia,
                                            @RequestParam(required = false) StatusMensalidade status,
                                            @RequestParam(required = false) Boolean atrasadas) {
        return service.listar(competencia(competencia), status, atrasadas);
    }

    @GetMapping("/mensalidades/aluno/{alunoId}")
    public List<MensalidadeResponse> doAluno(@PathVariable Long alunoId) {
        return service.doAluno(alunoId);
    }

    @PostMapping("/mensalidades/gerar")
    public GeracaoResponse gerar(@RequestParam(required = false) String competencia) {
        return service.gerar(competencia(competencia));
    }

    @PostMapping("/mensalidades/{id}/pagar")
    public MensalidadeResponse pagar(@PathVariable Long id, @Valid @RequestBody PagamentoRequest req) {
        return service.pagar(id, req);
    }

    @PostMapping("/mensalidades/{id}/estornar")
    public MensalidadeResponse estornar(@PathVariable Long id) {
        return service.estornar(id);
    }

    @PostMapping("/mensalidades/{id}/cancelar")
    public MensalidadeResponse cancelar(@PathVariable Long id) {
        return service.cancelar(id);
    }

    /** Gera o link de pagamento no Asaas (body opcional: { "cpf": "..." } para alunos sem CPF). */
    @PostMapping("/mensalidades/{id}/link")
    public MensalidadeResponse gerarLink(@PathVariable Long id, @RequestBody(required = false) LinkRequest req) {
        return service.gerarLink(id, req);
    }

    @PostMapping("/mensalidades/links")
    public LinksResponse gerarLinks(@RequestParam(required = false) String competencia) {
        return service.gerarLinks(competencia(competencia));
    }

    @PostMapping("/asaas/sincronizar")
    public SincronizacaoAsaas sincronizarAsaas() {
        return service.sincronizarAsaas();
    }

    @GetMapping("/inadimplentes")
    public List<MensalidadeResponse> inadimplentes() {
        return service.inadimplentes();
    }

    @GetMapping("/resumo")
    public ResumoFinanceiro resumo(@RequestParam(required = false) String competencia) {
        return service.resumo(competencia(competencia));
    }

    private YearMonth competencia(String valor) {
        return valor == null || valor.isBlank() ? YearMonth.from(service.hoje()) : YearMonth.parse(valor);
    }
}
