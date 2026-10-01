package com.bancoias.creditapplications.domain.model;

import com.bancoias.creditapplications.domain.model.enums.CustomerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Table("customer")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {
    @Id
    private Long id;

    private String customerId;

    private CustomerStatus status;

    private BigDecimal maxApprovedAmount;

    private BigDecimal currentApprovedAmount;
}

