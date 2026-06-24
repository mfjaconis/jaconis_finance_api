package com.jaconis.finance_api.service;

import com.jaconis.finance_api.domain.entity.User;
import com.jaconis.finance_api.dto.request.RegisterRequest;
import com.jaconis.finance_api.dto.response.UserResponse;

public final class UserMapper {

    private UserMapper() {}

    public static User toEntity(RegisterRequest request, String passwordHash){
        return User.builder()
                .email(request.email())
                .passwordHash(passwordHash)
                .build();
    }

    public static UserResponse toResponse(User user){
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getCreatedAt()
        );
    }

}
