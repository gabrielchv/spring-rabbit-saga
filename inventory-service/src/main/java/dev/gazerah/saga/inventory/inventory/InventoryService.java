package dev.gazerah.saga.inventory.inventory;

import dev.gazerah.saga.events.InventoryFailedEvent;
import dev.gazerah.saga.events.InventoryReservedEvent;
import dev.gazerah.saga.events.PaymentProcessedEvent;
import dev.gazerah.saga.inventory.config.RabbitConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class InventoryService {

    static final int DEFAULT_STOCK = 1000;

    private final RabbitTemplate rabbit;
    private final AtomicInteger availableStock;

    @Autowired
    public InventoryService(RabbitTemplate rabbit) {
        this(rabbit, DEFAULT_STOCK);
    }

    InventoryService(RabbitTemplate rabbit, int initialStock) {
        this.rabbit = rabbit;
        this.availableStock = new AtomicInteger(initialStock);
    }

    public void reserve(PaymentProcessedEvent event) {
        if (availableStock.getAndDecrement() > 0) {
            rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_INVENTORY_RESERVED,
                    new InventoryReservedEvent(event.orderId(), event.correlationId(), Instant.now()));
        } else {
            rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_INVENTORY_FAILED,
                    new InventoryFailedEvent(event.orderId(), "out of stock", event.correlationId(), Instant.now()));
        }
    }
}
