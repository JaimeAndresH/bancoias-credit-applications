package com.bancoias.creditapplications.exception;

import com.bancoias.creditapplications.dto.response.ApiErrorResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(CreditApplicationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(CreditApplicationNotFoundException error) {
        return errorResponse(HttpStatus.NOT_FOUND, "CREDIT_APPLICATION_NOT_FOUND", error.getMessage());
    }

    @ExceptionHandler(ApplicationReferenceConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ApplicationReferenceConflictException error) {
        return errorResponse(HttpStatus.CONFLICT, "APPLICATION_REFERENCE_CONFLICT", error.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception error) {
        logger.error("Error inesperado al atender una solicitud", error);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "No fue posible completar la solicitud");
    }

    @Override
    protected Mono<ResponseEntity<Object>> handleExceptionInternal(
            Exception error, Object body, HttpHeaders headers,
            HttpStatusCode status, ServerWebExchange exchange) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(error);
        }

        HttpStatus httpStatus = HttpStatus.resolve(status.value());
        String code = status.value() == 400 ? "INVALID_REQUEST"
                : status.is5xxServerError() ? "INTERNAL_ERROR"
                : httpStatus != null ? httpStatus.name() : "HTTP_ERROR";
        String message = status.value() == 400
                ? "Verifica los campos obligatorios, el formato JSON y los parametros de la solicitud"
                : status.is5xxServerError() ? "No fue posible completar la solicitud"
                : httpStatus != null ? httpStatus.getReasonPhrase() : "La solicitud no pudo procesarse";

        if (status.is5xxServerError()) {
            logger.error("Error HTTP al atender una solicitud", error);
        }

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        return Mono.just(new ResponseEntity<>(
                new ApiErrorResponse(code, message, Instant.now()), responseHeaders, status));
    }

    private ResponseEntity<ApiErrorResponse> errorResponse(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(code, message, Instant.now()));
    }
}
