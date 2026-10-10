import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.*;

//@SpringBootTest
public class RedisTemplateTest {


    @Test
    public void testHand(){
        //1.工厂--信息--初始化
        LettuceConnectionFactory factory=new LettuceConnectionFactory();
        factory.setDatabase(1);
        factory.setHostName("192.168.100.130");
        factory.setPassword("123456");
        factory.setPort(6379);
        factory.afterPropertiesSet();
        //2.用工厂创srt--初始化
        StringRedisTemplate template=new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        //3.操作
//        String[] keys={"redis:lovers:1","redis:lovers:2"};
        List<String> keys= Arrays.asList("redis:lovers:1","redis:lovers:2");
        template.opsForValue().set(keys.get(0),"chengjinchou");
        template.opsForValue().set(keys.get(1),"tabayuer");
        String val=template.opsForValue().get(keys.get(0));

        List<String> lovers=template.opsForValue().multiGet(keys);
        System.out.println(lovers.get(0)+"和"+lovers.get(1)+"是一对！");

        Map<String,String> map=new HashMap<>();
        map.put("price","120");
        map.put("stock","20");
        template.opsForHash().put("product:1","name","001");
        template.opsForHash().putAll("product:1",map);
        Object productName=template.opsForHash().get("product:1","name");
        String nameval=(String) productName;
        System.out.println("商品名称为："+nameval);

        template.opsForList().leftPush("queue:task","msg1");
        template.opsForList().leftPush("queue:task","msg2");
        template.opsForList().leftPush("queue:task","msg3");
        String valList= template.opsForList().rightPop("queue:task");
        System.out.println(valList);

        template.opsForSet().add("user:tags:1","java","redis","spring");
        Set<String> set=template.opsForSet().members("user:tags:1");
        System.out.println("Set: "+set);

        template.opsForZSet().add("rank:score","user1",100);
        template.opsForZSet().add("rank:score","user2",200);
        template.opsForZSet().add("rank:score","user3",150);
        Set<ZSetOperations.TypedTuple<String>> zset=
        template.opsForZSet().popMax("rank:score",2);
        System.out.println("zset: "+zset);

        // 最后
        factory.destroy();  // 关闭连接池

    }

//    @Autowired
//    private StringRedisTemplate stringRedisTemplate;
//
//    @Test
//    public void testRedisTemplate() {
//        // 1. 存数据
//        stringRedisTemplate.opsForValue().set("srt:name", "yuer");
//        System.out.println("✅ 存入成功！key=srt:name, value=yuer");
//
//        // 2. 取数据
//        String value = stringRedisTemplate.opsForValue().get("srt:name");
//        System.out.println("✅ 取出的值：" + value);
//    }

}