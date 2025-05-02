package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

@Data
// 用于逻辑过期时间 （通过组合方式，相较于继承，便于扩展）
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
