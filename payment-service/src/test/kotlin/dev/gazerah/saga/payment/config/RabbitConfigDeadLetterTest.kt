package dev.gazerah.saga.payment.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The payment queue dead-letters to a DLQ, and that only happens if the routing
 * key its rejects are published with matches a binding on the DLX. The DLX is a
 * topic exchange whose binding key has no wildcard, so the match is exact.
 *
 * It was not: the queue dead-lettered with `<queue>.dlq` while the DLQ bound the
 * bare queue name, so every exhausted payment message was dropped silently —
 * after the customer's card had already been charged.
 */
class RabbitConfigDeadLetterTest {

    private val config = RabbitConfig()

    @Test
    @DisplayName("the payment queue's dead-letter routing key matches its DLQ binding")
    fun deadLetterRoutingKeyIsRoutable() {
        val queue = config.paymentQueue()
        val binding = config.paymentDlqBinding()

        val key = queue.arguments["x-dead-letter-routing-key"] as String?

        assertThat(key).`as`("the queue must declare a dead-letter routing key").isNotNull()
        assertThat(queue.arguments["x-dead-letter-exchange"])
            .`as`("the queue must dead-letter to the DLX")
            .isEqualTo(RabbitConfig.DLX)
        assertThat(binding.exchange).isEqualTo(RabbitConfig.DLX)
        assertThat(binding.routingKey)
            .`as`("routing key %s does not match the DLQ binding", key)
            .isEqualTo(key)
        assertThat(binding.destination).isEqualTo("${queue.name}.dlq")
    }
}
