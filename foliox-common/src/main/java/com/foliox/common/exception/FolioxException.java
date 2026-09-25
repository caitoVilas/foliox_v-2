package com.foliox.common.exception;

public abstract class FolioxException extends RuntimeException {

    protected FolioxException(String message) {
        super(message);
    }

    protected FolioxException(String message, Throwable cause) {
        super(message, cause);
    }
}
