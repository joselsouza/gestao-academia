package com.academia.gestao.cobranca;

import java.util.Set;

/**
 * Situação da cobrança (da parcela "da vez", no caso de recorrente).
 * ENCERRADA: assinatura inativa/expirada sem parcela em aberto.
 */
public enum StatusCobranca {
    PENDENTE, VENCIDA, PAGA, ESTORNADA, ENCERRADA;

    private static final Set<String> PAGOS = Set.of("RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH", "DUNNING_RECEIVED");
    private static final Set<String> VENCIDOS = Set.of("OVERDUE", "DUNNING_REQUESTED");
    private static final Set<String> ESTORNADOS = Set.of("REFUNDED", "REFUND_REQUESTED", "REFUND_IN_PROGRESS",
            "CHARGEBACK_REQUESTED", "CHARGEBACK_DISPUTE", "AWAITING_CHARGEBACK_REVERSAL");

    /** Converte o status do pagamento no Asaas. */
    static StatusCobranca doAsaas(String status) {
        if (status == null) {
            return PENDENTE;
        }
        if (PAGOS.contains(status)) {
            return PAGA;
        }
        if (VENCIDOS.contains(status)) {
            return VENCIDA;
        }
        if (ESTORNADOS.contains(status)) {
            return ESTORNADA;
        }
        return PENDENTE; // PENDING, AWAITING_RISK_ANALYSIS
    }

    boolean emAberto() {
        return this == PENDENTE || this == VENCIDA;
    }
}
