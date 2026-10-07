package com.bancoias.creditapplications.domain;

import com.bancoias.creditapplications.domain.model.CreditApplication;
import com.bancoias.creditapplications.domain.model.Customer;
import com.bancoias.creditapplications.domain.model.enums.*;
import com.bancoias.creditapplications.domain.policy.CreditApplicationPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class DomainTests {
    private final LocalDateTime processedAt = LocalDateTime.of(2026, 10, 1, 12, 0);

    @ParameterizedTest
    @CsvSource({"0,5,INVALID_AMOUNT", "-1,12,INVALID_AMOUNT", "100,5,INVALID_TERM", "100,61,INVALID_TERM"})
    void evaluatesRejectionWithAmountTakingPrecedence(String amount, int term, RejectionReason reason) {
        assertEquals(reason, CreditApplicationPolicy.evaluate(new BigDecimal(amount), term).orElseThrow());
    }

    @ParameterizedTest
    @CsvSource({"0.01,6", "100,60"})
    void acceptsBoundaryTerms(String amount, int term) {
        assertTrue(CreditApplicationPolicy.evaluate(new BigDecimal(amount), term).isEmpty());
    }

    @Test
    void missingValuesAreNotBusinessRejections() {
        assertThrows(IllegalArgumentException.class, () -> CreditApplicationPolicy.evaluate(null, 12));
        assertThrows(IllegalArgumentException.class, () -> CreditApplicationPolicy.evaluate(BigDecimal.ONE, null));
    }

    @Test
    void invalidAmountCanBeStoredAsARejectionButCannotBeApproved() {
        var application = CreditApplication.reject("REF", "CLI", BigDecimal.ZERO, 12,
                RejectionReason.INVALID_AMOUNT, processedAt);
        assertEquals(CreditApplicationStatus.REJECTED, application.getStatus());
        assertEquals(BigDecimal.ZERO, application.getAmount());
        assertThrows(IllegalArgumentException.class,
                () -> CreditApplication.approve("REF", "CLI", BigDecimal.ZERO, 12, processedAt));
    }

    @Test
    void protectsResultConsistency() {
        assertThrows(IllegalArgumentException.class, () -> CreditApplication.reject(
                "REF", "CLI", BigDecimal.ONE, 12, null, processedAt));
        assertThrows(IllegalArgumentException.class, () -> CreditApplication.approve(
                "REF", "CLI", BigDecimal.ONE, 12, null));
        assertThrows(IllegalArgumentException.class, () -> CreditApplication.restore(
                1L, "REF", "CLI", BigDecimal.ONE, 12, CreditApplicationStatus.APPROVED,
                RejectionReason.INVALID_AMOUNT, processedAt, null));
    }

    @Test
    void comparesBusinessValuesWithoutDependingOnDecimalScale() {
        var application = CreditApplication.approve("REF", "CLI", new BigDecimal("100.00"), 12, processedAt);
        assertTrue(application.matchesRequest("CLI", new BigDecimal("100"), 12));
        assertFalse(application.matchesRequest("OTHER", new BigDecimal("100"), 12));
        assertFalse(application.matchesRequest("CLI", new BigDecimal("101"), 12));
        assertFalse(application.matchesRequest("CLI", new BigDecimal("100"), 13));
    }

    @Test
    void eligibilityBelongsToCustomer() {
        assertTrue(Customer.builder().status(CustomerStatus.ELIGIBLE).build().isEligible());
        assertFalse(Customer.builder().status(CustomerStatus.BLOCKED).build().isEligible());
        assertFalse(Customer.builder().build().isEligible());
    }
}
