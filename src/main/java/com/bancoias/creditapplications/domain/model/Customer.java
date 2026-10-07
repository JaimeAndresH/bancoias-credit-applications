package com.bancoias.creditapplications.domain.model;

import com.bancoias.creditapplications.domain.model.enums.CustomerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public final class Customer {
    private final Long id;

    private final String customerId;

    private final CustomerStatus status;

    private final BigDecimal maxApprovedAmount;

    private final BigDecimal currentApprovedAmount;
    public boolean isEligible() {
        return status == CustomerStatus.ELIGIBLE;
    }
}
