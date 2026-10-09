package dev.gazerah.saga.inventory.inventory;

import dev.gazerah.saga.events.PaymentProcessedEvent;
import dev.gazerah.saga.inventory.config.RabbitConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryConsumer {

    private final InventoryService inventory;

    public InventoryConsumer(InventoryService inventory) {
        this.inventory = inventory;
    }

    @RabbitListener(queues = RabbitConfig.INVENTORY_QUEUE)
    public void onPaymentProcessed(PaymentProcessedEvent event) {
        inventory.reserve(event);
    }
}
