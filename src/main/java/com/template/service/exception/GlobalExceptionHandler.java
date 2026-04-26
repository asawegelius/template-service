package com.template.service.exception;

import com.template.service.logging.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;

@ControllerAdvice
public class GlobalExceptionHandler {

    static final String ERROR_CODE_MDC_KEY = "error_code";

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "Bad request", e);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_SERVER_ERROR,
                "Internal server error",
                e);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status, ErrorCode errorCode, String message, Exception exception) {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        String previousErrorCode = MDC.get(ERROR_CODE_MDC_KEY);
        MDC.put(ERROR_CODE_MDC_KEY, errorCode.value());

        try {
            if (status.is5xxServerError()) {
                LOGGER.error("{}: {}", errorCode.value(), exception.getMessage(), exception);
            } else {
                LOGGER.warn("{}: {}", errorCode.value(), exception.getMessage());
            }

            return ResponseEntity.status(status)
                    .body(new ErrorResponse(errorCode.value(), message, correlationId));
        }
        finally {
            if (previousErrorCode == null) {
                MDC.remove(ERROR_CODE_MDC_KEY);
            } else {
                MDC.put(ERROR_CODE_MDC_KEY, previousErrorCode);
            }
        }
    }
}
