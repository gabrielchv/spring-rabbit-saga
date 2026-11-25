package dev.gazerah.saga.inventory.inventory;

import dev.gazerah.saga.events.InventoryFailedEvent;
import dev.gazerah.saga.events.InventoryReservedEvent;
import dev.gazerah.saga.events.PaymentProcessedEvent;
import dev.gazerah.saga.inventory.config.RabbitConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class InventoryService {

    static final int DEFAULT_STOCK = 1000;

    private final RabbitTemplate rabbit;
    private final AtomicInteger availableStock;

    /**
     * The outcome already decided for each payment, keyed by {@code paymentId}.
     *
     * A message can be delivered more than once — a channel dies between the
     * work and the ack, the container restarts, a retry republishes — and the
     * consumer has no way to tell the copy from the original. Without a record of
     * what was already decided, a redelivery reserves twice for one order.
     * Keeping the *outcome*, not just a "seen" flag, also means a redelivery
     * re-emits the same answer instead of a contradictory one.
     */
    private final Map<UUID, Decision> decisions = new ConcurrentHashMap<>();

    /** Serialises the check-and-decrement so stock cannot go negative. */
    private final ReentrantLock stockLock = new ReentrantLock();

    private record Decision(boolean reserved, InventoryReservedEvent reservedEvent, InventoryFailedEvent failedEvent) {}

    @Autowired
    public InventoryService(RabbitTemplate rabbit) {
        this(rabbit, DEFAULT_STOCK);
    }

    InventoryService(RabbitTemplate rabbit, int initialStock) {
        this.rabbit = rabbit;
        this.availableStock = new AtomicInteger(initialStock);
    }

    /**
     * Reserve stock for a paid order, exactly once per payment.
     *
     * Two defects lived in the previous three lines:
     *
     * 1. {@code availableStock.getAndDecrement() > 0} decremented on *both*
     *    branches, so the out-of-stock path pushed stock to -1 and every
     *    subsequent failure pushed it further down. Stock drifted negative and
     *    never recovered.
     * 2. Nothing was deduplicated, so a redelivered {@code payment.processed}
     *    reserved a second unit for the same order — and if the first delivery
     *    had emptied the stock, the redelivery reported "out of stock" for an
     *    order that had just been reserved, leaving it both reserved and failed.
     *
     * The outcome is recorded and replayed rather than recomputed, so a retry
     * produces the same event the caller already saw.
     */
    public void reserve(PaymentProcessedEvent event) {
        Decision decision = decisions.computeIfAbsent(event.paymentId(), ignored -> decide(event));
        publish(decision);
    }

    private Decision decide(PaymentProcessedEvent event) {
        stockLock.lock();
        try {
            if (availableStock.get() > 0) {
                availableStock.decrementAndGet();
                return new Decision(
                        true,
                        new InventoryReservedEvent(event.orderId(), event.correlationId(), Instant.now()),
                        null);
            }
            // Out of stock does not touch the counter: there was nothing to take.
            return new Decision(
                    false,
                    null,
                    new InventoryFailedEvent(event.orderId(), "out of stock", event.correlationId(), Instant.now()));
        } finally {
            stockLock.unlock();
        }
    }

    private void publish(Decision decision) {
        if (decision.reserved()) {
            rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_INVENTORY_RESERVED,
                    decision.reservedEvent());
        } else {
            rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_INVENTORY_FAILED,
                    decision.failedEvent());
        }
    }

    /** Remaining stock. Test-only visibility. */
    int availableStock() {
        return availableStock.get();
    }
}
