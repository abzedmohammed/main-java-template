package com.abzed.template.messaging;

import java.time.Instant;

public record AuthEventMessage(
        String event,
        String email,
        String status,
        String details,
        Instant at
) {
}
