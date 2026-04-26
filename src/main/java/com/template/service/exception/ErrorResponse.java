package com.template.service.exception;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ErrorResponse(
        @JsonProperty("error_code") String errorCode,
        String message,
        @JsonProperty("correlation_id") String correlationId) {
}
