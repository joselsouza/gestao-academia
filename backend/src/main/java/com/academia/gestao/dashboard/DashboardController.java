package com.academia.gestao.dashboard;

import com.academia.gestao.aluno.AlunoRepository;
import com.academia.gestao.financeiro.FinanceiroDtos.ResumoFinanceiro;
import com.academia.gestao.financeiro.FinanceiroService;
import com.academia.gestao.graduacao.GraduacaoService;
import com.academia.gestao.presenca.PresencaDtos.TotalDia;
import com.academia.gestao.presenca.PresencaRepository;
import com.academia.gestao.presenca.PresencaService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final AlunoRepository alunos;
    private final PresencaRepository presencas;
    private final PresencaService presencaService;
    private final FinanceiroService financeiro;
    private final GraduacaoService graduacao;

    public record Dashboard(
            long alunosAtivos,
            long bolsistas,
            long presencasHoje,
            long aptosGraduacao,
            long inadimplentes,
            ResumoFinanceiro financeiroMes,
            List<TotalDia> presencasUltimos14Dias) {}

    @GetMapping("/api/dashboard")
    public Dashboard dashboard() {
        LocalDate hoje = financeiro.hoje();
        return new Dashboard(
                alunos.countByAtivoTrue(),
                alunos.countBolsistasAtivos(),
                presencas.countByData(hoje),
                graduacao.status(true).size(),
                financeiro.contarAlunosInadimplentes(),
                financeiro.resumo(YearMonth.from(hoje)),
                presencaService.totaisPorDia(hoje.minusDays(13), hoje));
    }
}
