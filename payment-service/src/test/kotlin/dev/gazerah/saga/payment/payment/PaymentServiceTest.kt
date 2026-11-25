package dev.gazerah.saga.payment.payment

import dev.gazerah.saga.events.OrderCreatedEvent
import dev.gazerah.saga.events.PaymentFailedEvent
import dev.gazerah.saga.events.PaymentProcessedEvent
import dev.gazerah.saga.payment.config.RabbitConfig
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.assertj.core.api.Assertions.assertThat
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

    @Test
    fun `a redelivery reuses the original payment id`() {
        // The previous code minted a fresh random paymentId per call, so a
        // duplicate charge left no stable key for anyone downstream to detect it.
        val orderId = UUID.randomUUID()
        val event = OrderCreatedEvent(orderId, "c1", BigDecimal("100"), UUID.randomUUID(), Instant.now())

        service.process(event)
        service.process(event)

        val captor = ArgumentCaptor.forClass(PaymentProcessedEvent::class.java)
        verify(rabbit, times(2)).convertAndSend(
            eq(RabbitConfig.EXCHANGE),
            eq(RabbitConfig.RK_PAYMENT_PROCESSED),
            captor.capture()
        )

        val ids = captor.allValues.map { it.paymentId }.distinct()
        assertThat(ids).`as`("a redelivery must not invent a second payment id").hasSize(1)
    }

    @Test
    fun `distinct orders each get their own decision`() {
        val first = OrderCreatedEvent(UUID.randomUUID(), "c1", BigDecimal("100"), UUID.randomUUID(), Instant.now())
        val second = OrderCreatedEvent(UUID.randomUUID(), "c2", BigDecimal("20000"), UUID.randomUUID(), Instant.now())

        service.process(first)
        service.process(second)

        verify(rabbit).convertAndSend(
            eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_PAYMENT_PROCESSED),
            any(PaymentProcessedEvent::class.java)
        )
        verify(rabbit).convertAndSend(
            eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_PAYMENT_FAILED),
            any(PaymentFailedEvent::class.java)
        )
    }

    @Test
    fun `a redelivery of a declined order stays declined`() {
        val orderId = UUID.randomUUID()
        val event = OrderCreatedEvent(orderId, "c1", BigDecimal("20000"), UUID.randomUUID(), Instant.now())

        service.process(event)
        service.process(event)

        verify(rabbit, times(2)).convertAndSend(
            eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_PAYMENT_FAILED),
            any(PaymentFailedEvent::class.java)
        )
        verify(rabbit, times(0)).convertAndSend(
            eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_PAYMENT_PROCESSED),
            any(PaymentProcessedEvent::class.java)
        )
    }
}
