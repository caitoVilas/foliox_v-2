package com.foliox.documentos.config;

import java.time.Duration;

import io.minio.MinioAsyncClient;

import okhttp3.OkHttpClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Decision 1: {@link MinioAsyncClient} bean (async facade, Java 8 bytecode → Java 25 safe). */
@Configuration
public class MinioConfig {

    @Bean
    public MinioAsyncClient minioAsyncClient(
            @Value("${app.minio.endpoint}") String endpoint,
            @Value("${app.minio.access-key}") String accessKey,
            @Value("${app.minio.secret-key}") String secretKey) {
        return MinioAsyncClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                // The 8.6.0 builder exposes no timeout methods — time out via the HTTP client.
                .httpClient(new OkHttpClient.Builder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofSeconds(30))
                        .writeTimeout(Duration.ofSeconds(30))
                        .build())
                .build();
    }
}
