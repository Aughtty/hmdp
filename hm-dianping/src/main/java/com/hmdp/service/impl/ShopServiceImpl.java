package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        //Shop shop = queryWithPassThrough(id);

        // 缓存击穿，利用互斥锁解决
        //Shop shop = queryWithMutex(id);

        // 缓存击穿，利用逻辑过期解决
        Shop shop = queryWithLogicalExpire(id);


        if (shop == null) {
            // 说明商铺不存在，返回错误信息
            return Result.fail("商铺不存在");
        }

        //返回对应的json数据
        return Result.ok(shop);
    }

    //解决缓存穿透的方法
    public Shop queryWithPassThrough(Long id) {
        //1.根据id判断 redis 中是否存在商铺
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断缓存是否命中
        if (StrUtil.isNotBlank(shopJson)) {
            //3.如果命中，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if(shopJson != null) {
            // 说明缓存中有，但是数据库中不存在
            return null;
        }
        //4.如果没有命中，查询数据库
        Shop shop = getById(id);
        //5.判断数据库是否存在
        if (shop == null) {
            //6.如果不存在，返回错误信息
            //防止缓存穿透，将空值存入redis     //注：字符串为空并不等于null
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //7.如果存在，将数据存储到redis中
        //将对象转换为json字符串
        String json = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(key, json,CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return shop;
    }

    //解决缓存击穿的方法，利用互斥锁解决
    public Shop queryWithMutex(Long id) {
        //1.根据id判断 redis 中是否存在商铺
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断缓存是否命中
        if (StrUtil.isNotBlank(shopJson)) {
            //3.如果命中，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if(shopJson != null) {
            // 说明缓存中有，但是数据库中不存在
            return null;
        }
        //4.如果没有命中, 需要实现缓存重建
        // 4.1 获取锁
        String lockKey = LOCK_SHOP_KEY + id;
        // 4.2 判断是否获取锁成功
        boolean isLock = tryLock(lockKey);
        if (!isLock) {
            // 4.3 如果没有获取到锁，休眠一段时间后重试
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //休眠完继续查询，不断循环
            return queryWithMutex(id);
        }
        Shop shop = null;
        try {
            // 4.4 如果获取到锁，继续查询数据库
            // 4.5 查询数据库
            shop = getById(id);
            // 模拟重建的延时
            Thread.sleep(200);
            // 4.6 判断数据库是否存在
            // 4.7 如果不存在，返回错误信息
            if (shop == null) {
                //6.如果不存在，返回错误信息
                //防止缓存穿透，将空值存入redis     //注：字符串为空并不等于null
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 4.8 如果存在，将数据存储到redis中
            String json = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set(key, json,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // 4.9 释放锁
            unlock(lockKey);
        }
        // 4.9 返回数据
        return shop;
    }

    // 定义线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    //解决缓存穿透的方法
    public Shop queryWithLogicalExpire(Long id) {
        //1.根据id判断 redis 中是否存在商铺
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断缓存是否命中
        if (StrUtil.isNotBlank(shopJson)) {
            //3.如果命中，判断是否过期
            RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
            //获取商铺数据
            Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
            //获取过期时间
            LocalDateTime expireTime = redisData.getExpireTime();
            //判断是否过期
            if (expireTime.isAfter(LocalDateTime.now())) {
                // 说明没有过期，直接返回
                return shop;
            }
            // 说明过期了，需要进行缓存重建
            // 4.1 获取锁
            String lockKey = LOCK_SHOP_KEY + id;
            // 4.2 判断是否获取锁成功
            boolean isLock = tryLock(lockKey);


            //4.3 如果没有获取锁，则直接返回旧数据
            if(!isLock) {
                return shop;
            }
            // 4.4 如果获取到锁，则开启独立线程，获取数据库数据，本线程直接返回旧数据
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
            // 4.5 返回旧数据
            return shop;
        }
        if(shopJson != null) {
            // 说明缓存中有，但是数据库中不存在
            return null;
        }
        //4.如果没有命中，查询数据库
        Shop shop = getById(id);
        //5.判断数据库是否存在
        if (shop == null) {
            //6.如果不存在，返回错误信息
            //防止缓存穿透，将空值存入redis     //注：字符串为空并不等于null
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //7.如果存在，将数据存储到redis中
        //将对象转换为json字符串
        String json = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(key, json,CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return shop;
    }

    private boolean tryLock(String key) {
        // 1.获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        // 2.判断是否获取锁成功
        return Boolean.TRUE.equals(success);
    }
    private void unlock(String key) {
        // 1.删除锁
        stringRedisTemplate.delete(key);
    }



    // 将商铺信息存储到 redis 中  // 解决缓存击穿，以逻辑删除的方式
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 1.查询商铺数据
        Shop shop = getById(id);
        // 模拟查询时的延时
        Thread.sleep(200);
        // 2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入 redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional  //通过事务保证一致性
    public Result update(Shop shop) {
        // 1.更新数据库
        boolean update = updateById(shop);
        if (!update) {
            return Result.fail("更新失败");
        }
        //2.删除缓存
        String key = CACHE_SHOP_KEY + shop.getId();
        stringRedisTemplate.delete(key);
        //3.返回结果
        return Result.ok();
    }
}
