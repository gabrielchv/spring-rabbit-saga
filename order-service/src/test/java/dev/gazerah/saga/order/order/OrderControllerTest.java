package dev.gazerah.saga.order.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OrderService orders;

    @Test
    void createReturnsAccepted() throws Exception {
        when(orders.create(any(), any())).thenReturn(new Order("c1", new BigDecimal("10")));

        mvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"c1\",\"amount\":10}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void createRejectsMissingAmount() throws Exception {
        mvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"c1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReturnsOrder() throws Exception {
        Order order = new Order("c1", new BigDecimal("10"));
        UUID id = UUID.randomUUID();
        when(orders.find(id)).thenReturn(Optional.of(order));

        mvc.perform(get("/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void getReturnsNotFound() throws Exception {
        when(orders.find(any())).thenReturn(Optional.empty());

        mvc.perform(get("/orders/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
