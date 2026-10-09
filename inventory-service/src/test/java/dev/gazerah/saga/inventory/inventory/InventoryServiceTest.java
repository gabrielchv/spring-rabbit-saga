package dev.gazerah.saga.inventory.inventory;

import dev.gazerah.saga.events.InventoryFailedEvent;
import dev.gazerah.saga.events.InventoryReservedEvent;
import dev.gazerah.saga.events.PaymentProcessedEvent;
import dev.gazerah.saga.inventory.config.RabbitConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InventoryServiceTest {

    @Test
    void reservesWhenStockAvailable() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        InventoryService service = new InventoryService(rabbit, 2);

        service.reserve(new PaymentProcessedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()));

        verify(rabbit).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_INVENTORY_RESERVED), any(InventoryReservedEvent.class));
    }

    @Test
    void failsWhenOutOfStock() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        InventoryService service = new InventoryService(rabbit, 0);

        service.reserve(new PaymentProcessedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()));

        verify(rabbit).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_INVENTORY_FAILED), any(InventoryFailedEvent.class));
    }
}
