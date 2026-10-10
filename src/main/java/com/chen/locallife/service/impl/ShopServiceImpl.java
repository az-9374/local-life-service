package com.chen.locallife.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen.locallife.dto.Result;
import com.chen.locallife.entity.Shop;
import com.chen.locallife.mapper.ShopMapper;
import com.chen.locallife.service.IShopService;
import com.chen.locallife.utils.CacheClient;
import com.chen.locallife.utils.RedisConstants;
import com.chen.locallife.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.chen.locallife.utils.RedisConstants.*;

/**
 * 服务实现类
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @Autowired
    private ShopMapper shopMapper;



    /**
     * 根据id查询商铺信息
     */
    @Override
    public Result queryShopById(Long id) {
        //
        String key="shop:id-"+id;
        String value=stringRedisTemplate.opsForValue().get(key);//json->shop
        if(value!=null){//redis
            if("".equals(value)){//缓存穿透
                return Result.ok(null);
            }
            Shop shop=JSONUtil.toBean(value,Shop.class);
            System.out.println("从redis取出~~~");
            return Result.ok(shop);
        }

        //mysql
        Shop shop=baseMapper.selectById(id);//shop->json
        if(shop==null){
            //存redis

            stringRedisTemplate.opsForValue().set(key,"",5,TimeUnit.MINUTES);
            return Result.ok(null);
        }
        String jsonVal=JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(key,jsonVal,30,TimeUnit.MINUTES);
        return Result.ok(shop);
    }


    @Override
    public Result queryShop() {
        String key="shop:all";
        String cachedJson = stringRedisTemplate.opsForValue().get(key);
        if(cachedJson!=null){
            if("".equals(cachedJson)){return Result.ok(null);}//处理缓存穿透
            List<Shop> shopList= JSONUtil.toList(cachedJson,Shop.class);//讲讲这句语法
            System.out.println("从redis取出~~~");
            return Result.ok(shopList, (long) shopList.size());
        }
        List<Shop> shopList=shopMapper.selectList(null);//没太看懂这个select的语法，常用的有哪些

        if(shopList==null || shopList.isEmpty()){//处理缓存穿透
            stringRedisTemplate.opsForValue().set(key,"",5,TimeUnit.MINUTES);
            return Result.ok(null);
        }
        String json=JSONUtil.toJsonStr(shopList);
        stringRedisTemplate.opsForValue().set(key,json,60,TimeUnit.MINUTES);
        System.out.println("从mysql取出~~~");
        return Result.ok(shopList, (long) shopList.size());
    }

    @Override
    public Result queryById(Long id) {
        //缓存穿透
//        Shop shop = queryWithPassThrough(id);
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);//在下面
        //逻辑过期解决缓存击穿
//        Shop shop = queryWithLogicalExpire(id);
//        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        if (shop == null) {
//            return Result.fail("店铺不存在");
//        }
//        return Result.ok(shop);

        //普通的缓存穿透

        Shop shop = cacheClient.queryWithPassThrough(
                CACHE_SHOP_KEY,id, Shop.class,
                this::getById,
                CACHE_SHOP_TTL,TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 互斥锁解决缓存击穿
     *
     * @param id id
     * @return {@link Shop}
     */
    /*private Shop queryWithMutex(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        //从redis中查询
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //判断是否存在
        if (StringUtils.isNotEmpty(shopJson)) {
            //存在直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断控值
        if ("".equals(shopJson)) {
            return null;
        }
        //实现缓存重建
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            //是否获取成功
            if (!isLock) {
                //获取失败 休眠并且重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //成功 通过id查询数据库
            shop = getById(id);
            //模拟重建延时
            Thread.sleep(200);//？不太理解
            if (shop == null) {//为啥不判断是否为空“”？
                //redis写入空值
                stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                //数据库不存在 返回错误
                return null;
            }
            //数据库存在 写入redis
            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            unLock(lockKey);
        }
        //返回
        return shop;
    }*/

    /**
     * 逻辑过期解决缓存击穿
     *
     * @param id id
     * @return {@link Shop}
     */
    /*private Shop queryWithLogicalExpire(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        //从redis中查询
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //判断是否存在
        if (StringUtils.isEmpty(shopJson)) {
            //不存在返回空
            return null;
        }
        //命中 反序列化
        RedisDate redisDate = JSONUtil.toBean(shopJson, RedisDate.class);
        JSONObject jsonObject = (JSONObject) redisDate.getData();
        Shop shop = BeanUtil.toBean(jsonObject, Shop.class);
        LocalDateTime expireTime = redisDate.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期 直接返回
            return shop;
        }
        //已过期
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean flag = tryLock(lockKey);
        //是否获取锁成功
        if (flag) {
            //成功 异步重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShopToRedis(id, 20L);//存redisData
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        //返回过期商铺信息
        return shop;
    }*/

    /**
     * 使用设置空值解决缓存穿透
     */
   /* private Shop queryWithPassThrough(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        //从redis中查询
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //判断是否存在
        if (StringUtils.isNotEmpty(shopJson)) {
            //存在直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断空值
        if ("".equals(shopJson)) {
            return null;
        }
        //不存在 查询数据库
        Shop shop = getById(id);
        if (shop == null) {
            //redis写入空值
            stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            //数据库不存在 返回错误
            return null;
        }
        //数据库存在 写入redis
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回
        return shop;
    }*/
    @Override
    @Transactional(rollbackFor = Exception.class)//告诉 Spring：这个方法里的所有数据库操作，要么全部成功，要么全部回滚
    //rollbackFor = Exception.class遇到任何异常都回滚（默认只对 RuntimeException 回滚）
    public Result update(Shop shop) {//更新
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("id不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());//等它下次查询就有缓存了是吧
        return Result.ok();
    }

    //shopType+shop表

    /**
     * @param typeId  id类型
     * @param current 当前页
     * @param x       x坐标
     * @param y       y坐标
     * @return
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //判断是否需要坐标查询
        if (x == null || y == null) {
            //不需要坐标查询
            Page<Shop> page = lambdaQuery()
                    .eq(Shop::getTypeId, typeId)//我有点看不懂，条件嘛
                    .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));//看不懂，分页嘛但我具体句子看不懂
            return Result.ok(page.getRecords());//page.getRecords()啥？？
        }
        //计算分页参数
        int from = (current - 1) * SystemConstants.MAX_PAGE_SIZE;//
        int end = current * SystemConstants.MAX_PAGE_SIZE;
        //因为 Redis 的 GEO 查询不支持分页，所以要自己算范围，取出来后再手动截取。
        //查询redis 距离排序 分页
        String key= SHOP_GEO_KEY+typeId;
        //这个geo我没看不太懂
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(key//（范围）某个key里面存的一堆地点
                        , GeoReference.fromCoordinate(x, y)
                        , new Distance(5000)
                        , RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                                .includeDistance()//返回结果带距离
                                .limit(end)// 最多返回 end 条
                );


        //解析出id
        if (results==null){
            return Result.ok(Collections.emptyList());
            //返回一个空列表，而不是 null。
            //为什么？ 因为前端拿到空列表可以直接遍历，如果拿到 null 会报空指针异常。
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        //？?你result不是列表还能取个列表出来
        if (content.size()<from){//如果查出来的数据总数，还没到当前页的起始位置，说明没有这一页
            //没有下一页
            return Result.ok();
        }
        //截取
        List<Long> ids=new ArrayList<>(content.size());
        Map<String,Distance> distanceMap=new HashMap<>();
        content.stream().skip(from).forEach(result->{
            //skip跳过了前面from条
            //店铺id
            String shopId = result.getContent().getName();//这个getname取的是当时存的value吗
            ids.add(Long.valueOf(shopId));//？转成long吗，和（long）的区别是--后者只能数值来转
            //怎么一会name一会id给我看懵了，最后要的是id吧，那为啥用的是getName？--就是id
            //距离
            Distance distance = result.getDistance();
            distanceMap.put(shopId,distance);
        });
        //根据id查询shop
        String join = StrUtil.join(",", ids);//把 ids 列表用逗号拼接成字符串
        List<Shop> shopList = lambdaQuery()
                .in(Shop::getId, ids)//为啥是in，这句啥条件，WHERE id IN (1,3,5,2)
                .last("order by field(id,"+join+")")
                //ORDER BY FIELD(id, 1,3,5,2)，可以拼接自定义 SQL 片段，这里用来指定排序顺序
                .list();//转成列表
        for (Shop shop : shopList) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());//层层get
        }

        return Result.ok(shopList);
    }

//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /*    *//**
     * 获取锁
     *
     * @param key 关键
     * @return boolean
     *//*
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    *//**
     * 释放锁
     *
     * @param key 关键
     *//*
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    *//**
     * 存入redis 携带逻辑过期时间
     *//*
    public void saveShopToRedis(Long id, Long expireSeconds) throws InterruptedException {
        //查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);//？？
        //封装逻辑过期
        RedisDate redisDate = new RedisDate();
        redisDate.setData(shop);
        redisDate.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写了redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisDate));
    }*/
}
