package com.bancoias.creditapplications.controller;

import com.bancoias.creditapplications.domain.model.enums.CreditApplicationStatus;
import com.bancoias.creditapplications.domain.model.enums.RejectionReason;
import com.bancoias.creditapplications.dto.request.CreditApplicationRequest;
import com.bancoias.creditapplications.dto.response.CreditApplicationResponse;
import com.bancoias.creditapplications.exception.ApplicationReferenceConflictException;
import com.bancoias.creditapplications.exception.CreditApplicationNotFoundException;
import com.bancoias.creditapplications.exception.GlobalExceptionHandler;
import com.bancoias.creditapplications.service.CreditApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class CreditApplicationControllerTests {

    private CreditApplicationService service;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        service = mock(CreditApplicationService.class);
        client = WebTestClient.bindToController(new CreditApplicationController(service))
                .controllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void returnsApprovedDecision() {
        CreditApplicationRequest request = new CreditApplicationRequest(
                "REF-001", "CLI-1001", new BigDecimal("6000000"), 24);
        when(service.process(request)).thenReturn(Mono.just(new CreditApplicationResponse(
                request.applicationReference(), request.customerId(), request.amount(),
                request.termMonths(), CreditApplicationStatus.APPROVED, null,
                LocalDateTime.of(2026, 10, 1, 12, 0))));

        client.post().uri("/api/credit-applications")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.applicationReference").isEqualTo("REF-001")
                .jsonPath("$.customerId").isEqualTo("CLI-1001")
                .jsonPath("$.amount").isEqualTo(6000000)
                .jsonPath("$.termMonths").isEqualTo(24)
                .jsonPath("$.status").isEqualTo("APPROVED")
                .jsonPath("$.processedAt").isEqualTo("2026-10-01T12:00:00");

        verify(service).process(request);
    }

    @Test
    void passesInvalidBusinessValuesToServiceAndReturnsRejection() {
        CreditApplicationRequest request = new CreditApplicationRequest(
                "REF-002", "CLI-1001", BigDecimal.ZERO, 5);
        when(service.process(request)).thenReturn(Mono.just(new CreditApplicationResponse(
                request.applicationReference(), request.customerId(), request.amount(),
                request.termMonths(), CreditApplicationStatus.REJECTED,
                RejectionReason.INVALID_AMOUNT, LocalDateTime.of(2026, 10, 1, 12, 0))));

        client.post().uri("/api/credit-applications")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(request)
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("REJECTED")
                .jsonPath("$.rejectionReason").isEqualTo("INVALID_AMOUNT");

        verify(service).process(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"customerId\":\"CLI-1001\",\"amount\":100,\"termMonths\":12}",
            "{\"applicationReference\":\" \",\"customerId\":\"CLI-1001\",\"amount\":100,\"termMonths\":12}",
            "{\"applicationReference\":\"REF-001\",\"amount\":100,\"termMonths\":12}",
            "{\"applicationReference\":\"REF-001\",\"customerId\":\" \",\"amount\":100,\"termMonths\":12}",
            "{\"applicationReference\":\"REF-001\",\"customerId\":\"CLI-1001\",\"termMonths\":12}",
            "{\"applicationReference\":\"REF-001\",\"customerId\":\"CLI-1001\",\"amount\":100}",
            "{invalid-json}"
    })
    void rejectsInvalidInputBeforeCallingService(String json) {
        client.post().uri("/api/credit-applications")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(json)
                .exchange().expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.message").isNotEmpty()
                .jsonPath("$.timestamp").value(value ->
                        assertDoesNotThrow(() -> Instant.parse((String) value)));

        verifyNoInteractions(service);
    }

    @Test
    void findsApplicationByReference() {
        when(service.findByApplicationReference("REF-001"))
                .thenReturn(Mono.just(response("REF-001")));

        client.get().uri("/api/credit-applications/REF-001")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.applicationReference").isEqualTo("REF-001");

        verify(service).findByApplicationReference("REF-001");
    }

    @Test
    void returnsNotFoundForMissingReference() {
        when(service.findByApplicationReference("MISSING"))
                .thenReturn(Mono.error(new CreditApplicationNotFoundException("No existe")));

        client.get().uri("/api/credit-applications/MISSING")
                .exchange().expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CREDIT_APPLICATION_NOT_FOUND")
                .jsonPath("$.message").isEqualTo("No existe")
                .jsonPath("$.timestamp").isNotEmpty();
    }

    @Test
    void listsRecentApplicationsWithDefaultLimit() {
        when(service.findRecent(20)).thenReturn(Flux.just(response("REF-002"), response("REF-001")));

        client.get().uri("/api/credit-applications")
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].applicationReference").isEqualTo("REF-002")
                .jsonPath("$[1].applicationReference").isEqualTo("REF-001");

        verify(service).findRecent(20);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void acceptsCustomLimit(int limit) {
        when(service.findRecent(limit)).thenReturn(Flux.just(response("REF-001")));

        client.get().uri("/api/credit-applications?limit=" + limit)
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[0].applicationReference").isEqualTo("REF-001");

        verify(service).findRecent(limit);
    }

    @Test
    void returnsEmptyArrayWhenThereAreNoRecentApplications() {
        when(service.findRecent(20)).thenReturn(Flux.empty());

        client.get().uri("/api/credit-applications")
                .exchange().expectStatus().isOk()
                .expectBody().json("[]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "101", "abc"})
    void rejectsInvalidLimit(String limit) {
        client.get().uri("/api/credit-applications?limit=" + limit)
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_REQUEST");

        verifyNoInteractions(service);
    }

    @Test
    void returnsConflictForReferenceWithDifferentPayload() {
        CreditApplicationRequest request = new CreditApplicationRequest(
                "REF-001", "CLI-1001", new BigDecimal("100"), 12);
        when(service.process(request)).thenReturn(Mono.error(
                new ApplicationReferenceConflictException("Referencia procesada con datos diferentes")));

        client.post().uri("/api/credit-applications")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(request)
                .exchange().expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("APPLICATION_REFERENCE_CONFLICT")
                .jsonPath("$.message").isEqualTo("Referencia procesada con datos diferentes")
                .jsonPath("$.timestamp").isNotEmpty();
    }

    @Test
    void hidesTechnicalDetailsOfUnexpectedErrors() {
        when(service.findByApplicationReference("REF-001"))
                .thenReturn(Mono.error(new IllegalStateException("Sensitive database details")));

        client.get().uri("/api/credit-applications/REF-001")
                .exchange().expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INTERNAL_ERROR")
                .jsonPath("$.message").isEqualTo("No fue posible completar la solicitud")
                .jsonPath("$.timestamp").isNotEmpty()
                .jsonPath("$.trace").doesNotExist();
    }

    @Test
    void preservesUnsupportedMediaTypeStatus() {
        client.post().uri("/api/credit-applications")
                .contentType(MediaType.TEXT_PLAIN).bodyValue("not json")
                .exchange().expectStatus().isEqualTo(415)
                .expectBody().jsonPath("$.code").isEqualTo("UNSUPPORTED_MEDIA_TYPE");

        verifyNoInteractions(service);
    }

    private CreditApplicationResponse response(String reference) {
        return new CreditApplicationResponse(reference, "CLI-1001", new BigDecimal("100"),
                12, CreditApplicationStatus.APPROVED, null, LocalDateTime.of(2026, 10, 1, 12, 0));
    }

    @Test
    void rejectsMissingBody() {
        client.post().uri("/api/credit-applications")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_REQUEST");

        verifyNoInteractions(service);
    }
}
