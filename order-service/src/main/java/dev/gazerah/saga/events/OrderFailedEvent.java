package dev.gazerah.saga.events;

import java.time.Instant;
import java.util.UUID;

public record OrderFailedEvent(
        UUID orderId,
        String reason,
        UUID correlationId,
        Instant timestamp
) {
}
