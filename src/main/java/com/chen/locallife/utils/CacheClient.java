package com.chen.locallife.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.chen.locallife.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.chen.locallife.utils.RedisConstants.*;

/**
 * redis工具
 */
@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;//为啥是final

    @Autowired
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    //？这上面嘛意思---初始化，我知道是给这里的spt赋值使用时创建的spt但是加这个注解是何意味？哦是其他地方使用时直接不用初始化二调用这里的方法了？
    /**
     * 将任意对象序列化成json存入redis
     *
     * @param key   关键
     * @param value 价值
     * @param time  时间
     * @param unit  单位
     */
    //redis的set(),相当于简化，还帮你直接把Str转成JSON了
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 将任意对象序列化成json存入redis 并且携带逻辑过期时间
     *
     * @param key   关键
     * @param value 价值
     * @param time  时间
     * @param unit  单位
     */
    //---封装RedisData并set()
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));//截止过期时间
        //这是把Time按unit转成秒之后是增加到现在的时间
        //存入redis--无截止时间
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 设置空值解决缓存穿透
     *
     * @param keyPrefix  关键前缀
     * @param id         id
     * @param type       类型
     * @param dbFallback db回退
     * @param time       时间
     * @param unit       单位
     * @return {@link R}
     */
    //根据id查询r，mysql方面的实现在dbFallback里面,(这里是使用空值解决缓存穿透的问题)
    public <R, ID> R queryWithPassThrough(//<R,ID>这啥写法我只知道<T>，这返回的是个啥
            String keyPrefix
            , ID id
            , Class<R> type//返回R的类型
            , Function<ID, R> dbFallback//传入一个函数（dbFallback），传入id返回r
            , Long time//?
            , TimeUnit unit) {
        String key = keyPrefix + id;
        //从redis中查询
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StringUtils.isNotEmpty(json)) {//不为null不为空判断
            //存在直接返回
            return JSONUtil.toBean(json, type);
        }
        //判断空值
        if ("".equals(json)) {//缓存穿透问题,为啥不是json.equals("")?-->防止json为null时报错，防御性写法
            return null;
        }
        //不存在 查询数据库
        R r = dbFallback.apply(id);//调用传入的函数的方法
        if (r == null) {
            //redis写入空值
            this.set(key, "", CACHE_NULL_TTL, TimeUnit.SECONDS);
            //数据库不存在 返回错误
            return null;
        }
        //数据库存在 写入redis
        this.set(key, r, time, unit);
        //返回
        return r;
    }

    /**
     * 逻辑过期解决缓存击穿
     *
     * @param id id
     * @return {@link Shop}
     */
    //根据id查询r，功能同上(使用逻辑过期解决缓存击穿的问题)
    public <R, ID> R queryWithLogicalExpire(String keyPrefix
            , ID id
            , Class<R> type
            , Function<ID, R> dbFallback
            , Long time
            , TimeUnit unit) {
        String key = keyPrefix + id;
        //从redis中查询
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StringUtils.isEmpty(json)) {
            //不存在返回空
            return null;
        }
        //命中 反序列化
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject jsonObject = (JSONObject) redisData.getData();
        R r = BeanUtil.toBean(jsonObject, type);//为啥是JSONObject类,java里不是没有这个吗，
        //JSONObject，JSONUtil、BeanUtil 都来自 Hutool
        LocalDateTime expireTime = redisData.getExpireTime();//取出过期时间
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期 直接返回
            return r;
        }
        //已过期
        //获取互斥锁--一个人读一个人写？--锁了这个key:lockKey
        String lockKey = LOCK_SHOP_KEY + id;
        boolean flag = tryLock(lockKey);//这个用的是下面写的redis的并发锁方法
        //是否获取锁成功
        if (flag) {
            //成功 异步重建（用线程池是为了解决多个key同时需要线程更新缓存的情况，节省开销）
            CACHE_REBUILD_EXECUTOR.submit(() -> {//为了不让用户等待直接开一个异步线程来更新缓存
                try {
                    //查询数据库
                    R newR = dbFallback.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(key,newR,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        //返回过期商铺信息
        return r;
    }//没太看懂这个函数是拿来干嘛的

    /**
     * 简易线程池
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    /**
     * 获取锁
     *
     * @param key 关键
     * @return boolean
     */
    //redis里面的获取锁？为啥是获取不是使用
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);//为了flag为null时返回false
    }

    /**
     * 释放锁
     *
     * @param key 关键
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
