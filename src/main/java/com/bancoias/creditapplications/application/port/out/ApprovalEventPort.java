package com.bancoias.creditapplications.application.port.out;

import com.bancoias.creditapplications.domain.model.CreditApplication;
import reactor.core.publisher.Mono;

/** Records the approval durably within the application's current transaction. */
public interface ApprovalEventPort {
    Mono<Void> recordApproved(CreditApplication application);
}
