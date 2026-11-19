package com.academia.gestao.cobranca;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Confere diariamente a situação das cobranças (complementa o webhook). */
@Slf4j
@Component
@RequiredArgsConstructor
public class CobrancaScheduler {

    private final CobrancaService service;
    private final com.academia.gestao.financeiro.FinanceiroService financeiro;
    private final AsaasClient asaas;

    @Scheduled(cron = "${app.asaas.cron-sincronizar}", zone = "${app.timezone}")
    public void sincronizar() {
        if (!asaas.isConfigurado()) {
            return;
        }
        try {
            log.info("Mensalidades conferidas no Asaas: {}", financeiro.sincronizarAsaas());
            log.info("Cobranças avulsas sincronizadas com o Asaas: {}", service.sincronizarTodas());
        } catch (Exception e) {
            log.error("Falha ao sincronizar cobranças com o Asaas", e);
        }
    }
}
