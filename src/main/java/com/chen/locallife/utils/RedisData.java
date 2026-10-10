package com.chen.locallife.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Redis数据
 */
//这装的啥数据
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
