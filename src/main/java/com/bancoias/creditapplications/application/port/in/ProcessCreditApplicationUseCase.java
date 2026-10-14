package com.bancoias.creditapplications.application.port.in;

import com.bancoias.creditapplications.dto.request.CreditApplicationRequest;
import com.bancoias.creditapplications.dto.response.CreditApplicationResponse;
import reactor.core.publisher.Mono;

public interface ProcessCreditApplicationUseCase {
    Mono<CreditApplicationResponse> process(CreditApplicationRequest request);
}
