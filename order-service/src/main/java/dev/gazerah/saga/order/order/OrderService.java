package dev.gazerah.saga.order.order;

import dev.gazerah.saga.events.InventoryFailedEvent;
import dev.gazerah.saga.events.InventoryReservedEvent;
import dev.gazerah.saga.events.OrderCompletedEvent;
import dev.gazerah.saga.events.OrderCreatedEvent;
import dev.gazerah.saga.events.OrderFailedEvent;
import dev.gazerah.saga.events.PaymentFailedEvent;
import dev.gazerah.saga.order.config.RabbitConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final RabbitTemplate rabbit;

    public OrderService(OrderRepository orders, RabbitTemplate rabbit) {
        this.orders = orders;
        this.rabbit = rabbit;
    }

    @Transactional
    public Order create(String customerId, BigDecimal amount) {
        Order order = orders.save(new Order(customerId, amount));
        rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_ORDER_CREATED,
                new OrderCreatedEvent(order.getId(), customerId, amount, order.getId(), Instant.now()));
        return order;
    }

    @Transactional(readOnly = true)
    public Optional<Order> find(UUID id) {
        return orders.findById(id);
    }

    @Transactional
    public void onPaymentFailed(PaymentFailedEvent event) {
        fail(event.orderId(), event.reason(), event.correlationId());
    }

    @Transactional
    public void onInventoryReserved(InventoryReservedEvent event) {
        complete(event.orderId(), event.correlationId());
    }

    @Transactional
    public void onInventoryFailed(InventoryFailedEvent event) {
        fail(event.orderId(), event.reason(), event.correlationId());
    }

    private void complete(UUID orderId, UUID correlationId) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Unknown order: " + orderId));
        if (order.getStatus() == OrderStatus.CREATED) {
            order.markCompleted();
            orders.save(order);
            rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_ORDER_COMPLETED,
                    new OrderCompletedEvent(orderId, correlationId, Instant.now()));
        }
    }

    private void fail(UUID orderId, String reason, UUID correlationId) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Unknown order: " + orderId));
        if (order.getStatus() == OrderStatus.CREATED) {
            order.markFailed(reason);
            orders.save(order);
            rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_ORDER_FAILED,
                    new OrderFailedEvent(orderId, reason, correlationId, Instant.now()));
        }
    }
}
