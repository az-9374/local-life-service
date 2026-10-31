package com.chen.locallife.config;


import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String SECKILL_EXCHANGE="seckill.exchange";
    public static final String SECKILL_QUEUE="seckill.order.queue";
    public static final String SECKILL_ROUTING_KEY="seckill.order";

    @Bean
    public DirectExchange seckillExchange(){
        return new DirectExchange(SECKILL_EXCHANGE,true,false);
    }

    @Bean
    public Queue seckillQueue(){
        return QueueBuilder.durable(SECKILL_QUEUE).build();
    }

    @Bean
    public Binding seckillBinding(){
        return BindingBuilder.bind(seckillQueue()).to(seckillExchange()).with(SECKILL_ROUTING_KEY);
    }

    //修序列化（防报错）--让消息变成易读的 JSON，防止发消息时因为没实现 Serializable 报错
    @Bean
    public MessageConverter jsonMessageConverter(){
        return new Jackson2JsonMessageConverter();
    }
}
