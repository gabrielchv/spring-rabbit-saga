package dev.gazerah.saga.order.order;

import dev.gazerah.saga.events.InventoryFailedEvent;
import dev.gazerah.saga.events.InventoryReservedEvent;
import dev.gazerah.saga.events.OrderCompletedEvent;
import dev.gazerah.saga.events.OrderCreatedEvent;
import dev.gazerah.saga.events.OrderFailedEvent;
import dev.gazerah.saga.events.PaymentFailedEvent;
import dev.gazerah.saga.order.config.RabbitConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@DataJpaTest
@Import(OrderService.class)
class OrderServiceTest {

    @Autowired
    OrderRepository orders;

    @Autowired
    OrderService service;

    @MockitoBean
    RabbitTemplate rabbit;

    @Test
    void createPersistsOrderAndPublishesCreatedEvent() {
        Order order = service.create("c1", new BigDecimal("150.00"));

        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbit).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_ORDER_CREATED), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(OrderCreatedEvent.class);
    }

    @Test
    void inventoryReservedCompletesOrder() {
        Order order = orders.save(new Order("c1", new BigDecimal("150.00")));

        service.onInventoryReserved(new InventoryReservedEvent(order.getId(), order.getId(), Instant.now()));

        Order updated = orders.findById(order.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(updated.getCompletedAt()).isNotNull();
        verify(rabbit).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_ORDER_COMPLETED), any(OrderCompletedEvent.class));
    }

    @Test
    void paymentFailedFailsOrder() {
        Order order = orders.save(new Order("c1", new BigDecimal("150.00")));

        service.onPaymentFailed(new PaymentFailedEvent(order.getId(), "card declined", order.getId(), Instant.now()));

        Order updated = orders.findById(order.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(updated.getFailureReason()).isEqualTo("card declined");
        verify(rabbit).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_ORDER_FAILED), any(OrderFailedEvent.class));
    }

    @Test
    void inventoryFailedFailsOrder() {
        Order order = orders.save(new Order("c1", new BigDecimal("150.00")));

        service.onInventoryFailed(new InventoryFailedEvent(order.getId(), "out of stock", order.getId(), Instant.now()));

        Order updated = orders.findById(order.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(updated.getFailureReason()).isEqualTo("out of stock");
        verify(rabbit).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_ORDER_FAILED), any(OrderFailedEvent.class));
    }

    @Test
    void completedOrderIgnoresLaterEvents() {
        Order order = orders.save(new Order("c1", new BigDecimal("150.00")));
        service.onInventoryReserved(new InventoryReservedEvent(order.getId(), order.getId(), Instant.now()));
        service.onPaymentFailed(new PaymentFailedEvent(order.getId(), "late failure", order.getId(), Instant.now()));

        Order updated = orders.findById(order.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }
}
