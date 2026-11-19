package com.academia.gestao.financeiro;

import java.time.Clock;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Gera as mensalidades no dia 1 de cada mês (classe separada para passar pelo proxy transacional). */
@Slf4j
@Component
@RequiredArgsConstructor
public class MensalidadeScheduler {

    private final FinanceiroService service;
    private final Clock clock;

    /**
     * Também roda ao subir: em hospedagem gratuita o servidor "dorme" sem acesso e pode perder
     * o dia 1º. Como a geração é idempotente, rodar de novo não duplica nada.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "${app.financeiro.cron-gerar-mensalidades}", zone = "${app.timezone}")
    public void gerarMensalidadesDoMes() {
        YearMonth mes = YearMonth.now(clock);
        if (!service.cobravel(mes)) {
            log.info("Mensalidades de {} não geradas: a cobrança ainda não começou.", mes);
            return;
        }
        try {
            log.info("Mensalidades geradas automaticamente: {}", service.gerar(mes));
        } catch (Exception e) {
            log.error("Falha ao gerar mensalidades do mês", e);
        }
    }
}
