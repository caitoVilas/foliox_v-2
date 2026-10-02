package com.foliox.documentos.storage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.foliox.documentos.exception.ServicioNoDisponibleException;

import io.minio.GetObjectArgs;
import io.minio.MinioAsyncClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Decision 2: {@code subscribeOn(boundedElastic)} at the impl boundary only — the SDK does blocking
 * stream I/O (including {@code GetObjectResponse} reads) even behind {@link MinioAsyncClient}.
 * {@code NoSuchKey} on read → empty Mono (the row, not storage, decides between 404 and 502).
 */
@Component
public class MinioDocumentoStorage implements DocumentoStorage {

    private static final String CONTENT_TYPE_PDF = "application/pdf";

    private final MinioAsyncClient cliente;
    private final String bucket;

    public MinioDocumentoStorage(MinioAsyncClient cliente, @Value("${app.minio.bucket}") String bucket) {
        this.cliente = cliente;
        this.bucket = bucket;
    }

    @Override
    public Mono<Void> guardar(String clave, byte[] contenido) {
        return Mono.defer(() -> {
                    try {
                        return Mono.fromCompletionStage(cliente.putObject(
                                PutObjectArgs.builder()
                                        .bucket(bucket)
                                        .object(clave)
                                        .stream(new ByteArrayInputStream(contenido), contenido.length, -1)
                                        .contentType(CONTENT_TYPE_PDF)
                                        .build()));
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                })
                .then()
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(e -> new ServicioNoDisponibleException(
                        "No se pudo guardar el archivo en el almacenamiento", e));
    }

    @Override
    public Mono<InputStream> obtener(String clave) {
        return Mono.defer(() -> {
                    try {
                        return Mono.fromCompletionStage(cliente.getObject(
                                        GetObjectArgs.builder().bucket(bucket).object(clave).build()))
                                .map(respuesta -> (InputStream) respuesta);
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(MinioDocumentoStorage::esObjetoAusente, e -> Mono.empty())
                .onErrorMap(e -> new ServicioNoDisponibleException(
                        "No se pudo leer el archivo del almacenamiento", e));
    }

    @Override
    public Mono<Void> eliminar(String clave) {
        return Mono.defer(() -> {
                    try {
                        return Mono.fromCompletionStage(cliente.removeObject(
                                RemoveObjectArgs.builder().bucket(bucket).object(clave).build()));
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(e -> new ServicioNoDisponibleException(
                        "No se pudo eliminar el archivo del almacenamiento", e));
    }

    /** MinIO reports a missing object as {@code ErrorResponseException} with code {@code NoSuchKey}. */
    private static boolean esObjetoAusente(Throwable e) {
        Throwable actual = e;
        while (actual != null) {
            if (actual instanceof ErrorResponseException respuesta
                    && respuesta.errorResponse() != null
                    && "NoSuchKey".equals(respuesta.errorResponse().code())) {
                return true;
            }
            actual = actual.getCause();
        }
        return false;
    }
}
