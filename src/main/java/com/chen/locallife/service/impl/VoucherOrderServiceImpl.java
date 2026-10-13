package com.chen.locallife.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen.locallife.dto.Result;
import com.chen.locallife.dto.UserDTO;
import com.chen.locallife.entity.SeckillVoucher;
import com.chen.locallife.entity.VoucherOrder;
import com.chen.locallife.mapper.VoucherOrderMapper;
import com.chen.locallife.service.ISeckillVoucherService;
import com.chen.locallife.service.IVoucherOrderService;
import com.chen.locallife.utils.ILock;
import com.chen.locallife.utils.RedisIdWorker;
import com.chen.locallife.utils.SimpleRedisLock;
import com.chen.locallife.utils.UserHolder;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.aop.framework.AopProxy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 服务实现类
 */
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

    //10.
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

    //阻塞队列10.,11.不要这句
//    private final BlockingQueue<VoucherOrder> orderTasks=new ArrayBlockingQueue<>(1024*1024);
    //异步线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR=Executors.newSingleThreadExecutor();

    //代理对象
    private IVoucherOrderService proxy;

    @PostConstruct//init() 是普通方法，Spring 启动时不会自动执行--需要加 @PostConstruct
    private void init(){
        //消费者组缓存预热
        try{
            stringRedisTemplate.opsForStream().createGroup("stream.orders","g1");
            log.info("消费者组g1创建成功");
        } catch (Exception e) {
            log.info("消费者组已存在：{}",e.getMessage());
        }

        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }


    @Override
    public Result seckillVoucher(Long voucherId) {
        //基础版--判断（开始前？开始后？还有库存？）+扣库存（mysql）+保存（mysql）
        //进阶版--加上一人一单判断（mysql）
        //再进阶，把createOrder单独取出去并方法加synchronized锁(锁的粒度过于粗了-->seckill里锁用户id)
        //版本7，用redis里面的简单锁代替
        //版本8,解决7.的锁误删问题（用lua脚本）
        //版本9，用Redission替代手写锁,解决版本8的问题：手写锁不可重入、不可重试、锁不住
        //版本10：异步秒杀优化（阻塞队列）--功能拆分（判断+一人一单==能不能成；能不能成返回结果；阻塞队列生成订单）
        //版本11：消息队列--10.把队列存在jvm里，一旦重启数据丢失||Stream消息队列

//        // ===== 测试用：手动设置用户 =====
//        UserDTO userDTO = new UserDTO();
//        userDTO.setId(1L);
//        userDTO.setNickName("测试用户");
//        UserHolder.saveUser(userDTO);
//        // ===== 测试用结束 =====

        //10.
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
        Long orderId=redisIdWorker.nextId("order");//11.
        Long result=stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)//11.
                );
        //我比较疑惑的是，还没确定能不能成就创建了orderId，假设现在为3，如果每成下一个是3还是4，会不会乱
        //--随便用，没成功也行
        int r=result.intValue();
        if(r!=0){
            return Result.fail(r==1?"库存不足":"不能重复下单");
        }

        //组装装入阻塞队列10.
//        VoucherOrder voucherOrder=new VoucherOrder();
//        long orderId=redisIdWorker.nextId("order");10.
//        voucherOrder.setId(orderId);
//        voucherOrder.setVoucherId(voucherId);
//        voucherOrder.setUserId(userId);
//        orderTasks.add(voucherOrder);

        proxy=(IVoucherOrderService) AopContext.currentProxy();

        return Result.ok(orderId);


//        SeckillVoucher voucher=iSeckillVoucherService.getById(voucherId);
//        LocalDateTime now=LocalDateTime.now();
//        if(now.isBefore(voucher.getBeginTime())){
//            return Result.fail("秒杀暂未开始！");
//        }
//        if(now.isAfter(voucher.getEndTime())){
//            return Result.fail("秒杀已经结束！");
//        }
//        if(voucher.getStock()<1){
//            return Result.fail("暂无库存！");
//        }1.--9.


//        UserDTO userDTO=UserHolder.getUser();
//        //一人一单
//        Long count=query()
//                .eq("voucher_id",voucherId)
//                .eq("user_id",userDTO.getId())
//                .count();
//        if(count>0){
//            return Result.fail("重复下单！");
//        }
//
//        boolean success= iSeckillVoucherService.update()
//                .eq("voucher_id",voucherId)
//                .setSql("stock=stock-1")
//                .gt("stock",0).update();
//        if(!success){
//            return Result.fail("库存不足！");
//        }
//        VoucherOrder voucherOrder=new VoucherOrder();
//        Long orderId=redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        voucherOrder.setVoucherId(voucherId);
//
//
//        voucherOrder.setUserId(userDTO.getId());
//
//        voucherOrder.setCreateTime(LocalDateTime.now());
//        voucherOrder.setUpdateTime(LocalDateTime.now());
//        save(voucherOrder);

//        return Result.ok(createOrder(voucherId));4.

        //5.
//        Long userId=UserHolder.getUser().getId();
//        synchronized (userId.toString().intern()){
//            return this.createOrder(voucherId);//this会失效（）
//        }

        //6.
//        Long userId=UserHolder.getUser().getId();
//        synchronized (userId.toString().intern()){
//            IVoucherOrderService proxy=(IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createOrder(voucherId);//代理对象 + 事务
//        }

        //7.-->9.
//        Long userId=UserHolder.getUser().getId();
////        ILock lock= new  SimpleRedisLock("order:"+userId,stringRedisTemplate);7.
//        RLock lock=redissonClient.getLock("lock:order"+userId);
////        boolean isLock=lock.tryLock(1200L);//7.
//        boolean isLock=lock.tryLock();
//        if(!isLock){
//            return  Result.fail("不允许重复下单！");
//        }
//
//        try {
//            IVoucherOrderService proxy=(IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createOrder(voucherId);//代理对象 + 事务
//        }finally {
////            lock.unLock();//7.
//            lock.unlock();
//        }
    }

    //10.
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while(true){
                try {
//                    VoucherOrder voucherOrder= orderTasks.take();10.
//                    handleVoucherOrder(voucherOrder);

                    //从 Stream 消费消息(读取+解析得到voucherOrder+处理+ack确认)
                    List<MapRecord<String,Object,Object>> list=stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1","c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create("stream.orders",ReadOffset.lastConsumed()));
                    //streamRead,消费者为g1里面的c1,读取配置=最多一条+无消息时最长等待2秒，读“stream.order"里未读的
                    //为啥要创建空的配置？偏移量是下一个读哪里嘛？无消息时最长等待指的什么？
                    //StreamReadOptions 没有公开的构造方法，只能通过 empty() 创建一个默认配置，再用链式方法修改

                    //无消息继续循环
                    if(list==null||list.isEmpty()){
                        continue;
                    }

                    //解析一条消息
                    MapRecord<String,Object,Object> record=list.get(0);//get0取得是？
                    //因为我们配置了 count(1)，一次只读 1 条，所以列表里最多只有 1 条
                    Map<Object,Object> value=record.getValue();//得到字段键值对
                    //把 Map 里的键值对，按字段名填到 Java 对象里，，true	忽略大小写（字段名匹配时）
                    VoucherOrder voucherOrder=BeanUtil.fillBeanWithMap(value,new VoucherOrder(),true);
                    //我大概理解这句的目的--把value按字段名转成voucherOrder，但这只是我的发现，我需要你讲讲这个写法

                    //订单处理
                    handleVoucherOrder(voucherOrder);

                    //ack确认
                    stringRedisTemplate.opsForStream().acknowledge("stream.orders","g1",record.getId());
                    //怎么是s1？

                }catch (Exception e){
                    log.error("处理订单异常",e);

                    //11.处理pending-list里的异常消息（实现"消息至少被消费一次"//为了保证失败的消息能被重试)
                    handlePendingList();
                }
            }
        }
    }

    //11.handlePendingList
    private void handlePendingList(){
        while (true){
         //基本同上
         try {
             //读取+解析+执行+ack
             List<MapRecord<String,Object,Object>> list=stringRedisTemplate.opsForStream().read(
                     Consumer.from("g1","c1"),
                     StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                     StreamOffset.create("stream.orders",ReadOffset.from("0"))
             );

             if(list==null ||list.isEmpty()){
                 break;  // ← 正确，退出 pending-list 处理，回到正常消费
             }

             MapRecord<String,Object,Object> record=list.get(0);
             Map<Object,Object> value=record.getValue();
             VoucherOrder voucherOrder=BeanUtil.fillBeanWithMap(value,new VoucherOrder(),true);

             handleVoucherOrder(voucherOrder);

             stringRedisTemplate.opsForStream().acknowledge("stream.orders","g1",record.getId());

         }   catch (Exception e){
            log.error("处理pending订单异常",e);
            try {
                Thread.sleep(20);//发生异常睡眠20何用？等 20 毫秒，给系统一点恢复时间/等数据库连接/避免浪费cpu
            }catch (InterruptedException ex){
                ex.printStackTrace();
            }
         }
        }
    }

    //10.
    private void handleVoucherOrder(VoucherOrder voucherOrder){
        if (proxy == null) {//测试.
            // 后台线程比第一个秒杀请求先启动，此时还没有 proxy
            // 简单处理：直接返回，等有请求后再处理
            // 但这样消息会留在 pending 里，会被反复重试
            log.warn("proxy 尚未初始化，跳过本次处理");
            return;
        }

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
            proxy.createVoucherOrder(voucherOrder);
        }finally {
            lock.unlock();
        }

    }


    @Override
    @Transactional
//    private synchronized Result createOrder(Long voucherId){//4.
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
