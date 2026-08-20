package org.cmb.application.dto;

import java.time.Instant;

public class ApiErrorResponse {

    private String code;
    private String message;
    private Instant time = Instant.now();

    public ApiErrorResponse() {
    }

    public ApiErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getTime() {
        return time;
    }

    public void setTime(Instant time) {
        this.time = time;
    }
}
