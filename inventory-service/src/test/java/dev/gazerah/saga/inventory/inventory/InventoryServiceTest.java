package dev.gazerah.saga.inventory.inventory;

import dev.gazerah.saga.events.InventoryFailedEvent;
import dev.gazerah.saga.events.InventoryReservedEvent;
import dev.gazerah.saga.events.PaymentProcessedEvent;
import dev.gazerah.saga.inventory.config.RabbitConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class InventoryServiceTest {

    private static PaymentProcessedEvent paid(UUID paymentId) {
        return new PaymentProcessedEvent(UUID.randomUUID(), paymentId, UUID.randomUUID(), Instant.now());
    }

    @Test
    void reservesWhenStockAvailable() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        InventoryService service = new InventoryService(rabbit, 2);

        service.reserve(paid(UUID.randomUUID()));

        verify(rabbit).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_INVENTORY_RESERVED), any(InventoryReservedEvent.class));
    }

    @Test
    void failsWhenOutOfStock() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        InventoryService service = new InventoryService(rabbit, 0);

        service.reserve(paid(UUID.randomUUID()));

        verify(rabbit).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_INVENTORY_FAILED), any(InventoryFailedEvent.class));
    }

    @Test
    @DisplayName("a failed reservation does not consume stock")
    void outOfStockDoesNotDriveStockNegative() {
        // `getAndDecrement()` ran on both branches, so a failed reservation took a
        // unit that was not there: stock went to -1, then -2, and never recovered.
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        InventoryService service = new InventoryService(rabbit, 0);

        service.reserve(paid(UUID.randomUUID()));
        service.reserve(paid(UUID.randomUUID()));
        service.reserve(paid(UUID.randomUUID()));

        org.assertj.core.api.Assertions.assertThat(service.availableStock()).isZero();
    }

    @Test
    @DisplayName("a redelivered payment reserves only once")
    void redeliveryIsIdempotent() {
        // The consumer cannot tell a redelivery from the original, so the service
        // has to. Without this, one order reserved two units.
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        InventoryService service = new InventoryService(rabbit, 10);
        UUID paymentId = UUID.randomUUID();

        service.reserve(paid(paymentId));
        service.reserve(paid(paymentId));

        org.assertj.core.api.Assertions.assertThat(service.availableStock()).isEqualTo(9);
        verify(rabbit, times(2))
                .convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_INVENTORY_RESERVED), any(InventoryReservedEvent.class));
    }

    @Test
    @DisplayName("a redelivery after the stock ran out still reports the first outcome")
    void redeliveryCannotFlipReservedIntoFailed() {
        // The nastiest version of the bug: take the last unit, die before the ack,
        // get redelivered. The retry found stock 0 and answered "out of stock" for
        // an order that had already reserved. The order was both reserved and
        // failed, and stock was -1.
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        InventoryService service = new InventoryService(rabbit, 1);
        UUID paymentId = UUID.randomUUID();

        service.reserve(paid(paymentId)); // takes the last unit
        service.reserve(paid(paymentId)); // redelivery

        org.assertj.core.api.Assertions.assertThat(service.availableStock()).isZero();
        verify(rabbit, times(2))
                .convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_INVENTORY_RESERVED), any(InventoryReservedEvent.class));
        verify(rabbit, times(0))
                .convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_INVENTORY_FAILED), any(InventoryFailedEvent.class));
    }

    @Test
    @DisplayName("distinct payments each reserve")
    void distinctPaymentsAreIndependent() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        InventoryService service = new InventoryService(rabbit, 10);

        service.reserve(paid(UUID.randomUUID()));
        service.reserve(paid(UUID.randomUUID()));

        org.assertj.core.api.Assertions.assertThat(service.availableStock()).isEqualTo(8);
    }
}
