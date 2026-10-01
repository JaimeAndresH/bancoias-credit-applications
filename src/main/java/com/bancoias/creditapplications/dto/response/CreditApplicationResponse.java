package com.bancoias.creditapplications.dto.response;

import com.bancoias.creditapplications.domain.model.enums.CreditApplicationStatus;
import com.bancoias.creditapplications.domain.model.enums.RejectionReason;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreditApplicationResponse(
        String applicationReference,
        String customerId,
        BigDecimal amount,
        Integer termMonths,
        CreditApplicationStatus status,
        RejectionReason rejectionReason,
        LocalDateTime processedAt
) {}
