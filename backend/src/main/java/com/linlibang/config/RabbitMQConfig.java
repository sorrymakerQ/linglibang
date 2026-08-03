package com.linlibang.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 配置类
 * 声明队列、交换机和绑定关系
 */
@Configuration
public class RabbitMQConfig {

    // ==================== 订单相关 ====================

    /** 订单超时队列（延迟队列） */
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";

    /** 订单死信队列（实际处理队列） */
    public static final String ORDER_DEAD_QUEUE = "order.dead.queue";

    /** 订单交换机 */
    public static final String ORDER_EXCHANGE = "order.exchange";

    /** 订单延迟路由键 */
    public static final String ORDER_DELAY_KEY = "order.delay";

    /** 订单死信路由键 */
    public static final String ORDER_DEAD_KEY = "order.dead";

    // ==================== 通知相关 ====================

    /** 通知队列 */
    public static final String NOTIFICATION_QUEUE = "notification.queue";

    /** 通知交换机 */
    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";

    /** 通知路由键 */
    public static final String NOTIFICATION_KEY = "notification.send";

    /**
     * 订单延迟队列（带死信配置，30分钟过期后转入死信队列）
     */
    @Bean
    public Queue orderDelayQueue() {
        Map<String, Object> args = new HashMap<>();
        // 消息过期时间：30分钟
        args.put("x-message-ttl", 30 * 60 * 1000);
        // 死信交换机
        args.put("x-dead-letter-exchange", ORDER_EXCHANGE);
        // 死信路由键
        args.put("x-dead-letter-routing-key", ORDER_DEAD_KEY);
        return QueueBuilder.durable(ORDER_DELAY_QUEUE).withArguments(args).build();
    }

    /**
     * 订单死信队列（实际消费的队列）
     */
    @Bean
    public Queue orderDeadQueue() {
        return QueueBuilder.durable(ORDER_DEAD_QUEUE).build();
    }

    /**
     * 通知队列
     */
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE).build();
    }

    /**
     * 订单交换机（Topic类型）
     */
    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE);
    }

    /**
     * 通知交换机（Direct类型）
     */
    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(NOTIFICATION_EXCHANGE);
    }

    /**
     * 绑定：订单延迟队列 -> 订单交换机
     */
    @Bean
    public Binding orderDelayBinding() {
        return BindingBuilder.bind(orderDelayQueue()).to(orderExchange()).with(ORDER_DELAY_KEY);
    }

    /**
     * 绑定：订单死信队列 -> 订单交换机
     */
    @Bean
    public Binding orderDeadBinding() {
        return BindingBuilder.bind(orderDeadQueue()).to(orderExchange()).with(ORDER_DEAD_KEY);
    }

    /**
     * 绑定：通知队列 -> 通知交换机
     */
    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue()).to(notificationExchange()).with(NOTIFICATION_KEY);
    }
}
