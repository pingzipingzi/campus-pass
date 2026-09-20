package com.example.campuspass.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class NullException extends RuntimeException {
    private final Integer code;
    private final HttpStatus httpStatus;

    public NullException(Integer code, String message, HttpStatus httpStatus) {
        super(message);
        this.code=code;
        this.httpStatus = httpStatus;
    }
}
