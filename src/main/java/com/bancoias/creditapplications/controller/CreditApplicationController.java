package com.bancoias.creditapplications.controller;

import com.bancoias.creditapplications.dto.request.CreditApplicationRequest;
import com.bancoias.creditapplications.dto.response.CreditApplicationResponse;
import com.bancoias.creditapplications.service.CreditApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/credit-applications")
public class CreditApplicationController {

    private final CreditApplicationService service;

    public CreditApplicationController(CreditApplicationService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<CreditApplicationResponse> process(
            @Valid @RequestBody CreditApplicationRequest request) {
        return service.process(request);
    }

    @GetMapping(value = "/{applicationReference}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<CreditApplicationResponse> findByApplicationReference(
            @PathVariable("applicationReference") String applicationReference) {
        return service.findByApplicationReference(applicationReference);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<CreditApplicationResponse> findRecent(
            @RequestParam(name = "limit", defaultValue = "20") @Min(1) @Max(100) int limit) {
        return service.findRecent(limit);
    }
}
