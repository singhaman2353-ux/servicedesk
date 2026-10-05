package com.servicedesk.exception;

import org.springframework.http.HttpStatus;

/** One message for every login failure, so responses never reveal which part was wrong. */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
    }
}