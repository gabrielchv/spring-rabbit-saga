package dev.gazerah.saga.payment.payment

import dev.gazerah.saga.events.OrderCreatedEvent
import dev.gazerah.saga.events.PaymentFailedEvent
import dev.gazerah.saga.events.PaymentProcessedEvent
import dev.gazerah.saga.payment.config.RabbitConfig
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class PaymentServiceTest {

    private val rabbit: RabbitTemplate = mock(RabbitTemplate::class.java)
    private val service = PaymentService(rabbit)

    @Test
    fun approvesOrderWithinLimit() {
        val event = OrderCreatedEvent(UUID.randomUUID(), "c1", BigDecimal("100"), UUID.randomUUID(), Instant.now())

        service.process(event)

        verify(rabbit).convertAndSend(
            eq(RabbitConfig.EXCHANGE),
            eq(RabbitConfig.RK_PAYMENT_PROCESSED),
            any(PaymentProcessedEvent::class.java)
        )
    }

    @Test
    fun declinesOrderAboveLimit() {
        val event = OrderCreatedEvent(UUID.randomUUID(), "c1", BigDecimal("20000"), UUID.randomUUID(), Instant.now())

        service.process(event)

        verify(rabbit).convertAndSend(
            eq(RabbitConfig.EXCHANGE),
            eq(RabbitConfig.RK_PAYMENT_FAILED),
            any(PaymentFailedEvent::class.java)
        )
    }
}
