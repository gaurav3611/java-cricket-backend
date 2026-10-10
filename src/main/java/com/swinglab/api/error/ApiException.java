package com.swinglab.api.error;

import org.springframework.http.HttpStatus;

/** An error the API reports on purpose. It becomes {"error": message} with the given status. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
