package com.academia.gestao.plano;

import com.academia.gestao.aluno.AlunoRepository;
import com.academia.gestao.aluno.Faixa;
import com.academia.gestao.common.NaoEncontradoException;
import com.academia.gestao.common.NegocioException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/planos")
@RequiredArgsConstructor
public class PlanoController {

    private final PlanoRepository planos;
    private final AlunoRepository alunos;

    public record PlanoRequest(
            @NotBlank String nome,
            @NotNull TipoPlano tipo,
            @DecimalMin("0.00") BigDecimal valor,
            String descricao,
            Boolean ativo,
            Set<Faixa> faixas) {}

    public record PlanoResponse(Long id, String nome, TipoPlano tipo, BigDecimal valor,
                                String descricao, boolean ativo, long alunosAtivos, Set<Faixa> faixas) {}

    @GetMapping
    public List<PlanoResponse> listar() {
        return planos.findAllByOrderByAtivoDescNomeAsc().stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public PlanoResponse criar(@Valid @RequestBody PlanoRequest req) {
        return toResponse(planos.save(aplicar(new Plano(), req, null)));
    }

    @PutMapping("/{id}")
    @Transactional
    public PlanoResponse atualizar(@PathVariable Long id, @Valid @RequestBody PlanoRequest req) {
        Plano p = planos.findById(id).orElseThrow(() -> new NaoEncontradoException("Plano", id));
        return toResponse(aplicar(p, req, id));
    }

    /** Planos em uso são apenas desativados, para preservar o histórico. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void remover(@PathVariable Long id) {
        Plano p = planos.findById(id).orElseThrow(() -> new NaoEncontradoException("Plano", id));
        if (alunos.countByPlanoId(id) > 0) {
            p.setAtivo(false);
        } else {
            planos.delete(p);
        }
    }

    private Plano aplicar(Plano p, PlanoRequest req, Long id) {
        Set<Faixa> faixas = req.faixas() == null ? Set.of() : req.faixas();
        for (Faixa f : faixas) {
            planos.findOutrosComFaixa(f, id).stream().findFirst().ifPresent(outro -> {
                throw new NegocioException("A faixa " + f + " já está vinculada ao plano \"" + outro.getNome()
                        + "\". Desvincule de lá primeiro.");
            });
        }
        p.setFaixas(new HashSet<>(faixas));
        p.setNome(req.nome().trim());
        p.setTipo(req.tipo());
        // Bolsista é sempre isento: valor zero, independente do que vier no request
        p.setValor(req.tipo() == TipoPlano.BOLSISTA || req.valor() == null ? BigDecimal.ZERO : req.valor());
        p.setDescricao(req.descricao());
        if (req.ativo() != null) {
            p.setAtivo(req.ativo());
        }
        return p;
    }

    private PlanoResponse toResponse(Plano p) {
        return new PlanoResponse(p.getId(), p.getNome(), p.getTipo(), p.getValor(), p.getDescricao(),
                p.isAtivo(), alunos.countByPlanoIdAndAtivoTrue(p.getId()), p.faixasOrdenadas());
    }
}
