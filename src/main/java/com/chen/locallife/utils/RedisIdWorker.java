package com.chen.locallife.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * redis ID生成器
 */
//用来生成全局唯一 ID，
// 1.安全性不想被随便看出规律，2.数据量大需要分库分表不建议id自增，而且要保证唯一性
//返回id:64位：相对时间戳+redis当天生成id计数
//redis 缓存 自增（String） key("icr:前缀:yyyyMMdd")
@Component
public class RedisIdWorker {
    /**
     * 初始时间戳
     */
    private static final Long BEGIN_TIMESTAMP = 1640995200L;
    /**
     * 序列号位数
     */
    private static final Integer COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 获取id
     *
     * @param keyPrefix 业务前缀
     * @return {@link Long}
     */
    public Long nextId(String keyPrefix) {
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);// 当前时间的秒数
        //转成"从1970-01-01 00:00:00 UTC到现在的秒数"
        //ZoneOffset.UTC	时区，UTC（世界标准时间
        long timestamp = nowSecond - BEGIN_TIMESTAMP; // 减去起始时间，得到相对时间戳

        //生成序列号
        //生成当前日期 精确到天
        String today = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        //now.format(...)	把时间对象格式化成字符串，DateTimeFormatter.ofPattern("yyyyMMdd")定义格式
        //自增长
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + today);
        //拼接并返回
        return timestamp << COUNT_BITS|count ;//long 本质就是二进制
    }

}
