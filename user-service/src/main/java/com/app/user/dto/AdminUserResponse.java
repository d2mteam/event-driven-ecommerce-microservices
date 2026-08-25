package com.app.user.dto;

import com.app.user.model.Role;
import com.app.user.model.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String username,
        String email,
        String fullName,
        Role role,
        UserStatus status,
        Instant createdAt
) {
}
