package dev.gazerah.saga.inventory.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;

/**
 * The inventory queue dead-letters to a DLQ. It only arrives there if the
 * routing key its rejects are published with matches a binding on the DLX — and
 * the DLX is a topic exchange with no wildcard in the binding key, so that is an
 * exact match.
 *
 * It did not match: the queue dead-lettered with {@code <queue>.dlq} while the
 * DLQ bound the bare queue name, so every exhausted message was dropped by the
 * broker with no trace.
 */
class RabbitConfigDeadLetterTest {

    private final RabbitConfig config = new RabbitConfig();

    @Test
    @DisplayName("the inventory queue's dead-letter routing key matches its DLQ binding")
    void deadLetterRoutingKeyIsRoutable() {
        Queue queue = config.inventoryQueue();
        Binding binding = config.inventoryDlqBinding();

        String key = (String) queue.getArguments().get("x-dead-letter-routing-key");

        assertThat(key).as("the queue must declare a dead-letter routing key").isNotNull();
        assertThat(queue.getArguments().get("x-dead-letter-exchange"))
                .as("the queue must dead-letter to the DLX")
                .isEqualTo(RabbitConfig.DLX);
        assertThat(binding.getExchange()).isEqualTo(RabbitConfig.DLX);
        assertThat(binding.getRoutingKey())
                .as("routing key %s does not match the DLQ binding", key)
                .isEqualTo(key);
        assertThat(binding.getDestination()).isEqualTo(queue.getName() + ".dlq");
    }
}
