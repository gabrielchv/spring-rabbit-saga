package dev.gazerah.saga.events;

import java.time.Instant;
import java.util.UUID;

public record InventoryReservedEvent(
        UUID orderId,
        UUID correlationId,
        Instant timestamp
) {
}
