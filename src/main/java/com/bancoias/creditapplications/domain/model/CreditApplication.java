package com.bancoias.creditapplications.domain.model;

import com.bancoias.creditapplications.domain.model.enums.CreditApplicationStatus;
import com.bancoias.creditapplications.domain.model.enums.RejectionReason;
import com.bancoias.creditapplications.domain.policy.CreditApplicationPolicy;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Getter
public final class CreditApplication {
    private final Long id;

    private final String applicationReference;

    private final String customerId;

    private final BigDecimal amount;

    private final Integer termMonths;

    private final CreditApplicationStatus status;

    private final RejectionReason rejectionReason;

    private final LocalDateTime processedAt;

    private final String requestHash;

    private CreditApplication(Long id, String reference, String customerId, BigDecimal amount,
                              Integer termMonths, CreditApplicationStatus status,
                              RejectionReason reason, LocalDateTime processedAt, String requestHash) {
        if (reference == null || reference.isBlank() || customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("applicationReference y customerId son obligatorios");
        }
        if (amount == null || termMonths == null) {
            throw new IllegalArgumentException("amount y termMonths son obligatorios");
        }
        if (status == null || processedAt == null
                || (status == CreditApplicationStatus.APPROVED && reason != null)
                || (status == CreditApplicationStatus.REJECTED && reason == null)) {
            throw new IllegalArgumentException("Resultado de solicitud incoherente");
        }
        this.id = id;
        this.applicationReference = reference;
        this.customerId = customerId;
        this.amount = amount;
        this.termMonths = termMonths;
        this.status = status;
        this.rejectionReason = reason;
        this.processedAt = processedAt;
        this.requestHash = requestHash;
    }

    public static CreditApplication approve(String reference, String customerId, BigDecimal amount,
                                            Integer termMonths, LocalDateTime processedAt) {
        if (CreditApplicationPolicy.evaluate(amount, termMonths).isPresent()) {
            throw new IllegalArgumentException("No se puede aprobar un monto o plazo invalido");
        }
        return new CreditApplication(null, reference, customerId, amount, termMonths,
                CreditApplicationStatus.APPROVED, null, processedAt, null);
    }

    public static CreditApplication reject(String reference, String customerId, BigDecimal amount,
                                           Integer termMonths, RejectionReason reason, LocalDateTime processedAt) {
        return new CreditApplication(null, reference, customerId, amount, termMonths,
                CreditApplicationStatus.REJECTED, reason, processedAt, null);
    }

    /** Restores a processed result without reapplying today's approval policy. */
    public static CreditApplication restore(Long id, String reference, String customerId, BigDecimal amount,
                                            Integer termMonths, CreditApplicationStatus status,
                                            RejectionReason reason, LocalDateTime processedAt, String requestHash) {
        return new CreditApplication(id, reference, customerId, amount, termMonths,
                status, reason, processedAt, requestHash);
    }

    public boolean matchesRequest(String customerId, BigDecimal amount, Integer termMonths) {
        return Objects.equals(this.customerId, customerId) && amount != null
                && this.amount.compareTo(amount) == 0 && Objects.equals(this.termMonths, termMonths);
    }
}
