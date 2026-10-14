package com.bancoias.creditapplications.repository;

import com.bancoias.creditapplications.domain.model.CreditApplication;
import com.bancoias.creditapplications.domain.model.enums.CreditApplicationStatus;
import com.bancoias.creditapplications.domain.model.enums.RejectionReason;
import com.bancoias.creditapplications.repository.entity.CreditApplicationEntity;
import com.bancoias.creditapplications.repository.r2dbc.CreditApplicationPersistenceAdapter;
import com.bancoias.creditapplications.repository.r2dbc.CreditApplicationR2dbcRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PersistenceAdapterTests {
    @Test
    void preservesRejectedBusinessValuesAndPersistenceMetadataAcrossSave() {
        var repository = mock(CreditApplicationR2dbcRepository.class);
        var adapter = new CreditApplicationPersistenceAdapter(repository);
        var time = LocalDateTime.of(2026, 10, 1, 12, 0);
        var application = CreditApplication.restore(7L, "REF", "CLI", new BigDecimal("-10.00"),
                5, CreditApplicationStatus.REJECTED, RejectionReason.INVALID_AMOUNT, time, "hash");
        when(repository.save(any(CreditApplicationEntity.class))).thenAnswer(invocation -> {
            CreditApplicationEntity entity = invocation.getArgument(0);
            assertEquals(7L, entity.getId());
            assertEquals("REF", entity.getApplicationReference());
            assertEquals("CLI", entity.getCustomerId());
            assertEquals(new BigDecimal("-10.00"), entity.getAmount());
            assertEquals(5, entity.getTermMonths());
            assertEquals(CreditApplicationStatus.REJECTED, entity.getStatus());
            assertEquals(RejectionReason.INVALID_AMOUNT, entity.getRejectionReason());
            assertEquals(time, entity.getProcessedAt());
            assertEquals("hash", entity.getRequestHash());
            return Mono.just(entity);
        });

        StepVerifier.create(adapter.save(application)).assertNext(saved -> {
            assertEquals(application.getId(), saved.getId());
            assertEquals(application.getApplicationReference(), saved.getApplicationReference());
            assertEquals(application.getCustomerId(), saved.getCustomerId());
            assertEquals(application.getAmount(), saved.getAmount());
            assertEquals(application.getTermMonths(), saved.getTermMonths());
            assertEquals(application.getStatus(), saved.getStatus());
            assertEquals(application.getRejectionReason(), saved.getRejectionReason());
            assertEquals(application.getProcessedAt(), saved.getProcessedAt());
            assertEquals(application.getRequestHash(), saved.getRequestHash());
        }).verifyComplete();
    }
}
