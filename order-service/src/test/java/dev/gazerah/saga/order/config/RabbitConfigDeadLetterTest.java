package dev.gazerah.saga.order.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;

/**
 * The dead-letter path is a promise the README makes: three attempts, then the
 * message lands in a DLQ where someone can look at it.
 *
 * That promise is only kept if the routing key a dead-lettered message is
 * published with actually matches one of the DLX bindings. The exchange is a
 * topic exchange, and the binding keys contain no wildcards, so it is an exact
 * match — an easy thing to get wrong, and it was wrong: the queues dead-lettered
 * with {@code <queue>.dlq} while the DLQs bound the bare queue name, so every
 * exhausted message was dropped by the broker with no trace. Not requeued, not
 * parked, not returned (no mandatory flag) — gone.
 *
 * These tests compare the queue argument against the bindings rather than
 * asserting a hardcoded string, so they fail for any future mismatch, not just
 * this one.
 */
class RabbitConfigDeadLetterTest {

    private static final Pattern TOPIC_WILDCARD = Pattern.compile("[*#]");

    private final RabbitConfig config = new RabbitConfig();

    /** Every queue this service declares with a dead-letter policy. */
    private List<Queue> decayingQueues() {
        return List.of(
                config.orderPaymentFailedQueue(),
                config.orderInventoryReservedQueue(),
                config.orderInventoryFailedQueue());
    }

    /** Every binding declared against the dead-letter exchange. */
    private List<Binding> deadLetterBindings() {
        return List.of(
                config.orderPaymentFailedDlqBinding(),
                config.orderInventoryReservedDlqBinding(),
                config.orderInventoryFailedDlqBinding());
    }

    @Test
    @DisplayName("the dead-letter exchange is the topic exchange the bindings target")
    void deadLetterExchangeIsTheOneUsedByBindings() {
        assertThat(config.deadLetterExchange().getName()).isEqualTo(RabbitConfig.DLX);
        assertThat(deadLetterBindings())
                .allSatisfy(binding -> assertThat(binding.getExchange()).isEqualTo(RabbitConfig.DLX));
    }

    @Test
    @DisplayName("every dead-letter routing key matches a DLX binding")
    void deadLetterRoutingKeysAreRoutable() {
        List<String> bindingKeys = deadLetterBindings().stream().map(Binding::getRoutingKey).toList();

        for (Queue queue : decayingQueues()) {
            Map<String, Object> args = queue.getArguments();
            String key = (String) args.get("x-dead-letter-routing-key");

            assertThat(key)
                    .as("queue %s must declare a dead-letter routing key", queue.getName())
                    .isNotNull();

            // The DLX is a topic exchange. With no wildcard in the binding key,
            // delivery requires an exact match.
            assertThat(TOPIC_WILDCARD.matcher(key).find())
                    .as("routing key %s must not rely on wildcard matching", key)
                    .isFalse();
            assertThat(bindingKeys)
                    .as("no DLQ binding matches routing key %s (bindings: %s)", key, bindingKeys)
                    .contains(key);
        }
    }

    @Test
    @DisplayName("each queue dead-letters to exactly its own DLQ")
    void eachQueueOwnsItsDeadLetterQueue() {
        List<Binding> bindings = deadLetterBindings();

        for (Queue queue : decayingQueues()) {
            String key = (String) queue.getArguments().get("x-dead-letter-routing-key");
            List<Binding> matching =
                    bindings.stream().filter(binding -> binding.getRoutingKey().equals(key)).toList();

            assertThat(matching)
                    .as("exactly one DLQ should receive %s", queue.getName())
                    .hasSize(1);
            assertThat(matching.get(0).getDestination())
                    .as("%s should dead-letter to %s.dlq", queue.getName(), queue.getName())
                    .isEqualTo(queue.getName() + ".dlq");
        }
    }
}
