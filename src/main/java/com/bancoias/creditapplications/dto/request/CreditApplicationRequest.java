package com.bancoias.creditapplications.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record  CreditApplicationRequest(
        @NotBlank String applicationReference,
        @NotBlank String customerId,
        @NotNull BigDecimal amount,
        @NotNull Integer termMonths
) {}
