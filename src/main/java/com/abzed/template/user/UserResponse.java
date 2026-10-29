package com.abzed.template.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Public view of a {@link User}. Controllers return this instead of the entity
 * so persistence-only fields (e.g. the password hash) are never serialized.
 */
public record UserResponse(
        UUID usrId,
        String usrFullName,
        String usrEmail,
        String usrRole,
        String usrProvider,
        boolean usrEmailVerified,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getUsrId(),
                user.getUsrFullName(),
                user.getUsrEmail(),
                user.getUsrRole().name(),
                user.getUsrProvider().name(),
                user.isUsrEmailVerified(),
                user.getCreatedAt()
        );
    }
}
