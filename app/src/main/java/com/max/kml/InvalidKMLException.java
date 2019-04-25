package com.max.kml;

public class InvalidKMLException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidKMLException(String message) {
        super(message);
    }

    public InvalidKMLException(String message, Throwable cause) {
        super(message, cause);
    }

}
