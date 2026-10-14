package com.bancoias.creditapplications.application.port.in;

import com.bancoias.creditapplications.dto.response.CreditApplicationResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface QueryCreditApplicationsUseCase {
    Mono<CreditApplicationResponse> findByApplicationReference(String applicationReference);
    Flux<CreditApplicationResponse> findRecent(int limit);
}
