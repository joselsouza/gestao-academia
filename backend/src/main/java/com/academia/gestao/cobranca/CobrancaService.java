package com.academia.gestao.cobranca;

import com.academia.gestao.aluno.Aluno;
import com.academia.gestao.aluno.AlunoRepository;
import com.academia.gestao.aluno.Documentos;
import com.academia.gestao.cobranca.AsaasDtos.*;
import com.academia.gestao.cobranca.CobrancaDtos.*;
import com.academia.gestao.common.NaoEncontradoException;
import com.academia.gestao.common.NegocioException;
import com.academia.gestao.config.AppProperties;
import com.academia.gestao.push.AvisosPush;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class CobrancaService {

    private final CobrancaRepository cobrancas;
    private final AlunoRepository alunos;
    private final AsaasClient asaas;
    private final AsaasCobrador cobrador;
    private final Clock clock;
    private final boolean webhookConfigurado;
    private final String nomeAcademia;
    /** Notificações push: nova cobrança (aluno) e pagamento confirmado (equipe). */
    private final AvisosPush avisos;
    /** Pagamentos mais antigos que isso não geram aviso (evita avisar o histórico na 1ª sincronização). */
    private static final int DIAS_AVISO_PAGAMENTO = 3;

    public CobrancaService(CobrancaRepository cobrancas, AlunoRepository alunos, AsaasClient asaas,
                           AsaasCobrador cobrador, AvisosPush avisos, Clock clock, AppProperties props) {
        this.cobrador = cobrador;
        this.avisos = avisos;
        this.cobrancas = cobrancas;
        this.alunos = alunos;
        this.asaas = asaas;
        this.clock = clock;
        this.webhookConfigurado = props.asaas() != null && props.asaas().webhookToken() != null
                && !props.asaas().webhookToken().isBlank();
        this.nomeAcademia = props.nomeAcademia();
    }

    private LocalDate hoje() {
        return LocalDate.now(clock);
    }

    public ConfigResponse config() {
        return new ConfigResponse(asaas.isConfigurado(), asaas.isSandbox(), webhookConfigurado);
    }

    @Transactional(readOnly = true)
    public List<CobrancaResponse> listar() {
        LocalDate hoje = hoje();
        return cobrancas.findAllComAluno().stream().map(c -> CobrancaResponse.of(c, hoje)).toList();
    }

    @Transactional
    public CobrancaResponse criar(CobrancaRequest req) {
        LocalDate hoje = hoje();
        if (req.vencimento().isBefore(hoje)) {
            throw new NegocioException("O vencimento não pode ser anterior a hoje.");
        }
        if (req.tipo() == TipoCobranca.RECORRENTE && req.ciclo() == null) {
            throw new NegocioException("Informe a periodicidade da cobrança recorrente.");
        }
        if (req.dataFim() != null && !req.dataFim().isAfter(req.vencimento())) {
            throw new NegocioException("A data de término deve ser depois do primeiro vencimento.");
        }
        Aluno aluno = alunos.findById(req.alunoId()).orElseThrow(() -> new NaoEncontradoException("Aluno", req.alunoId()));
        if (req.cpf() != null && !req.cpf().isBlank()) {
            aluno.setCpf(Documentos.cpfOuNull(req.cpf()));
        }
        if (req.email() != null && !req.email().isBlank()) {
            aluno.setEmail(req.email().trim().toLowerCase());
        }
        String cliente = cobrador.garantirCliente(aluno);

        String descricao = req.descricao() == null || req.descricao().isBlank()
                ? (req.tipo() == TipoCobranca.RECORRENTE ? "Mensalidade " + nomeAcademia : nomeAcademia)
                : req.descricao().trim();
        descricao = AsaasCobrador.descricaoPara(aluno, descricao);
        Cobranca c = new Cobranca();
        c.setAluno(aluno);
        c.setTipo(req.tipo());
        c.setForma(req.forma());
        c.setDescricao(descricao);
        c.setValor(req.valor());
        c.setVencimento(req.vencimento());
        String ref = "aluno-" + aluno.getId();

        if (req.tipo() == TipoCobranca.UNICA) {
            Pagamento p = asaas.criarPagamento(new PagamentoReq(cliente, req.forma().asaas, req.valor(),
                    req.vencimento().toString(), descricao, ref));
            c.setAsaasId(p.id());
            aplicarPagamento(c, p);
        } else {
            c.setCiclo(req.ciclo());
            c.setDataFim(req.dataFim());
            Assinatura s = asaas.criarAssinatura(new AssinaturaReq(cliente, req.forma().asaas, req.valor(),
                    req.vencimento().toString(), req.ciclo().asaas, descricao,
                    req.dataFim() == null ? null : req.dataFim().toString(), ref));
            c.setAsaasId(s.id());
            try {
                atualizarRecorrente(c, s);
            } catch (NegocioException e) {
                // A assinatura já existe no Asaas; o link aparece na próxima atualização.
                log.warn("Assinatura {} criada, mas a 1ª parcela ainda não pôde ser lida: {}", s.id(), e.getMessage());
            }
        }
        c.setAtualizadoEm(OffsetDateTime.now(clock));
        Cobranca salva = cobrancas.save(c);
        if (salva.getStatus().emAberto()) {
            avisos.novaCobranca(salva.getAsaasPagamentoId() != null ? salva.getAsaasPagamentoId() : salva.getAsaasId(),
                    aluno.getId(), descricaoCurta(salva), salva.getValor(), salva.getVencimento());
        }
        return CobrancaResponse.of(salva, hoje);
    }

    /** Exclui no Asaas (pagamento ou assinatura com as parcelas em aberto) e no sistema. */
    @Transactional
    public void excluir(Long id) {
        Cobranca c = buscar(id);
        if (c.getTipo() == TipoCobranca.UNICA) {
            if (c.getStatus() == StatusCobranca.PAGA) {
                throw new NegocioException("Cobrança já paga não pode ser excluída. Faça o estorno pelo painel do Asaas.");
            }
            asaas.excluirPagamento(c.getAsaasId());
        } else {
            asaas.excluirAssinatura(c.getAsaasId());
        }
        cobrancas.delete(c);
    }

    @Transactional
    public CobrancaResponse sincronizar(Long id) {
        Cobranca c = buscar(id);
        sincronizar(c);
        return CobrancaResponse.of(c, hoje());
    }

    /** Atualiza com o Asaas tudo o que ainda pode mudar. Uma falha não interrompe as demais. */
    @Transactional
    public SincronizacaoResponse sincronizarTodas() {
        int ok = 0;
        List<String> falhas = new ArrayList<>();
        for (Cobranca c : cobrancas.findSincronizaveis()) {
            try {
                sincronizar(c);
                ok++;
            } catch (NegocioException e) {
                falhas.add(c.getAluno().getNome() + ": " + e.getMessage());
            }
        }
        return new SincronizacaoResponse(ok, falhas);
    }

    /** Chamado pelo webhook: localiza a cobrança pelo pagamento ou pela assinatura e atualiza. */
    @Transactional
    public void aoReceberEvento(Evento evento) {
        Pagamento p = evento.payment();
        if (p == null) {
            return;
        }
        Optional<Cobranca> c = p.subscription() != null
                ? cobrancas.findByAsaasId(p.subscription())
                : cobrancas.findByAsaasId(p.id());
        if (c.isEmpty()) {
            log.debug("Evento {} do Asaas sem cobrança correspondente ({})", evento.event(), p.id());
            return;
        }
        if (c.get().getTipo() == TipoCobranca.UNICA) {
            if ("PAYMENT_DELETED".equals(evento.event())) {
                // Excluída pelo painel do Asaas: some daqui também.
                cobrancas.delete(c.get());
                return;
            }
            aplicarPagamento(c.get(), p);
            c.get().setAtualizadoEm(OffsetDateTime.now(clock));
        } else {
            sincronizar(c.get());
        }
    }

    // ------------------------------------------------------------------ interno

    private void sincronizar(Cobranca c) {
        if (c.getTipo() == TipoCobranca.UNICA) {
            aplicarPagamento(c, asaas.pagamento(c.getAsaasId()));
        } else {
            String parcelaAntes = c.getAsaasPagamentoId();
            atualizarRecorrente(c, asaas.assinatura(c.getAsaasId()));
            // Nova parcela da recorrente em aberto: avisa o aluno. (Sem parcela antes = a 1ª, já avisada na criação.)
            if (parcelaAntes != null && c.getAsaasPagamentoId() != null && !c.getAsaasPagamentoId().equals(parcelaAntes)
                    && c.getStatus().emAberto()) {
                avisos.novaCobranca(c.getAsaasPagamentoId(), c.getAluno().getId(), descricaoCurta(c), c.getValor(), c.getVencimento());
            }
        }
        c.setAtualizadoEm(OffsetDateTime.now(clock));
    }

    /** Descrição sem o sufixo " - aluno(a): ..." que vai para o Asaas quando quem paga é o responsável. */
    private static String descricaoCurta(Cobranca c) {
        String d = c.getDescricao() == null ? "" : c.getDescricao();
        int i = d.indexOf(" - aluno(a): ");
        return i > 0 ? d.substring(0, i) : d;
    }

    /** Pagamento confirmado recentemente: avisa a equipe (uma vez por pagamento do Asaas). */
    private void avisarSePago(Cobranca c, Pagamento p) {
        if (p == null || p.id() == null || StatusCobranca.doAsaas(p.status()) != StatusCobranca.PAGA) {
            return;
        }
        LocalDate pago = data(p.paymentDate() != null ? p.paymentDate() : p.clientPaymentDate());
        if (pago != null && pago.isBefore(hoje().minusDays(DIAS_AVISO_PAGAMENTO))) {
            return;
        }
        avisos.pagamentoConfirmado(p.id(), c.getAluno().getNome(), p.value() != null ? p.value() : c.getValor(),
                descricaoCurta(c), "/financeiro/cobrancas");
    }

    /**
     * Recorrente: a parcela "da vez" é a mais antiga ainda em aberto; se todas estiverem pagas,
     * mostra a última (paga) e o próximo vencimento previsto da assinatura.
     */
    private void atualizarRecorrente(Cobranca c, Assinatura s) {
        c.setProximoVencimento(data(s.nextDueDate()));
        List<Pagamento> parcelas = asaas.pagamentosDaAssinatura(c.getAsaasId()).stream()
                .filter(p -> !Boolean.TRUE.equals(p.deleted()) && p.dueDate() != null)
                .sorted(Comparator.comparing(Pagamento::dueDate))
                .toList();
        Optional<Pagamento> aberta = parcelas.stream()
                .filter(p -> StatusCobranca.doAsaas(p.status()).emAberto()).findFirst();
        Optional<Pagamento> atual = aberta.isPresent() ? aberta
                : parcelas.isEmpty() ? Optional.empty() : Optional.of(parcelas.get(parcelas.size() - 1));
        parcelas.forEach(p -> avisarSePago(c, p));
        atual.ifPresent(p -> aplicarPagamento(c, p));
        boolean ativa = "ACTIVE".equals(s.status()) && !Boolean.TRUE.equals(s.deleted());
        if (!ativa && aberta.isEmpty()) {
            c.setStatus(StatusCobranca.ENCERRADA);
        }
    }

    private void aplicarPagamento(Cobranca c, Pagamento p) {
        if (c.getTipo() == TipoCobranca.UNICA) {
            avisarSePago(c, p);
        }
        c.setAsaasPagamentoId(p.id());
        if (p.value() != null) {
            c.setValor(p.value());
        }
        c.setVencimento(data(p.dueDate()));
        c.setLink(p.invoiceUrl());
        c.setStatus(StatusCobranca.doAsaas(p.status()));
        LocalDate pago = data(p.paymentDate() != null ? p.paymentDate() : p.clientPaymentDate());
        c.setDataPagamento(c.getStatus() == StatusCobranca.PAGA ? pago : null);
    }

    private Cobranca buscar(Long id) {
        return cobrancas.findById(id).orElseThrow(() -> new NaoEncontradoException("Cobrança", id));
    }

    private static LocalDate data(String iso) {
        return iso == null || iso.isBlank() ? null : LocalDate.parse(iso.substring(0, 10));
    }
}
