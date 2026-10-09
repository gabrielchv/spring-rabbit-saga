package dev.gazerah.saga.order.saga;

import dev.gazerah.saga.events.InventoryFailedEvent;
import dev.gazerah.saga.events.InventoryReservedEvent;
import dev.gazerah.saga.events.PaymentFailedEvent;
import dev.gazerah.saga.order.config.RabbitConfig;
import dev.gazerah.saga.order.order.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderSagaConsumer {

    private final OrderService orders;

    public OrderSagaConsumer(OrderService orders) {
        this.orders = orders;
    }

    @RabbitListener(queues = RabbitConfig.ORDER_PAYMENT_FAILED_QUEUE)
    public void onPaymentFailed(PaymentFailedEvent event) {
        orders.onPaymentFailed(event);
    }

    @RabbitListener(queues = RabbitConfig.ORDER_INVENTORY_RESERVED_QUEUE)
    public void onInventoryReserved(InventoryReservedEvent event) {
        orders.onInventoryReserved(event);
    }

    @RabbitListener(queues = RabbitConfig.ORDER_INVENTORY_FAILED_QUEUE)
    public void onInventoryFailed(InventoryFailedEvent event) {
        orders.onInventoryFailed(event);
    }
}
