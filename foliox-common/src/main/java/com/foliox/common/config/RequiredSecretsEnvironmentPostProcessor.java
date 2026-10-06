package com.foliox.common.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.foliox.common.exception.SecretoFaltanteException;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

/**
 * Fail-fast para secretos obligatorios.
 *
 * <p>La resolucion de placeholders en {@code spring.r2dbc.password} (y en las demas
 * propiedades de conexion) es LAZY: sin un valor, la aplicacion arranca igual y solo
 * falla en el primer uso de la conexion. Este procesador corre ANTES del refresh del
 * contexto y aborta el boot si una propiedad obligatoria no resuelve a un valor real
 * (vacia, ausente o quedando como placeholder sin resolver, p. ej.
 * {@code ${POSTGRES_PASSWORD}}).</p>
 *
 * <p>Se registra en {@code META-INF/spring.factories} bajo la clave
 * {@code org.springframework.boot.EnvironmentPostProcessor}. {@code spring.r2dbc.password}
 * se valida SIEMPRE; {@code foliox.required-properties} (separada por coma) agrega
 * secretos adicionales del servicio, p. ej. {@code app.minio.secret-key}. Una lista
 * vacia no desactiva la validacion (fail-closed).</p>
 */
public class RequiredSecretsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** Corre despues de ConfigDataEnvironmentPostProcessor: application.yml ya esta cargado. */
    private static final int ORDER = ConfigDataEnvironmentPostProcessor.ORDER + 10;

    private static final String REQUIRED_PROPERTIES_KEY = "foliox.required-properties";

    private static final String DEFAULT_REQUIRED_PROPERTIES = "spring.r2dbc.password";

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Siempre validamos el default: la lista configurada SUMA secretos, nunca los
        // reemplaza, y una lista vacia/blanca no puede desactivar la validacion
        // (fail-closed: el control de seguridad no se puede silenciar por config).
        Set<String> aValidar = new LinkedHashSet<>();
        aValidar.add(DEFAULT_REQUIRED_PROPERTIES);
        String configured = environment.getProperty(REQUIRED_PROPERTIES_KEY);
        if (configured != null) {
            for (String rawName : configured.split(",")) {
                String propertyName = rawName.trim();
                if (!propertyName.isEmpty()) {
                    aValidar.add(propertyName);
                }
            }
        }

        List<String> invalidas = new ArrayList<>();
        for (String propertyName : aValidar) {
            if (!resuelveValorReal(environment, propertyName)) {
                invalidas.add(propertyName);
            }
        }

        if (!invalidas.isEmpty()) {
            throw new SecretoFaltanteException(invalidas);
        }
    }

    /**
     * No usa {@code environment.getProperty}: ese metodo resuelve placeholders de forma
     * estricta y lanza PlaceholderResolutionException antes de que podamos reportar qué
     * falta. Leemos el valor crudo por fuente (respeta precedencias) y resolvemos
     * nosotros de forma no estricta: lo no resuelto queda como {@code ${...}}.
     */
    private boolean resuelveValorReal(ConfigurableEnvironment environment, String propertyName) {
        String crudo = valorCrudo(environment, propertyName);
        if (crudo == null) {
            return false;
        }
        String resolved = environment.resolvePlaceholders(crudo);
        return !resolved.isBlank() && !resolved.contains("${");
    }

    private String valorCrudo(ConfigurableEnvironment environment, String propertyName) {
        for (PropertySource<?> source : environment.getPropertySources()) {
            Object value;
            try {
                value = source.getProperty(propertyName);
            }
            catch (RuntimeException ex) {
                continue;
            }
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }
}
