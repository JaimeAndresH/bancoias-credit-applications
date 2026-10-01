package com.bancoias.creditapplications.dto.response;

import java.time.Instant;

public record ApiErrorResponse(String code, String message, Instant timestamp) {
}
