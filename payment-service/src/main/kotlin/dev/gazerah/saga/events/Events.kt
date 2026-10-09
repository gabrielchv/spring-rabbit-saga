package dev.gazerah.saga.events

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class OrderCreatedEvent(
    val orderId: UUID,
    val customerId: String,
    val amount: BigDecimal,
    val correlationId: UUID,
    val timestamp: Instant
)

data class PaymentProcessedEvent(
    val orderId: UUID,
    val paymentId: UUID,
    val correlationId: UUID,
    val timestamp: Instant
)

data class PaymentFailedEvent(
    val orderId: UUID,
    val reason: String,
    val correlationId: UUID,
    val timestamp: Instant
)
