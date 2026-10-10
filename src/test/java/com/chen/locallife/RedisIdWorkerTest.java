package com.chen.locallife;

import com.chen.locallife.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
public class RedisIdWorkerTest {
    @Autowired
    private RedisIdWorker redisIdWorker;
    private ExecutorService es= Executors.newFixedThreadPool(500);

    @Test
    public void testDate() throws InterruptedException {
        CountDownLatch latch=new CountDownLatch(300);

        Runnable task=()->{
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("Id : "+id);
            }
            latch.countDown();
        };

        long begin=System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }

        latch.await();

        long end=System.currentTimeMillis();
        System.out.println("time = "+(end-begin));

    }

    @Test
    public void testIdByte64(){
        long order= redisIdWorker.nextId("order");
        System.out.println("order = " +order);

        long decimal=order;
        String binary="";
        while (decimal!=0){
            binary=decimal%2+binary;
            decimal=decimal/2;
        }
        System.out.println("binary = "+binary);
    }
}
