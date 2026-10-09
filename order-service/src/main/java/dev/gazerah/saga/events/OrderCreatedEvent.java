package dev.gazerah.saga.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID orderId,
        String customerId,
        BigDecimal amount,
        UUID correlationId,
        Instant timestamp
) {
}
