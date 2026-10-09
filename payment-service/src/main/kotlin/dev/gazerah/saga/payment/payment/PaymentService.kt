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

@Service
class PaymentService(private val rabbit: RabbitTemplate) {

    fun process(event: OrderCreatedEvent) {
        val now = Instant.now()
        if (event.amount <= APPROVAL_LIMIT) {
            rabbit.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.RK_PAYMENT_PROCESSED,
                PaymentProcessedEvent(event.orderId, UUID.randomUUID(), event.correlationId, now)
            )
        } else {
            rabbit.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.RK_PAYMENT_FAILED,
                PaymentFailedEvent(event.orderId, "amount exceeds approval limit", event.correlationId, now)
            )
        }
    }

    companion object {
        private val APPROVAL_LIMIT: BigDecimal = BigDecimal("10000")
    }
}
