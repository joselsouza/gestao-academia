package com.academia.gestao.cobranca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

/** Formatos da API v3 do Asaas (somente os campos usados). Datas no formato yyyy-MM-dd. */
final class AsaasDtos {

    private AsaasDtos() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ClienteReq(String name, String cpfCnpj, String email, String mobilePhone, String externalReference,
                      Boolean notificationDisabled) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Cliente(String id) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PagamentoReq(String customer, String billingType, BigDecimal value, String dueDate, String description,
                        String externalReference) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Pagamento(String id, String subscription, String status, BigDecimal value, String dueDate,
                     String invoiceUrl, String paymentDate, String clientPaymentDate, Boolean deleted,
                     String billingType, String externalReference) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AssinaturaReq(String customer, String billingType, BigDecimal value, String nextDueDate, String cycle,
                         String description, String endDate, String externalReference) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Assinatura(String id, String status, String nextDueDate, Boolean deleted) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Lista<T>(List<T> data, Boolean hasMore) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Erro(List<Item> errors) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Item(String code, String description) {}
    }

    /** Corpo do webhook: { "event": "PAYMENT_RECEIVED", "payment": { ... } }. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Evento(String id, String event, Pagamento payment) {}
}
