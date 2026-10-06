package com.foliox.common.config;

import com.foliox.common.exception.SecretoFaltanteException;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Convierte {@link SecretoFaltanteException} en el banner
 * APPLICATION FAILED TO START con descripcion y accion en espanol.
 */
public class RequiredSecretsFailureAnalyzer extends AbstractFailureAnalyzer<SecretoFaltanteException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, SecretoFaltanteException cause) {
        // cause.getMessage() = "Faltan secretos obligatorios: [prop1, prop2]"
        String accion = "Cada secreto debe venir del entorno (sin default en application.yml). "
            + "Copia la plantilla y completa los valores: Copy-Item .env.example .env "
            + "(o `cp .env.example .env`), o exporta las variables de entorno antes de arrancar.";
        return new FailureAnalysis(cause.getMessage(), accion, cause);
    }
}
