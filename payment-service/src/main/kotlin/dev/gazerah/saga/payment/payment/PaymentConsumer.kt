package dev.gazerah.saga.payment.payment

import dev.gazerah.saga.events.OrderCreatedEvent
import dev.gazerah.saga.payment.config.RabbitConfig
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class PaymentConsumer(private val service: PaymentService) {

    @RabbitListener(queues = [RabbitConfig.PAYMENT_QUEUE])
    fun onOrderCreated(event: OrderCreatedEvent) {
        service.process(event)
    }
}
