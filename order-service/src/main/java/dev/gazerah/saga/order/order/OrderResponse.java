package dev.gazerah.saga.order.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerId,
        BigDecimal amount,
        String status,
        String failureReason,
        Instant createdAt,
        Instant completedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getAmount(),
                order.getStatus().name(),
                order.getFailureReason(),
                order.getCreatedAt(),
                order.getCompletedAt()
        );
    }
}
