package com.foliox.common.exception;

import java.util.List;

/**
 * Un secreto obligatorio no resuelve a un valor real (vacio, ausente o
 * quedando como placeholder sin resolver, p. ej. ${POSTGRES_PASSWORD}).
 *
 * <p>Relanzada por {@link com.foliox.common.config.RequiredSecretsEnvironmentPostProcessor};
 * analizada por {@link com.foliox.common.config.RequiredSecretsFailureAnalyzer}
 * para mostrar el banner APPLICATION FAILED TO START.</p>
 */
public class SecretoFaltanteException extends IllegalStateException {

    private final transient List<String> propiedades;

    public SecretoFaltanteException(List<String> propiedades) {
        super("Faltan secretos obligatorios: " + propiedades);
        this.propiedades = List.copyOf(propiedades);
    }

    public List<String> getPropiedades() {
        return propiedades;
    }
}
