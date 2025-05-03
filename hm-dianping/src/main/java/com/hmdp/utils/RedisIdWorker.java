package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;


@Component
public class RedisIdWorker {

    /*
        * 2022年1月1日0时0分0秒的时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    /*
        * 每一部分占用的位数
        * 0位符号位，31位时间戳，32位序列号
     */
    private static final int COUNT_BITS = 32;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 根据不同的业务，生成id
    public long nextId(String keyPrefix) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long timestamp = now.toEpochSecond(ZoneOffset.UTC) - BEGIN_TIMESTAMP;

        // 2.生成序列号
        // 2.1获取当前日期    这样每天都可以有一个新的序列号重新开始
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2自增序列号
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 3.拼接并返回  //因为是二进制，所以不能使用转类型，只能使用位运算
        return timestamp << COUNT_BITS | count;
    }



}
