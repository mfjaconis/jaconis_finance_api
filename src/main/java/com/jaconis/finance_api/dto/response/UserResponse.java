package com.jaconis.finance_api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        Instant createdAt
) {}
