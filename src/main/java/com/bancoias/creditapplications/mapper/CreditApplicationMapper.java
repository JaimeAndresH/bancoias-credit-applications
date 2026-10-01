package com.bancoias.creditapplications.mapper;

import com.bancoias.creditapplications.domain.model.CreditApplication;
import com.bancoias.creditapplications.dto.response.CreditApplicationResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CreditApplicationMapper {
    CreditApplicationResponse toResponse(CreditApplication creditApplication);
}
