package com.jaconis.finance_api.dto.response;

public record TokenResponse(
        String token,
        UserResponse user
) {}
