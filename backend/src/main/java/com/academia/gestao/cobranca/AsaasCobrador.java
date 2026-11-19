package com.academia.gestao.cobranca;

import com.academia.gestao.aluno.Aluno;
import com.academia.gestao.aluno.Responsavel;
import com.academia.gestao.cobranca.AsaasDtos.*;
import com.academia.gestao.common.NegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Operações de alto nível no Asaas usadas pelo Financeiro (mensalidades) e pelas cobranças avulsas.
 * Sem @Transactional de propósito: quem chama controla a transação, e um erro do Asaas
 * (NegocioException) pode ser tratado sem marcar a transação para rollback.
 */
@Component
public class AsaasCobrador {

    private final AsaasClient asaas;

    public AsaasCobrador(AsaasClient asaas) {
        this.asaas = asaas;
    }

    public boolean isConfigurado() {
        return asaas.isConfigurado();
    }

    /** Pagamento emitido: id no Asaas e link da fatura. */
    public record Emitido(String id, String link) {}

    /** Situação de um pagamento no Asaas, já traduzida. */
    public record Situacao(String id, StatusCobranca status, LocalDate dataPagamento, BigDecimal valor,
                           String billingType, String link, boolean excluido) {}

    /**
     * Emite um pagamento avulso (PIX, cartão ou boleto à escolha do aluno).
     * Se o vencimento já passou, o Asaas exige data futura: usa hoje.
     */
    public Emitido emitir(Aluno aluno, BigDecimal valor, LocalDate vencimento, LocalDate hoje, String descricao,
                          String referencia) {
        String cliente = garantirCliente(aluno);
        LocalDate venc = vencimento.isBefore(hoje) ? hoje : vencimento;
        Pagamento p = asaas.criarPagamento(new PagamentoReq(cliente, FormaCobranca.INDEFINIDA.asaas, valor,
                venc.toString(), descricaoPara(aluno, descricao), referencia));
        return new Emitido(p.id(), p.invoiceUrl());
    }

    /** Remove o pagamento no Asaas (se já não existir, segue normalmente). */
    public void excluir(String pagamentoId) {
        asaas.excluirPagamento(pagamentoId);
    }

    public Situacao consultar(String pagamentoId) {
        try {
            return situacao(asaas.pagamento(pagamentoId));
        } catch (AsaasClient.NaoExisteNoAsaas e) {
            return new Situacao(pagamentoId, StatusCobranca.PENDENTE, null, null, null, null, true);
        }
    }

    public static Situacao situacao(Pagamento p) {
        StatusCobranca st = StatusCobranca.doAsaas(p.status());
        String data = p.paymentDate() != null ? p.paymentDate() : p.clientPaymentDate();
        return new Situacao(p.id(), st, data == null ? null : LocalDate.parse(data.substring(0, 10)), p.value(),
                p.billingType(), p.invoiceUrl(), Boolean.TRUE.equals(p.deleted()));
    }

    /**
     * Quando quem paga é o responsável, a fatura leva o nome do aluno (o responsável pode ter
     * mais de um filho na academia).
     */
    public static String descricaoPara(Aluno aluno, String descricao) {
        if (!aluno.isCobradoDoResponsavel() || descricao.contains(aluno.getNome())) {
            return descricao;
        }
        String sufixo = " - aluno(a): " + aluno.getNome();
        // A descrição é gravada em VARCHAR(255): corta o texto da academia, nunca o nome do aluno.
        return descricao.substring(0, Math.min(descricao.length(), 255 - sufixo.length())) + sufixo;
    }

    /**
     * Cliente do Asaas que recebe a cobrança do aluno: o responsável (nome e CPF dele), se o aluno
     * tiver um; senão, o próprio aluno.
     */
    public String garantirCliente(Aluno a) {
        Responsavel r = a.getResponsavel();
        if (r == null) {
            return garantirClienteAluno(a);
        }
        ClienteReq dados = new ClienteReq(r.getNome(), r.getCpf(), r.getEmail(),
                r.getTelefone() == null ? null : r.getTelefone().replaceAll("\\D", ""),
                "responsavel-" + r.getId(), true);
        r.setAsaasCustomerId(garantir(r.getAsaasCustomerId(), dados));
        return r.getAsaasCustomerId();
    }

    /**
     * Cria o cliente no Asaas na primeira cobrança do aluno; nas seguintes, atualiza os dados.
     * As notificações automáticas do Asaas ficam desligadas: o link é enviado pela academia.
     */
    private String garantirClienteAluno(Aluno a) {
        ClienteReq dados = new ClienteReq(a.getNome(), a.getCpf(), a.getEmail(),
                a.getTelefone() == null ? null : a.getTelefone().replaceAll("\\D", ""),
                "aluno-" + a.getId(), true);
        if (a.getAsaasCustomerId() == null && a.getCpf() == null) {
            throw new NegocioException("Cadastre o CPF de " + a.getNome() + ": o Asaas exige para emitir a cobrança.");
        }
        a.setAsaasCustomerId(garantir(a.getAsaasCustomerId(), dados));
        return a.getAsaasCustomerId();
    }

    /** Cria o cliente (sem id) ou atualiza os dados; se foi removido no painel do Asaas, cria de novo. */
    private String garantir(String clienteId, ClienteReq dados) {
        if (clienteId == null) {
            return asaas.criarCliente(dados).id();
        }
        try {
            asaas.atualizarCliente(clienteId, dados);
            return clienteId;
        } catch (AsaasClient.NaoExisteNoAsaas e) {
            return asaas.criarCliente(dados).id();
        }
    }
}
