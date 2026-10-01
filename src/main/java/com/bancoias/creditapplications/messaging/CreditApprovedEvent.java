package com.bancoias.creditapplications.messaging;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CreditApprovedEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String applicationReference,
        String customerId,
        BigDecimal amount,
        Integer termMonths,
        LocalDateTime processedAt) {
}
