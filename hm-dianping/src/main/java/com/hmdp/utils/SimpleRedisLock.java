package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.sql.Time;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements Ilock{

    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX ="lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    @Override
    // 执行 setn x操作
    public boolean tryLock(long timeoutSec) {
        // 获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        // 防止过度拆箱装箱
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        // 1.获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 2.获取锁标识
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        // 3.判断是否是同一个线程         // 防止删除其他线程的锁
        if (threadId.equals(id)) {
            // 4.删除锁
            stringRedisTemplate.delete(KEY_PREFIX + name);
            return;
        }
    }
}
