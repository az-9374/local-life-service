package com.chen.locallife.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen.locallife.config.RabbitMQConfig;
import com.chen.locallife.dto.Result;
import com.chen.locallife.dto.UserDTO;
import com.chen.locallife.entity.VoucherOrder;
import com.chen.locallife.mapper.VoucherOrderMapper;
import com.chen.locallife.service.ISeckillVoucherService;
import com.chen.locallife.service.IVoucherOrderService;
import com.chen.locallife.utils.RedisIdWorker;
import com.chen.locallife.utils.UserHolder;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;

import static com.chen.locallife.utils.RedisConstants.SECKILL_ORDER_KEY;
import static com.chen.locallife.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * 服务实现类
 */
//stream
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ISeckillVoucherService iSeckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    @Lazy
    private IVoucherOrderService self;


    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT=new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    //秒杀限流
    private static final DefaultRedisScript<Long> LIMIT_SCRIPT;
    static {
        LIMIT_SCRIPT=new DefaultRedisScript<>();
        LIMIT_SCRIPT.setLocation(new ClassPathResource("limit.lua"));
        LIMIT_SCRIPT.setResultType(Long.class);
    }


    @Override
    public Result seckillVoucher(Long voucherId) {
        //秒杀下单流程：
        //1.限流：Redis + Lua 按用户维度限频
        //2.Lua 脚本原子完成：库存判断 + 一人一单判断 + 扣减库存 + 记录下单用户
        //3.投递订单消息到 RabbitMQ，投递失败则回滚 Redis 库存（补偿）
        //4.监听器异步消费落库：Redisson 锁防并发重复下单 + 手动 ack 保证消息不丢

        Long userId=UserHolder.getUser().getId();
        //=====秒杀限流=====
        String limitKey = "limit:seckill:"+userId;
        Long limitResult=stringRedisTemplate.execute(
                LIMIT_SCRIPT,
                Collections.singletonList(limitKey),
                "10",
                "1"
        );
        if(limitResult==null || limitResult==0){
            return  Result.fail("请求过于频繁，请稍后再试");
        }
        //=====限流结束=====
        Long orderId=redisIdWorker.nextId("order");
        Long result=stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
                );
        int r=result.intValue();
        if(r!=0){
            return Result.fail(r==1?"库存不足":"不能重复下单");
        }

        //由于改了mq使原秒杀脚本原子性破坏--这里做发mq以及补偿逻辑
        VoucherOrder voucherOrder=new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);

        try {
            //发送消息
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SECKILL_EXCHANGE,
                    RabbitMQConfig.SECKILL_ROUTING_KEY,
                    voucherOrder
            );
        }catch (Exception e){
            log.error("秒杀消息投递失败，触发补偿回滚",e);
            //补偿回滚逻辑--把脚本里面扣掉的库存补上+Order记录删掉
            stringRedisTemplate.opsForValue().increment(SECKILL_STOCK_KEY+voucherId);
            stringRedisTemplate.opsForSet().remove(SECKILL_ORDER_KEY+voucherId,userId.toString());
            return  Result.fail("系统繁忙，请稍后重试");
        }

        return Result.ok(orderId);


    }

    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
    public void receiveSeckillOrder(VoucherOrder voucherOrder, Message message,
                                    Channel channel) throws IOException {
        long deliveryTag =message.getMessageProperties().getDeliveryTag();
        try{
            log.info("收到秒杀订单信息：{}",voucherOrder);
            // 调用原来处理订单的逻辑（走代理，为了事务生效）
            handleVoucherOrder(voucherOrder);
            // 手动 ACK：处理成功，告诉 MQ 删消息
            channel.basicAck(deliveryTag,false);
        } catch (Exception e) {
            log.error("处理秒杀订单失败，消息ID：{}",voucherOrder.getId(),e);
            // 拒绝消息，requeue=false（不再重新入队，防止死循环，真实生产环境应进死信队列）
            // requeue=true(重新入队），解决静默丢消息的问题
            channel.basicNack(deliveryTag,false,true);
        }

    }



    private void handleVoucherOrder(VoucherOrder voucherOrder){
        //并发锁处理订单
//        Long userId=UserHolder.getUser().getId();后台线程里 UserHolder.getUser() 拿不到用户
        Long userId= voucherOrder.getUserId();
        RLock lock=redissonClient.getLock("lock:order:"+userId);
        boolean isLock=lock.tryLock();
        if(!isLock){
            log.error("不允许重复下单!");
            return ;
        }
        try {
            self.createVoucherOrder(voucherOrder);
        }finally {
            lock.unlock();
        }

    }


    @Override
    @Transactional
    public   Result createOrder(Long voucherId){
        //一人一单+扣库存+save

        UserDTO userDTO=UserHolder.getUser();
        Long count=query()
                .eq("voucher_id",voucherId)
                .eq("user_id",userDTO.getId())
                .count();
        if(count>0){
            return Result.fail("重复下单！");
        }

        boolean success= iSeckillVoucherService.update()
                .eq("voucher_id",voucherId)
                .setSql("stock=stock-1")
                .gt("stock",0).update();
        if(!success){
            return Result.fail("库存不足！");
        }
        VoucherOrder voucherOrder=new VoucherOrder();
        Long orderId=redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);


        voucherOrder.setUserId(userDTO.getId());

        voucherOrder.setCreateTime(LocalDateTime.now());
        voucherOrder.setUpdateTime(LocalDateTime.now());
        save(voucherOrder);

        return Result.ok(orderId);
    }

    @Override
    public Result getResult(Long voucherId) {
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //判断一人一单+实际扣库存+实际存入订单
        Long voucherId=voucherOrder.getVoucherId();
        Long userId=voucherOrder.getUserId();
        Long count=query()
                .eq("voucher_id",voucherId)
                .eq("user_id",userId)
                .count();
        if(count>0){
            log.error("不允许重复下单!!");
            return ;
        }

        boolean success= iSeckillVoucherService.update()
                .eq("voucher_id",voucherId)
                .setSql("stock=stock-1")
                .gt("stock",0).update();
        if(!success){
            log.error("库存不足！！");
            return ;
        }

        //这个需要设置
        voucherOrder.setCreateTime(LocalDateTime.now());
        voucherOrder.setUpdateTime(LocalDateTime.now());
        save(voucherOrder);
    }
}
