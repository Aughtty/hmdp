package com.hmdp.utils;

public interface Ilock {
    /**
     * * 尝试获取锁
     * @param timeoutSec 超时时间
     * @return true 获取锁成功，false 获取锁失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
