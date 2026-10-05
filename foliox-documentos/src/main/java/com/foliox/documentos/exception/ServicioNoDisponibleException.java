package com.foliox.documentos.exception;

import com.foliox.common.exception.FolioxException;

/** expedientes or MinIO failure → 502 (design decisions 3 and 9). Never fail open. */
public class ServicioNoDisponibleException extends FolioxException {

    public ServicioNoDisponibleException(String message) {
        super(message);
    }

    public ServicioNoDisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
