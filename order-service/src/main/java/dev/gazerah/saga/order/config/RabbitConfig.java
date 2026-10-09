package dev.gazerah.saga.order.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "saga.exchange";
    public static final String DLX = "saga.dlx";

    public static final String RK_ORDER_CREATED = "order.created";
    public static final String RK_PAYMENT_PROCESSED = "payment.processed";
    public static final String RK_PAYMENT_FAILED = "payment.failed";
    public static final String RK_INVENTORY_RESERVED = "inventory.reserved";
    public static final String RK_INVENTORY_FAILED = "inventory.failed";
    public static final String RK_ORDER_COMPLETED = "order.completed";
    public static final String RK_ORDER_FAILED = "order.failed";

    public static final String ORDER_PAYMENT_FAILED_QUEUE = "order.payment-failed.queue";
    public static final String ORDER_INVENTORY_RESERVED_QUEUE = "order.inventory-reserved.queue";
    public static final String ORDER_INVENTORY_FAILED_QUEUE = "order.inventory-failed.queue";

    @Bean
    public TopicExchange sagaExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return ExchangeBuilder.topicExchange(DLX).durable(true).build();
    }

    @Bean
    public Queue orderPaymentFailedQueue() {
        return queueWithDlq(ORDER_PAYMENT_FAILED_QUEUE);
    }

    @Bean
    public Queue orderInventoryReservedQueue() {
        return queueWithDlq(ORDER_INVENTORY_RESERVED_QUEUE);
    }

    @Bean
    public Queue orderInventoryFailedQueue() {
        return queueWithDlq(ORDER_INVENTORY_FAILED_QUEUE);
    }

    @Bean
    public Binding orderPaymentFailedBinding() {
        return BindingBuilder.bind(orderPaymentFailedQueue()).to(sagaExchange()).with(RK_PAYMENT_FAILED);
    }

    @Bean
    public Binding orderInventoryReservedBinding() {
        return BindingBuilder.bind(orderInventoryReservedQueue()).to(sagaExchange()).with(RK_INVENTORY_RESERVED);
    }

    @Bean
    public Binding orderInventoryFailedBinding() {
        return BindingBuilder.bind(orderInventoryFailedQueue()).to(sagaExchange()).with(RK_INVENTORY_FAILED);
    }

    @Bean
    public Queue orderPaymentFailedDlq() {
        return QueueBuilder.durable(ORDER_PAYMENT_FAILED_QUEUE + ".dlq").build();
    }

    @Bean
    public Binding orderPaymentFailedDlqBinding() {
        return BindingBuilder.bind(orderPaymentFailedDlq()).to(deadLetterExchange()).with(ORDER_PAYMENT_FAILED_QUEUE);
    }

    @Bean
    public Queue orderInventoryReservedDlq() {
        return QueueBuilder.durable(ORDER_INVENTORY_RESERVED_QUEUE + ".dlq").build();
    }

    @Bean
    public Binding orderInventoryReservedDlqBinding() {
        return BindingBuilder.bind(orderInventoryReservedDlq()).to(deadLetterExchange()).with(ORDER_INVENTORY_RESERVED_QUEUE);
    }

    @Bean
    public Queue orderInventoryFailedDlq() {
        return QueueBuilder.durable(ORDER_INVENTORY_FAILED_QUEUE + ".dlq").build();
    }

    @Bean
    public Binding orderInventoryFailedDlqBinding() {
        return BindingBuilder.bind(orderInventoryFailedDlq()).to(deadLetterExchange()).with(ORDER_INVENTORY_FAILED_QUEUE);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(retryInterceptor());
        return factory;
    }

    private RetryOperationsInterceptor retryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000, 2.0, 10000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    private static Queue queueWithDlq(String name) {
        return QueueBuilder.durable(name)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", name + ".dlq")
                .build();
    }
}
