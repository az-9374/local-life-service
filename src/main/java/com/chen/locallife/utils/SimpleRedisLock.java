package com.chen.locallife.utils;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
//import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 简单的redis分布式锁
 */
public class SimpleRedisLock implements ILock {
    //7.
    private static final String KEY_PREFIX="lock:";
    private final String name;
    private final StringRedisTemplate stringRedisTemplate;

    //8.
    private static final String ID_PREFIX= UUID.randomUUID().toString(true)+"-";
    //static final：类加载时初始化一次，同一个 JVM 里所有线程共享(不同 JVM 的 ID_PREFIX 不同)
    //和java里面的uuid区别是？
    // (这个可控制是否带横线，true为不带，java里面的会带-->“550e8400-e29b-41d4-a716-446655440000”)
    //lua--unlock脚本
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT=new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(Long timeoutSec) {
        //获取线程标识
        String threadId=ID_PREFIX+Thread.currentThread().getId();
        //加锁，set key value nx ex
        boolean success=stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX+name,threadId,timeoutSec,TimeUnit.SECONDS);
        //key:"lock:"+name(order:userId)
        //value:string(threadId)
        return Boolean.TRUE.equals(success);//因为小概率会返回null
    }

    @Override
    public void unLock() {
//        stringRedisTemplate.delete(KEY_PREFIX+name);//可能误删--不安全7.

        //8.
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX+name),
                ID_PREFIX+Thread.currentThread().getId()
        );
    }
}
