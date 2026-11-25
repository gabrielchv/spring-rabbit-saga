package dev.gazerah.saga.payment.payment

import dev.gazerah.saga.events.OrderCreatedEvent
import dev.gazerah.saga.events.PaymentFailedEvent
import dev.gazerah.saga.events.PaymentProcessedEvent
import dev.gazerah.saga.payment.config.RabbitConfig
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class PaymentService(private val rabbit: RabbitTemplate) {

    /**
     * The outcome already decided for each order.
     *
     * Charging a card is not idempotent at the ends of the wire: a redelivered
     * `order.created` is indistinguishable from the original, so processing it
     * again charges again. The message broker can redeliver for entirely mundane
     * reasons — the channel died between the send and the ack, the container
     * restarted, a retry republished — and none of them mean the customer wants
     * to pay twice.
     *
     * Two things are recorded, and both matter:
     *
     * - the decision, so a redelivery does not re-decide against a counter that
     *   may have moved;
     * - the `paymentId`, because the previous code generated a fresh
     *   `UUID.randomUUID()` on every invocation. A downstream service that wanted
     *   to deduplicate on payment id had no stable key to use, so duplicate
     *   charges were not even detectable after the fact.
     */
    private val decisions = ConcurrentHashMap<UUID, Outcome>()

    private sealed interface Outcome {
        data class Approved(val event: PaymentProcessedEvent) : Outcome
        data class Declined(val event: PaymentFailedEvent) : Outcome
    }

    fun process(event: OrderCreatedEvent) {
        val outcome = decisions.computeIfAbsent(event.orderId) { decide(event) }
        publish(outcome)
    }

    private fun decide(event: OrderCreatedEvent): Outcome {
        val now = Instant.now()
        return if (event.amount <= APPROVAL_LIMIT) {
            Outcome.Approved(
                PaymentProcessedEvent(event.orderId, UUID.randomUUID(), event.correlationId, now)
            )
        } else {
            Outcome.Declined(
                PaymentFailedEvent(
                    event.orderId, "amount exceeds approval limit", event.correlationId, now
                )
            )
        }
    }

    private fun publish(outcome: Outcome) {
        when (outcome) {
            is Outcome.Approved ->
                rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_PAYMENT_PROCESSED, outcome.event)
            is Outcome.Declined ->
                rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_PAYMENT_FAILED, outcome.event)
        }
    }

    companion object {
        private val APPROVAL_LIMIT: BigDecimal = BigDecimal("10000")
    }
}
