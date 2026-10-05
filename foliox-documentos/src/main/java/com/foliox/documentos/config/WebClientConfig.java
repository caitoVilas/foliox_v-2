package com.foliox.documentos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Boot 4.1 does not auto-register a WebClient.Builder bean in this app
 * (startup failed: ExpedienteClient could not be constructed), so declare it
 * explicitly — WebClient.Builder is prototype-scoped by convention to avoid
 * shared-builder mutation between clients.
 */
@Configuration
public class WebClientConfig {

    @Bean
    @org.springframework.context.annotation.Scope("prototype")
    WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
