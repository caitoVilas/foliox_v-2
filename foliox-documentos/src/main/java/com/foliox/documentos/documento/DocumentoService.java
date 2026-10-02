package com.foliox.documentos.documento;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.foliox.common.enums.EstadoExpediente;
import com.foliox.common.exception.ForbiddenOperationException;
import com.foliox.common.exception.ResourceNotFoundException;
import com.foliox.documentos.client.ExpedienteClient;
import com.foliox.documentos.exception.ArchivoNoValidoException;
import com.foliox.documentos.exception.ServicioNoDisponibleException;
import com.foliox.documentos.storage.DocumentoStorage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class DocumentoService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoService.class);

    private static final int NOMBRE_MAXIMO = 255;
    private static final int TAMANO_BLOQUE = 8192;
    private static final String PREFIJO_PDF = "%PDF-";
    private static final String NOMBRE_POR_DEFECTO = "documento.pdf";
    private static final DataBufferFactory FABRICANTE_BUFFERS = new DefaultDataBufferFactory();

    private final DocumentoRepository documentoRepository;
    private final DocumentoStorage storage;
    private final ExpedienteClient expedienteClient;
    private final long maximoBytes;

    public DocumentoService(
            DocumentoRepository documentoRepository,
            DocumentoStorage storage,
            ExpedienteClient expedienteClient,
            @Value("${spring.codec.max-in-memory-size}") String maxInMemorySize) {
        this.documentoRepository = documentoRepository;
        this.storage = storage;
        this.expedienteClient = expedienteClient;
        this.maximoBytes = DataSize.parse(maxInMemorySize).toBytes();
    }

    /**
     * Decisions 4, 5 and 9: cheap-first order (content-type → Content-Length → counted aggregate →
     * magic bytes → expediente hop → MinIO put → DB insert). On insert failure the stored object is
     * removed best-effort and the failure logged (orphan without a row is invisible to the API).
     */
    public Mono<DocumentoResponse> crear(
            UUID estudioId, UUID expedienteId, FilePart file, String token) {
        return Mono.defer(() -> {
            MediaType contentType = file.headers().getContentType();
            if (contentType == null || !MediaType.APPLICATION_PDF.isCompatibleWith(contentType)) {
                return Mono.error(new ArchivoNoValidoException("El archivo debe ser un PDF"));
            }
            if (file.headers().getContentLength() > maximoBytes) {
                return Mono.error(new DataBufferLimitException("El archivo supera el tamaño máximo permitido"));
            }
            return leerContenido(file)
                    .flatMap(contenido -> {
                        if (!esPdf(contenido)) {
                            return Mono.error(new ArchivoNoValidoException("El archivo debe ser un PDF"));
                        }
                        return expedienteClient.obtener(expedienteId, token).map(resumen -> contenido);
                    })
                    .flatMap(contenido -> persistir(estudioId, expedienteId, file.filename(), contenido));
        });
    }

    public Mono<List<DocumentoResponse>> listar(UUID estudioId) {
        return documentoRepository.findByEstudioId(estudioId)
                .map(DocumentoResponse::from)
                .collectList();
    }

    public Mono<DocumentoResponse> obtener(UUID id, UUID estudioId) {
        return porEstudio(id, estudioId).map(DocumentoResponse::from);
    }

    /**
     * Decision 7: row-backed streaming — scoped lookup (404) → storage read; object missing or
     * unreadable → 502 (the row proves the resource exists for the tenant). The InputStream is
     * bridged lazily with {@code DataBufferUtils.readInputStream}, which closes it on termination.
     */
    public Mono<ResponseEntity<Flux<DataBuffer>>> contenido(UUID id, UUID estudioId) {
        return porEstudio(id, estudioId).flatMap(documento ->
                storage.obtener(documento.getUbicacion())
                        .switchIfEmpty(Mono.error(new ServicioNoDisponibleException(
                                "El contenido del documento no está disponible en el almacenamiento")))
                        .map(entrada -> {
                            Flux<DataBuffer> cuerpo =
                                    DataBufferUtils.readInputStream(() -> entrada, FABRICANTE_BUFFERS, TAMANO_BLOQUE);
                            return ResponseEntity.ok()
                                    .contentType(MediaType.APPLICATION_PDF)
                                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition
                                            .attachment()
                                            .filename(documento.getNombre(), StandardCharsets.UTF_8)
                                            .build()
                                            .toString())
                                    .body(cuerpo);
                        }));
    }

    /**
     * Decision 8: scoped lookup 404 first → re-validate the new expediente when present (404/502,
     * document untouched on failure) → apply non-null fields → save. {@code estudio_id} is never
     * modifiable (not part of the command record).
     */
    public Mono<DocumentoResponse> actualizar(
            UUID id, UUID estudioId, ActualizarDocumentoCommand command, String token) {
        return porEstudio(id, estudioId)
                .flatMap(documento -> revalidar(documento, command, token).map(validado -> {
                    if (command.nombre() != null) {
                        validado.setNombre(truncar(command.nombre()));
                    }
                    if (command.expedienteId() != null) {
                        validado.setExpedienteId(command.expedienteId());
                    }
                    return validado;
                }))
                .flatMap(documentoRepository::save)
                .map(DocumentoResponse::from);
    }

    /**
     * Decisions 3 and 9: row-first delete — scoped 404 before any role check; ADMIN bypasses the
     * expediente check; otherwise the live estado must be CERRADO (expedientes down → 502, document
     * untouched). Row delete (auto-commit) → MinIO cleanup with {@code onErrorResume}: failure is
     * logged and never fails the request (still 204).
     */
    public Mono<Void> eliminar(UUID id, UUID estudioId, String token, boolean admin) {
        return porEstudio(id, estudioId)
                .flatMap(documento -> admin
                        ? borrar(documento)
                        : expedienteClient
                                .obtener(documento.getExpedienteId(), token)
                                .flatMap(resumen -> EstadoExpediente.CERRADO.name().equals(resumen.estado())
                                        ? borrar(documento)
                                        : Mono.error(new ForbiddenOperationException(
                                                "Solo se pueden eliminar documentos de expedientes cerrados"))));
    }

    /**
     * Decision 9: MinIO put → DB insert. The object key uses its own app-generated uuid because the
     * row id is database-generated ({@code DEFAULT gen_random_uuid()}) and {@code save()} cannot
     * insert a pre-assigned id (it would silently update a missing row). Order keeps the invariant
     * "no row without an object": a failed insert leaves an invisible orphan, never a 502 row.
     */
    private Mono<DocumentoResponse> persistir(
            UUID estudioId, UUID expedienteId, String filename, byte[] contenido) {
        String clave = estudioId + "/" + UUID.randomUUID() + ".pdf";
        Documento documento = new Documento();
        documento.setEstudioId(estudioId);
        documento.setExpedienteId(expedienteId);
        documento.setNombre(truncar(
                filename != null && !filename.isBlank() ? filename : NOMBRE_POR_DEFECTO));
        documento.setUbicacion(clave);
        documento.setCreadoEn(Instant.now());

        return storage.guardar(clave, contenido)
                .then(documentoRepository.save(documento)
                        .map(DocumentoResponse::from)
                        .onErrorResume(error -> {
                            log.error(
                                    "No se pudo registrar el documento del estudio {}; se elimina el objeto {}",
                                    estudioId,
                                    clave,
                                    error);
                            return storage.eliminar(clave)
                                    .onErrorResume(limpieza -> {
                                        log.warn(
                                                "No se pudo eliminar el objeto {} tras el fallo de inserción: {}",
                                                clave,
                                                limpieza.getMessage());
                                        return Mono.empty();
                                    })
                                    .then(Mono.error(error));
                        }));
    }

    /** Decision 4: aggregate while counting bytes — over the cap → DataBufferLimitException (413). */
    private Mono<byte[]> leerContenido(FilePart file) {
        return DataBufferUtils.join(file.content(), (int) maximoBytes).map(buffer -> {
            try {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                return bytes;
            } finally {
                DataBufferUtils.release(buffer);
            }
        });
    }

    private static boolean esPdf(byte[] contenido) {
        if (contenido.length < PREFIJO_PDF.length()) {
            return false;
        }
        String prefijo = new String(contenido, 0, PREFIJO_PDF.length(), StandardCharsets.US_ASCII);
        return PREFIJO_PDF.equals(prefijo);
    }

    /** Only re-validated when re-association is requested; failures leave the document unchanged. */
    private Mono<Documento> revalidar(
            Documento documento, ActualizarDocumentoCommand command, String token) {
        if (command.expedienteId() == null) {
            return Mono.just(documento);
        }
        return expedienteClient.obtener(command.expedienteId(), token).map(resumen -> documento);
    }

    private Mono<Void> borrar(Documento documento) {
        return documentoRepository.delete(documento)
                .then(storage.eliminar(documento.getUbicacion())
                        .onErrorResume(e -> {
                            log.warn(
                                    "No se pudo eliminar el objeto {} del almacenamiento: {}",
                                    documento.getUbicacion(),
                                    e.getMessage());
                            return Mono.empty();
                        }));
    }

    /** Scoped lookup: unknown or cross-estudio id → 404 (always before any role/estado check). */
    private Mono<Documento> porEstudio(UUID id, UUID estudioId) {
        return documentoRepository.findByIdAndEstudioId(id, estudioId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Documento no encontrado")));
    }

    private static String truncar(String valor) {
        if (valor == null) {
            return null;
        }
        return valor.length() > NOMBRE_MAXIMO ? valor.substring(0, NOMBRE_MAXIMO) : valor;
    }
}
