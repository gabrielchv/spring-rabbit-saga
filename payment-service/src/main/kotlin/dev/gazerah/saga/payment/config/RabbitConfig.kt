package dev.gazerah.saga.payment.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.ExchangeBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.retry.interceptor.RetryOperationsInterceptor

@Configuration
class RabbitConfig {

    @Bean
    fun sagaExchange(): TopicExchange =
        ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build()

    @Bean
    fun deadLetterExchange(): TopicExchange =
        ExchangeBuilder.topicExchange(DLX).durable(true).build()

    @Bean
    fun paymentQueue(): Queue = queueWithDlq(PAYMENT_QUEUE)

    @Bean
    fun paymentBinding(): Binding =
        BindingBuilder.bind(paymentQueue()).to(sagaExchange()).with(RK_ORDER_CREATED)

    @Bean
    fun paymentDlq(): Queue = QueueBuilder.durable("$PAYMENT_QUEUE.dlq").build()

    @Bean
    fun paymentDlqBinding(): Binding =
        BindingBuilder.bind(paymentDlq()).to(deadLetterExchange()).with(PAYMENT_QUEUE)

    @Bean
    fun jsonMessageConverter(objectMapper: ObjectMapper): MessageConverter =
        Jackson2JsonMessageConverter(objectMapper)

    @Bean
    fun rabbitTemplate(connectionFactory: ConnectionFactory, converter: MessageConverter): RabbitTemplate {
        val template = RabbitTemplate(connectionFactory)
        template.messageConverter = converter
        return template
    }

    @Bean
    fun rabbitListenerContainerFactory(
        connectionFactory: ConnectionFactory,
        converter: MessageConverter
    ): SimpleRabbitListenerContainerFactory {
        val factory = SimpleRabbitListenerContainerFactory()
        factory.setConnectionFactory(connectionFactory)
        factory.setMessageConverter(converter)
        factory.setDefaultRequeueRejected(false)
        factory.setAdviceChain(retryInterceptor())
        return factory
    }

    private fun retryInterceptor(): RetryOperationsInterceptor =
        RetryInterceptorBuilder.stateless()
            .maxAttempts(3)
            .backOffOptions(1000, 2.0, 10000)
            .recoverer(RejectAndDontRequeueRecoverer())
            .build()

    private fun queueWithDlq(name: String): Queue =
        QueueBuilder.durable(name)
            .withArgument("x-dead-letter-exchange", DLX)
            .withArgument("x-dead-letter-routing-key", "$name.dlq")
            .build()

    companion object {
        const val EXCHANGE = "saga.exchange"
        const val DLX = "saga.dlx"
        const val RK_ORDER_CREATED = "order.created"
        const val RK_PAYMENT_PROCESSED = "payment.processed"
        const val RK_PAYMENT_FAILED = "payment.failed"
        const val PAYMENT_QUEUE = "payment.queue"
    }
}
