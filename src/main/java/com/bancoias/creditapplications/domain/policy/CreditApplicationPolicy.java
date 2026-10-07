package com.bancoias.creditapplications.domain.policy;

import com.bancoias.creditapplications.domain.model.enums.RejectionReason;
import java.math.BigDecimal;
import java.util.Optional;

public final class CreditApplicationPolicy {
    private CreditApplicationPolicy() {}

    public static Optional<RejectionReason> evaluate(BigDecimal amount, Integer termMonths) {
        if (amount == null || termMonths == null) {
            throw new IllegalArgumentException("amount y termMonths son obligatorios");
        }
        if (amount.signum() <= 0) return Optional.of(RejectionReason.INVALID_AMOUNT);
        if (termMonths < 6 || termMonths > 60) return Optional.of(RejectionReason.INVALID_TERM);
        return Optional.empty();
    }
}
