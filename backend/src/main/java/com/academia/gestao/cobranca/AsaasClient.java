package com.academia.gestao.cobranca;

import com.academia.gestao.cobranca.AsaasDtos.*;
import com.academia.gestao.common.NegocioException;
import com.academia.gestao.config.AppProperties;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Cliente HTTP da API v3 do Asaas (autenticação pelo cabeçalho access_token). */
@Slf4j
@Component
public class AsaasClient {

    private static final String PRODUCAO = "https://api.asaas.com";

    private final RestClient http;
    private final boolean configurado;
    private final boolean sandbox;

    public AsaasClient(AppProperties props) {
        AppProperties.Asaas cfg = props.asaas();
        String chave = cfg == null ? null : cfg.apiKey();
        String base = cfg == null || cfg.baseUrl() == null || cfg.baseUrl().isBlank()
                ? "https://api-sandbox.asaas.com/v3" : cfg.baseUrl().trim();
        this.configurado = chave != null && !chave.isBlank();
        this.sandbox = !base.startsWith(PRODUCAO);

        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(10));
        fabrica.setReadTimeout(Duration.ofSeconds(30));
        this.http = RestClient.builder()
                .baseUrl(base)
                .requestFactory(fabrica)
                .defaultHeader("access_token", configurado ? chave.trim() : "")
                .defaultHeader("User-Agent", "Gestao-Academia")
                .build();
    }

    public boolean isConfigurado() {
        return configurado;
    }

    public boolean isSandbox() {
        return sandbox;
    }

    // ---------------------------------------------------------------- clientes

    Cliente criarCliente(ClienteReq req) {
        return chamar(() -> http.post().uri("/customers").contentType(MediaType.APPLICATION_JSON)
                .body(req).retrieve().body(Cliente.class));
    }

    Cliente atualizarCliente(String id, ClienteReq req) {
        return chamar(() -> http.put().uri("/customers/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .body(req).retrieve().body(Cliente.class));
    }

    // ------------------------------------------------------------- pagamentos

    Pagamento criarPagamento(PagamentoReq req) {
        return chamar(() -> http.post().uri("/payments").contentType(MediaType.APPLICATION_JSON)
                .body(req).retrieve().body(Pagamento.class));
    }

    Pagamento pagamento(String id) {
        return chamar(() -> http.get().uri("/payments/{id}", id).retrieve().body(Pagamento.class));
    }

    void excluirPagamento(String id) {
        chamarIgnorando404(() -> http.delete().uri("/payments/{id}", id).retrieve().toBodilessEntity());
    }

    // ------------------------------------------------------------ assinaturas

    Assinatura criarAssinatura(AssinaturaReq req) {
        return chamar(() -> http.post().uri("/subscriptions").contentType(MediaType.APPLICATION_JSON)
                .body(req).retrieve().body(Assinatura.class));
    }

    Assinatura assinatura(String id) {
        return chamar(() -> http.get().uri("/subscriptions/{id}", id).retrieve().body(Assinatura.class));
    }

    List<Pagamento> pagamentosDaAssinatura(String id) {
        Lista<Pagamento> lista = chamar(() -> http.get().uri("/subscriptions/{id}/payments?limit=100", id)
                .retrieve().body(new ParameterizedTypeReference<Lista<Pagamento>>() {}));
        return lista == null || lista.data() == null ? List.of() : lista.data();
    }

    void excluirAssinatura(String id) {
        chamarIgnorando404(() -> http.delete().uri("/subscriptions/{id}", id).retrieve().toBodilessEntity());
    }

    // ---------------------------------------------------------------- suporte

    private void chamarIgnorando404(Runnable r) {
        try {
            chamar(() -> {
                r.run();
                return null;
            });
        } catch (NaoExisteNoAsaas e) {
            log.info("Registro já não existia no Asaas; seguindo com a exclusão local.");
        }
    }

    private <T> T chamar(Supplier<T> chamada) {
        if (!configurado) {
            throw new NegocioException("Integração com o Asaas não configurada (defina ASAAS_API_KEY no servidor).");
        }
        try {
            return chamada.get();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new NaoExisteNoAsaas();
            }
            throw new NegocioException(mensagem(e));
        } catch (ResourceAccessException e) {
            log.warn("Falha de comunicação com o Asaas", e);
            throw new NegocioException("Não foi possível falar com o Asaas agora. Tente novamente em instantes.");
        }
    }

    private static String mensagem(RestClientResponseException e) {
        if (e.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
            return "Asaas recusou a chave de API (verifique ASAAS_API_KEY e se é do ambiente certo: sandbox ou produção).";
        }
        try {
            Erro erro = e.getResponseBodyAs(Erro.class);
            if (erro != null && erro.errors() != null && !erro.errors().isEmpty()) {
                return "Asaas: " + erro.errors().stream().map(Erro.Item::description)
                        .filter(Objects::nonNull).collect(Collectors.joining(" "));
            }
        } catch (RuntimeException ignorado) {
            // corpo não é JSON de erro do Asaas
        }
        log.warn("Erro do Asaas {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
        return "Asaas respondeu com erro " + e.getStatusCode().value() + ".";
    }

    /** 404 no Asaas: o registro foi removido por lá (ex.: pelo painel). */
    static class NaoExisteNoAsaas extends NegocioException {
        NaoExisteNoAsaas() {
            super("Cobrança não encontrada no Asaas (pode ter sido excluída pelo painel).");
        }
    }
}
