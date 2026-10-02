package com.foliox.documentos.web;

import java.util.List;
import java.util.UUID;

import com.foliox.documentos.documento.ActualizarDocumentoCommand;
import com.foliox.documentos.documento.DocumentoResponse;
import com.foliox.documentos.documento.DocumentoService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/documentos")
@Tag(name = "Documentos", description = "CRUD de documentos del estudio (multi-tenant)")
@SecurityRequirement(name = "bearerAuth")
public class DocumentoController {

    private final DocumentoService documentoService;

    public DocumentoController(DocumentoService documentoService) {
        this.documentoService = documentoService;
    }

    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<DocumentoResponse> crear(
            @RequestPart("file") FilePart file,
            @RequestPart("expedienteId") UUID expedienteId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @AuthenticationPrincipal Jwt jwt) {
        return documentoService.crear(estudioId(jwt), expedienteId, file, token);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<List<DocumentoResponse>> listar(@AuthenticationPrincipal Jwt jwt) {
        return documentoService.listar(estudioId(jwt));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<DocumentoResponse> obtener(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return documentoService.obtener(id, estudioId(jwt));
    }

    @GetMapping("/{id}/contenido")
    public Mono<ResponseEntity<Flux<DataBuffer>>> contenido(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return documentoService.contenido(id, estudioId(jwt));
    }

    @PatchMapping(
            value = "/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<DocumentoResponse> actualizar(
            @PathVariable UUID id,
            @RequestBody ActualizarDocumentoCommand command,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @AuthenticationPrincipal Jwt jwt) {
        return documentoService.actualizar(id, estudioId(jwt), command, token);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> eliminar(
            @PathVariable UUID id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @AuthenticationPrincipal Jwt jwt) {
        return documentoService.eliminar(id, estudioId(jwt), token, isAdmin(jwt));
    }

    /** Tenancy comes only from the JWT claim — body {@code estudio_id} is ignored. */
    private static UUID estudioId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("estudio_id"));
    }

    private static boolean isAdmin(Jwt jwt) {
        return "ADMIN".equals(jwt.getClaimAsString("rol"));
    }
}
