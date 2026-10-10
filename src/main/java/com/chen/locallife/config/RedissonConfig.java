package com.chen.locallife.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * redisson配置
 */
@Configuration
public class RedissonConfig {
    //9.
//    @Bean
//    public RedissonClient redissonClient(){
//        //配置
//        Config config=new Config();
//        config.useSingleServer()
//                .setAddress("redis://192.168.100.130:6379")
//                .setPassword("123456")
//                .setDatabase(1);
//        return Redisson.create(config);
//    }


    @Value("${spring.redis.host}")
    private String host;//从 application.yml 读 spring.redis.host 的值，赋给 host
    @Value("${spring.redis.port}")
    private String port;
    @Value("${spring.redis.password}")
    private String password;
    @Bean
    public RedissonClient redissonClient(){
        //配置
        Config config=new Config();
        config.useSingleServer().setAddress("redis://"+host+":"+port).setPassword(password);
        //创建对并且返回
        return Redisson.create(config);
    }

}
