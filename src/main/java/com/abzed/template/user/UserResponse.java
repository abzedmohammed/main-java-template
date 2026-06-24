package com.abzed.template.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Public view of a {@link User}. Controllers return this instead of the entity
 * so persistence-only fields (e.g. the password hash) are never serialized.
 */
public record UserResponse(
        UUID id,
        String fullName,
        String email,
        String role,
        String provider,
        boolean emailVerified,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name(),
                user.getProvider().name(),
                user.isEmailVerified(),
                user.getCreatedAt()
        );
    }
}
