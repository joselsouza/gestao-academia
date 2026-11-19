package com.academia.gestao.cobranca;

import com.academia.gestao.cobranca.AsaasDtos.Evento;
import com.academia.gestao.cobranca.AsaasDtos.Pagamento;
import com.academia.gestao.financeiro.FinanceiroService;
import com.academia.gestao.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook do Asaas (Integrações > Webhooks > URL: https://SEU-BACKEND/api/asaas/webhook).
 * Público, mas só aceita chamadas com o token configurado em ASAAS_WEBHOOK_TOKEN.
 * Sempre responde 200 para eventos válidos, para o Asaas não pausar a fila.
 */
@Slf4j
@RestController
public class AsaasWebhookController {

    private final CobrancaService service;
    private final FinanceiroService financeiro;
    private final byte[] token;

    public AsaasWebhookController(CobrancaService service, FinanceiroService financeiro, AppProperties props) {
        this.service = service;
        this.financeiro = financeiro;
        String t = props.asaas() == null ? null : props.asaas().webhookToken();
        this.token = t == null || t.isBlank() ? null : t.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/api/asaas/webhook")
    public ResponseEntity<Void> receber(
            @RequestHeader(name = "asaas-access-token", required = false) String recebido,
            @RequestBody Evento evento) {
        if (token == null || recebido == null
                || !MessageDigest.isEqual(token, recebido.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(401).build();
        }
        try {
            Pagamento p = evento.payment();
            boolean mensalidade = p != null && p.id() != null && financeiro.aoReceberPagamentoAsaas(p.id(),
                    AsaasCobrador.situacao(p));
            if (!mensalidade) {
                service.aoReceberEvento(evento);
            }
        } catch (RuntimeException e) {
            log.error("Falha ao processar evento {} do Asaas", evento.event(), e);
        }
        return ResponseEntity.ok().build();
    }
}
