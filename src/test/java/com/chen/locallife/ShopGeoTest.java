package com.chen.locallife;

import com.chen.locallife.entity.Shop;
import com.chen.locallife.mapper.ShopMapper;
import com.chen.locallife.service.IShopService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.geo.Point;

@SpringBootTest
class ShopGeoTest {

    @Resource
    private IShopService shopService;
    @Autowired
    private ShopMapper shopMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

//    @Test
    void loadShopGeo() {
        // 1. 查询所有店铺
        List<Shop> shopList = shopService.list();

        // 2. 按 typeId 分组
        Map<Long, List<Shop>> map = shopList.stream()
                .collect(Collectors.groupingBy(Shop::getTypeId));

        // 3. 遍历，写入 Redis GEO
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            String key = "shop:geo:" + typeId;

            for (Shop shop : entry.getValue()) {
                stringRedisTemplate.opsForGeo().add(
                        key,
                        new Point(shop.getX(), shop.getY()),
                        shop.getId().toString()
                );
            }
        }
    }

    @Test
    void testSelectById() {
        Shop shop = shopMapper.selectById(12L);
        System.out.println(shop);
    }
}
