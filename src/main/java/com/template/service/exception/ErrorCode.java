package com.template.service.exception;

public enum ErrorCode {

    BAD_REQUEST("bad_request"),
    INTERNAL_SERVER_ERROR("internal_server_error");

    private final String value;

    ErrorCode(String value) {
        this.value = value;
    }

    public String value() {
        return this.value;
    }
}
