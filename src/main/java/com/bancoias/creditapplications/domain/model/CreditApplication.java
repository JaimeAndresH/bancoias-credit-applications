package com.bancoias.creditapplications.domain.model;

import com.bancoias.creditapplications.domain.model.enums.CreditApplicationStatus;
import com.bancoias.creditapplications.domain.model.enums.RejectionReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("credit_application")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditApplication {
    @Id
    private Long id;

    private String applicationReference;

    private String customerId;

    private BigDecimal amount;

    private Integer termMonths;

    private CreditApplicationStatus status;

    private RejectionReason rejectionReason;

    private LocalDateTime processedAt;

    private String requestHash;
}
