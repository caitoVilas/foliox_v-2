package com.foliox.documentos.exception;

import com.foliox.common.exception.FolioxException;

/** Non-PDF upload (content-type or magic bytes) → 400 (design decision 5). */
public class ArchivoNoValidoException extends FolioxException {

    public ArchivoNoValidoException(String message) {
        super(message);
    }
}
