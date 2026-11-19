package com.academia.gestao.financeiro;

import com.academia.gestao.aluno.Aluno;
import com.academia.gestao.aluno.AlunoRepository;
import com.academia.gestao.common.NaoEncontradoException;
import com.academia.gestao.common.NegocioException;
import com.academia.gestao.financeiro.FinanceiroDtos.*;
import com.academia.gestao.plano.TipoPlano;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import com.academia.gestao.aluno.Documentos;
import com.academia.gestao.cobranca.AsaasCobrador;
import com.academia.gestao.cobranca.AsaasCobrador.Situacao;
import com.academia.gestao.cobranca.StatusCobranca;
import com.academia.gestao.push.AvisosPush;
import java.util.ArrayList;
import java.util.Locale;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class FinanceiroService {

    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMMM/yyyy", Locale.forLanguageTag("pt-BR"));

    private final MensalidadeRepository mensalidades;
    private final AlunoRepository alunos;
    private final AsaasCobrador asaas;
    private final Clock clock;
    /** Dia de vencimento de todas as mensalidades (padrão: 10). */
    private final int diaVencimento;
    /** Nome da academia na descrição das mensalidades enviadas ao Asaas. */
    private final String nomeAcademia;
    /** Primeiro mês cobrado: meses anteriores não geram mensalidade (null = sem limite). */
    private final YearMonth inicioCobranca;
    /** Notificações push: nova mensalidade (aluno) e pagamento confirmado (equipe). */
    private final AvisosPush avisos;

    public FinanceiroService(MensalidadeRepository mensalidades, AlunoRepository alunos, AsaasCobrador asaas,
                             AvisosPush avisos, Clock clock, @Value("${app.financeiro.dia-vencimento:10}") int diaVencimento,
                             @Value("${app.academia.nome:Academia}") String nomeAcademia,
                             @Value("${app.financeiro.inicio-cobranca:}") String inicioCobranca) {
        this.mensalidades = mensalidades;
        this.alunos = alunos;
        this.asaas = asaas;
        this.avisos = avisos;
        this.clock = clock;
        this.diaVencimento = diaVencimento;
        this.nomeAcademia = nomeAcademia == null || nomeAcademia.isBlank() ? "Academia" : nomeAcademia.trim();
        this.inicioCobranca = inicioCobranca == null || inicioCobranca.isBlank() ? null : YearMonth.parse(inicioCobranca.trim());
    }

    /** Se a competência já é cobrada (não é anterior ao início da cobrança). */
    public boolean cobravel(YearMonth competencia) {
        return inicioCobranca == null || !competencia.isBefore(inicioCobranca);
    }

    public LocalDate hoje() {
        return LocalDate.now(clock);
    }

    @Transactional(readOnly = true)
    public List<MensalidadeResponse> listar(YearMonth competencia, StatusMensalidade status, Boolean atrasadas) {
        LocalDate hoje = hoje();
        return mensalidades.findByCompetencia(competencia.atDay(1)).stream()
                .filter(m -> status == null || m.getStatus() == status)
                .filter(m -> atrasadas == null || m.isAtrasada(hoje) == atrasadas)
                .map(m -> MensalidadeResponse.of(m, hoje))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MensalidadeResponse> inadimplentes() {
        LocalDate hoje = hoje();
        return mensalidades.findAtrasadas(hoje).stream().map(m -> MensalidadeResponse.of(m, hoje)).toList();
    }

    @Transactional(readOnly = true)
    public long contarAlunosInadimplentes() {
        return mensalidades.countAlunosInadimplentes(hoje());
    }

    @Transactional(readOnly = true)
    public List<MensalidadeResponse> doAluno(Long alunoId) {
        LocalDate hoje = hoje();
        return mensalidades.findByAluno(alunoId).stream().map(m -> MensalidadeResponse.of(m, hoje)).toList();
    }

    /**
     * Gera as mensalidades da competência para todos os alunos ativos com plano pago, com vencimento
     * no dia 10. Idempotente: quem já tem mensalidade no mês é ignorado. Bolsistas não geram cobrança.
     * Para as mensalidades criadas agora, já emite o link de pagamento no Asaas (alunos com CPF).
     * Meses anteriores ao início da cobrança (app.financeiro.inicio-cobranca) são recusados.
     */
    @Transactional
    public GeracaoResponse gerar(YearMonth competencia) {
        if (!cobravel(competencia)) {
            throw new NegocioException("As mensalidades começam a ser cobradas em " + MES.format(inicioCobranca) + ".");
        }
        LocalDate primeiroDia = competencia.atDay(1);
        int criadas = 0;
        int existentes = 0;
        int bolsistas = 0;
        List<Mensalidade> novas = new ArrayList<>();
        for (Aluno a : alunos.findAtivosComPlano()) {
            if (a.getPlano() == null) {
                continue;
            }
            if (a.getPlano().getTipo() == TipoPlano.BOLSISTA) {
                bolsistas++;
                continue;
            }
            if (mensalidades.existsByAlunoIdAndCompetencia(a.getId(), primeiroDia)) {
                existentes++;
                continue;
            }
            Mensalidade m = new Mensalidade();
            m.setAluno(a);
            m.setCompetencia(primeiroDia);
            m.setValor(a.getPlano().getValor());
            m.setVencimento(competencia.atDay(Math.min(diaVencimento, competencia.lengthOfMonth())));
            novas.add(mensalidades.save(m));
            criadas++;
        }
        LinksResponse links = asaas.isConfigurado() ? emitirLinks(novas) : LinksResponse.vazio();
        for (Mensalidade m : novas) {
            avisos.novaMensalidade(m.getId(), m.getAluno().getId(), "Mensalidade de " + MES.format(m.getCompetencia()),
                    m.getValor(), m.getVencimento());
        }
        return new GeracaoResponse(competencia.toString(), criadas, existentes, bolsistas, links);
    }

    // ------------------------------------------------------------------ Asaas

    /** Gera (ou devolve) o link de pagamento no Asaas de uma mensalidade pendente. */
    @Transactional
    public MensalidadeResponse gerarLink(Long id, LinkRequest req) {
        Mensalidade m = buscar(id);
        if (m.getStatus() != StatusMensalidade.PENDENTE) {
            throw new NegocioException("Só mensalidades pendentes podem ter link de pagamento.");
        }
        if (req != null && req.cpf() != null && !req.cpf().isBlank()) {
            m.getAluno().setCpf(Documentos.cpfOuNull(req.cpf()));
        }
        if (m.getLink() == null) {
            emitir(m);
        }
        return MensalidadeResponse.of(m, hoje());
    }

    /** Gera os links que faltam nas mensalidades pendentes da competência. */
    @Transactional
    public LinksResponse gerarLinks(YearMonth competencia) {
        List<Mensalidade> semLink = mensalidades.findByCompetencia(competencia.atDay(1)).stream()
                .filter(m -> m.getStatus() == StatusMensalidade.PENDENTE && m.getLink() == null)
                .toList();
        return emitirLinks(semLink);
    }

    /** Confere no Asaas as mensalidades pendentes com link (complementa o webhook). */
    @Transactional
    public SincronizacaoAsaas sincronizarAsaas() {
        int verificadas = 0;
        int pagas = 0;
        List<String> falhas = new ArrayList<>();
        for (Mensalidade m : mensalidades.findPendentesNoAsaas()) {
            try {
                aplicar(m, asaas.consultar(m.getAsaasPagamentoId()));
                verificadas++;
                if (m.getStatus() == StatusMensalidade.PAGO) {
                    pagas++;
                }
            } catch (NegocioException e) {
                falhas.add(m.getAluno().getNome() + ": " + e.getMessage());
            }
        }
        return new SincronizacaoAsaas(verificadas, pagas, falhas);
    }

    /**
     * Webhook do Asaas: se o pagamento é de uma mensalidade, atualiza e devolve true.
     * Pago no Asaas → mensalidade PAGO; estornado → volta a PENDENTE; excluído → some o link.
     */
    @Transactional
    public boolean aoReceberPagamentoAsaas(String pagamentoId, Situacao situacao) {
        return mensalidades.findByAsaasPagamentoId(pagamentoId).map(m -> {
            aplicar(m, situacao);
            return true;
        }).orElse(false);
    }

    private LinksResponse emitirLinks(List<Mensalidade> lista) {
        int gerados = 0;
        int semCpf = 0;
        List<String> falhas = new ArrayList<>();
        for (Mensalidade m : lista) {
            if (m.getAluno().cpfDoPagador() == null) {
                semCpf++;
                continue;
            }
            try {
                emitir(m);
                gerados++;
            } catch (NegocioException e) {
                log.warn("Link do Asaas não gerado para a mensalidade {}: {}", m.getId(), e.getMessage());
                falhas.add(m.getAluno().getNome() + ": " + e.getMessage());
            }
        }
        return new LinksResponse(gerados, semCpf, falhas);
    }

    private void emitir(Mensalidade m) {
        String descricao = "Mensalidade " + nomeAcademia + " - " + MES.format(m.getCompetencia());
        AsaasCobrador.Emitido e = asaas.emitir(m.getAluno(), m.getValor(), m.getVencimento(), hoje(), descricao,
                "mensalidade-" + m.getId());
        m.setAsaasPagamentoId(e.id());
        m.setLink(e.link());
    }

    /** Remove o link no Asaas (pagamento por fora, cancelamento). */
    private void descartarLink(Mensalidade m) {
        if (m.getAsaasPagamentoId() != null) {
            asaas.excluir(m.getAsaasPagamentoId());
        }
        m.setAsaasPagamentoId(null);
        m.setLink(null);
    }

    private void aplicar(Mensalidade m, Situacao s) {
        if (s.excluido()) {
            if (m.getStatus() == StatusMensalidade.PENDENTE) {
                m.setAsaasPagamentoId(null);
                m.setLink(null);
            }
            return;
        }
        if (s.status() == StatusCobranca.PAGA) {
            if (m.getStatus() != StatusMensalidade.PAGO) {
                m.setStatus(StatusMensalidade.PAGO);
                m.setFormaPagamento(forma(s.billingType()));
                m.setDataPagamento(s.dataPagamento() != null ? s.dataPagamento() : hoje());
                if (s.valor() != null) {
                    m.setValor(s.valor());
                }
                m.setObservacao("Recebido pelo Asaas");
                String chave = m.getAsaasPagamentoId() != null ? m.getAsaasPagamentoId() : "mensalidade-" + m.getId();
                avisos.pagamentoConfirmado(chave, m.getAluno().getNome(), m.getValor(),
                        "Mensalidade de " + MES.format(m.getCompetencia()), "/financeiro/mensalidades");
            }
        } else if (s.status() == StatusCobranca.ESTORNADA) {
            if (m.getStatus() == StatusMensalidade.PAGO) {
                m.setStatus(StatusMensalidade.PENDENTE);
                m.setDataPagamento(null);
                m.setFormaPagamento(null);
                m.setObservacao("Pagamento estornado no Asaas");
            }
            m.setAsaasPagamentoId(null);
            m.setLink(null);
        } else if (s.link() != null) {
            m.setLink(s.link());
        }
    }

    private static FormaPagamento forma(String billingType) {
        if (billingType == null) {
            return FormaPagamento.PIX;
        }
        return switch (billingType) {
            case "CREDIT_CARD", "DEBIT_CARD" -> FormaPagamento.CARTAO;
            case "BOLETO" -> FormaPagamento.BOLETO;
            case "TRANSFER", "DEPOSIT" -> FormaPagamento.TRANSFERENCIA;
            default -> FormaPagamento.PIX;
        };
    }

    @Transactional
    public MensalidadeResponse pagar(Long id, PagamentoRequest req) {
        Mensalidade m = buscar(id);
        if (m.getStatus() == StatusMensalidade.CANCELADO) {
            throw new NegocioException("Mensalidade cancelada não pode ser paga.");
        }
        if (m.getStatus() == StatusMensalidade.PENDENTE) {
            // Pago por fora (dinheiro, maquininha...): o link do Asaas deixa de valer.
            descartarLink(m);
        }
        m.setStatus(StatusMensalidade.PAGO);
        m.setFormaPagamento(req.formaPagamento());
        m.setDataPagamento(req.dataPagamento() != null ? req.dataPagamento() : hoje());
        if (req.valor() != null) {
            m.setValor(req.valor());
        }
        m.setObservacao(req.observacao());
        return MensalidadeResponse.of(m, hoje());
    }

    @Transactional
    public MensalidadeResponse estornar(Long id) {
        Mensalidade m = buscar(id);
        m.setStatus(StatusMensalidade.PENDENTE);
        m.setDataPagamento(null);
        m.setFormaPagamento(null);
        // Se foi pago pelo Asaas, o estorno do dinheiro é feito no painel do Asaas.
        m.setAsaasPagamentoId(null);
        m.setLink(null);
        return MensalidadeResponse.of(m, hoje());
    }

    @Transactional
    public MensalidadeResponse cancelar(Long id) {
        Mensalidade m = buscar(id);
        if (m.getStatus() == StatusMensalidade.PAGO) {
            throw new NegocioException("Estorne o pagamento antes de cancelar.");
        }
        descartarLink(m);
        m.setStatus(StatusMensalidade.CANCELADO);
        return MensalidadeResponse.of(m, hoje());
    }

    @Transactional(readOnly = true)
    public ResumoFinanceiro resumo(YearMonth competencia) {
        LocalDate hoje = hoje();
        List<Mensalidade> lista = mensalidades.findByCompetencia(competencia.atDay(1)).stream()
                .filter(m -> m.getStatus() != StatusMensalidade.CANCELADO)
                .toList();
        BigDecimal previsto = soma(lista);
        List<Mensalidade> pagas = lista.stream().filter(m -> m.getStatus() == StatusMensalidade.PAGO).toList();
        List<Mensalidade> pendentes = lista.stream().filter(m -> m.getStatus() == StatusMensalidade.PENDENTE).toList();
        List<Mensalidade> atrasadas = pendentes.stream().filter(m -> m.isAtrasada(hoje)).toList();
        return new ResumoFinanceiro(competencia.toString(), previsto, soma(pagas), soma(pendentes), soma(atrasadas),
                pagas.size(), pendentes.size(), atrasadas.size(), alunos.countBolsistasAtivos());
    }

    private Mensalidade buscar(Long id) {
        return mensalidades.findById(id).orElseThrow(() -> new NaoEncontradoException("Mensalidade", id));
    }

    private static BigDecimal soma(List<Mensalidade> lista) {
        return lista.stream().map(Mensalidade::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
