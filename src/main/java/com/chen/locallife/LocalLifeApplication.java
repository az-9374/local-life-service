package com.chen.locallife;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 启动类
 */
@MapperScan("com.chen.locallife.mapper")
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)//秒杀6.
public class LocalLifeApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalLifeApplication.class, args);
    }

}
