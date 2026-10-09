package dev.gazerah.saga.events;

import java.time.Instant;
import java.util.UUID;

public record PaymentProcessedEvent(
        UUID orderId,
        UUID paymentId,
        UUID correlationId,
        Instant timestamp
) {
}
