package com.bancoias.creditapplications.service;

import com.bancoias.creditapplications.domain.model.CreditApplication;
import com.bancoias.creditapplications.domain.model.Customer;
import com.bancoias.creditapplications.domain.model.enums.CreditApplicationStatus;
import com.bancoias.creditapplications.domain.model.enums.CustomerStatus;
import com.bancoias.creditapplications.domain.model.enums.RejectionReason;
import com.bancoias.creditapplications.dto.request.CreditApplicationRequest;
import com.bancoias.creditapplications.dto.response.CreditApplicationResponse;
import com.bancoias.creditapplications.exception.ApplicationReferenceConflictException;
import com.bancoias.creditapplications.exception.CreditApplicationNotFoundException;
import com.bancoias.creditapplications.mapper.CreditApplicationMapper;
import com.bancoias.creditapplications.messaging.ApprovalEventOutbox;
import com.bancoias.creditapplications.repository.CreditApplicationRepository;
import com.bancoias.creditapplications.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.ReactiveTransaction;
import org.springframework.transaction.ReactiveTransactionManager;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CreditApplicationServiceTests {

    private CreditApplicationRepository applications;
    private CustomerRepository customers;
    private ReactiveTransactionManager transactionManager;
    private ReactiveTransaction transaction;
    private CreditApplicationService service;
    private ApprovalEventOutbox outbox;

    @BeforeEach
    void setUp() {
        applications = mock(CreditApplicationRepository.class);
        customers = mock(CustomerRepository.class);
        transactionManager = mock(ReactiveTransactionManager.class);
        transaction = mock(ReactiveTransaction.class);
        CreditApplicationMapper mapper = mock(CreditApplicationMapper.class);
        outbox = mock(ApprovalEventOutbox.class);
        when(outbox.recordApproved(any())).thenReturn(Mono.empty());

        when(transactionManager.getReactiveTransaction(any())).thenReturn(Mono.just(transaction));
        when(transactionManager.commit(transaction)).thenReturn(Mono.empty());
        when(transactionManager.rollback(transaction)).thenReturn(Mono.empty());
        when(applications.findByApplicationReference("REF-1")).thenReturn(Mono.empty());
        when(applications.save(any(CreditApplication.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(mapper.toResponse(any())).thenAnswer(invocation -> {
            CreditApplication application = invocation.getArgument(0);
            return new CreditApplicationResponse(
                    application.getApplicationReference(), application.getCustomerId(),
                    application.getAmount(), application.getTermMonths(), application.getStatus(),
                    application.getRejectionReason(), application.getProcessedAt());
        });
        service = new CreditApplicationService(mapper, applications, customers, transactionManager, outbox);
    }

    @ParameterizedTest
    @CsvSource({"6", "60"})
    void approvesWhenCreditReservationSucceeds(int term) {
        eligibleCustomer();
        when(customers.reserveCreditLimit("CLI-1", new BigDecimal("100")))
                .thenReturn(Mono.just(1));

        CreditApplicationResponse response = process("100", term);

        assertEquals(CreditApplicationStatus.APPROVED, response.status());
        assertNull(response.rejectionReason());
        assertNotNull(response.processedAt());
        assertEquals("REF-1", response.applicationReference());
        assertEquals("CLI-1", response.customerId());
        assertEquals(new BigDecimal("100"), response.amount());
        assertEquals(term, response.termMonths());
        verify(transactionManager).commit(transaction);
        verify(applications, times(1)).save(any(CreditApplication.class));
        verify(outbox, times(1)).recordApproved(any(CreditApplication.class));
    }

    @ParameterizedTest
    @CsvSource({"0,12,INVALID_AMOUNT", "-1,12,INVALID_AMOUNT",
            "100,5,INVALID_TERM", "100,61,INVALID_TERM"})
    void persistsInvalidBusinessValues(String amount, int term, RejectionReason reason) {
        assertRejected(process(amount, term), reason);
        verifyNoInteractions(customers);
        verifyNoInteractions(outbox);
        verify(applications).save(any(CreditApplication.class));
        verify(transactionManager).commit(transaction);
    }

    @Test
    void rejectsMissingCustomer() {
        when(customers.findByCustomerId("CLI-1")).thenReturn(Mono.empty());

        assertRejected(process("100", 12), RejectionReason.CUSTOMER_NOT_FOUND);
        verify(customers, never()).reserveCreditLimit(any(), any());
    }

    @Test
    void rejectsBlockedCustomerWithoutReservingCredit() {
        when(customers.findByCustomerId("CLI-1")).thenReturn(Mono.just(
                Customer.builder().customerId("CLI-1").status(CustomerStatus.BLOCKED).build()));

        assertRejected(process("100", 12), RejectionReason.CUSTOMER_BLOCKED);
        verify(customers, never()).reserveCreditLimit(any(), any());
    }

    @Test
    void rejectsWhenAtomicReservationFails() {
        eligibleCustomer();
        when(customers.reserveCreditLimit("CLI-1", new BigDecimal("100")))
                .thenReturn(Mono.just(0));

        assertRejected(process("100", 12), RejectionReason.INSUFFICIENT_CREDIT_LIMIT);
    }

    @Test
    void rollsBackWhenSavingFails() {
        eligibleCustomer();
        when(customers.reserveCreditLimit(any(), any())).thenReturn(Mono.just(1));
        IllegalStateException failure = new IllegalStateException("save failed");
        when(applications.save(any(CreditApplication.class))).thenReturn(Mono.error(failure));

        assertSame(failure, assertThrows(IllegalStateException.class, () -> process("100", 12)));
        verify(transactionManager).rollback(transaction);
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void returnsExistingApplicationWithoutConsumingCreditAgain() {
        when(applications.findByApplicationReference("REF-1"))
                .thenReturn(Mono.just(existing("100.00")));

        assertEquals(CreditApplicationStatus.APPROVED, process("100", 12).status());
        verifyNoInteractions(customers, transactionManager);
        verifyNoInteractions(outbox);
        verify(applications, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"100,false", "200,true"})
    void resolvesDuplicateReferenceAfterRollback(String storedAmount, boolean conflicts) {
        eligibleCustomer();
        when(customers.reserveCreditLimit(any(), any())).thenReturn(Mono.just(1));
        when(applications.save(any(CreditApplication.class)))
                .thenReturn(Mono.error(new DuplicateKeyException("duplicate reference")));
        when(applications.findByApplicationReference("REF-1"))
                .thenReturn(Mono.empty(), Mono.just(existing(storedAmount)));

        if (conflicts) {
            assertThrows(ApplicationReferenceConflictException.class, () -> process("100", 12));
        } else {
            assertEquals(CreditApplicationStatus.APPROVED, process("100", 12).status());
        }

        var order = inOrder(transactionManager, applications);
        order.verify(applications).findByApplicationReference("REF-1");
        order.verify(transactionManager).getReactiveTransaction(any());
        order.verify(applications).save(any(CreditApplication.class));
        order.verify(transactionManager).rollback(transaction);
        order.verify(applications).findByApplicationReference("REF-1");
    }

    @Test
    void findsAndMapsApplicationByReference() {
        when(applications.findByApplicationReference("REF-1"))
                .thenReturn(Mono.just(existing("100")));

        StepVerifier.create(service.findByApplicationReference("REF-1"))
                .assertNext(response -> {
                    assertEquals("REF-1", response.applicationReference());
                    assertEquals(CreditApplicationStatus.APPROVED, response.status());
                })
                .verifyComplete();

        verifyNoInteractions(customers, transactionManager);
        verify(applications, never()).save(any());
    }

    @Test
    void signalsMissingReference() {
        StepVerifier.create(service.findByApplicationReference("REF-1"))
                .expectError(CreditApplicationNotFoundException.class).verify();
    }

    @Test
    void mapsRecentApplicationsInRepositoryOrder() {
        CreditApplication newest = existing("200");
        newest.setApplicationReference("REF-2");
        newest.setProcessedAt(LocalDateTime.of(2026, 10, 1, 13, 0));
        when(applications.findRecent(2)).thenReturn(Flux.just(newest, existing("100")));

        StepVerifier.create(service.findRecent(2))
                .assertNext(response -> assertEquals("REF-2", response.applicationReference()))
                .assertNext(response -> assertEquals("REF-1", response.applicationReference()))
                .verifyComplete();

        verify(applications).findRecent(2);
        verifyNoInteractions(customers, transactionManager);
    }

    @Test
    void returnsEmptyRecentApplications() {
        when(applications.findRecent(20)).thenReturn(Flux.empty());

        StepVerifier.create(service.findRecent(20)).verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    void rejectsInvalidRecentLimitWithoutQueryingDatabase(int limit) {
        StepVerifier.create(service.findRecent(limit))
                .expectError(IllegalArgumentException.class).verify();

        verify(applications, never()).findRecent(anyInt());
    }

    @Test
    void rollsBackApprovalWhenRecordingEventFails() {
        eligibleCustomer();
        when(customers.reserveCreditLimit(any(), any())).thenReturn(Mono.just(1));
        IllegalStateException failure = new IllegalStateException("outbox insert failed");
        when(outbox.recordApproved(any())).thenReturn(Mono.error(failure));

        assertSame(failure, assertThrows(IllegalStateException.class, () -> process("100", 12)));
        verify(transactionManager).rollback(transaction);
        verify(transactionManager, never()).commit(any());
    }

    private void eligibleCustomer() {
        when(customers.findByCustomerId("CLI-1")).thenReturn(Mono.just(
                Customer.builder().customerId("CLI-1").status(CustomerStatus.ELIGIBLE).build()));
    }

    private CreditApplicationResponse process(String amount, int term) {
        return service.process(new CreditApplicationRequest(
                "REF-1", "CLI-1", new BigDecimal(amount), term)).block(Duration.ofSeconds(5));
    }

    private CreditApplication existing(String amount) {
        return CreditApplication.builder()
                .applicationReference("REF-1").customerId("CLI-1")
                .amount(new BigDecimal(amount)).termMonths(12)
                .status(CreditApplicationStatus.APPROVED)
                .processedAt(LocalDateTime.of(2026, 10, 1, 12, 0)).build();
    }

    private void assertRejected(CreditApplicationResponse response, RejectionReason reason) {
        assertEquals(CreditApplicationStatus.REJECTED, response.status());
        assertEquals(reason, response.rejectionReason());
    }
}
