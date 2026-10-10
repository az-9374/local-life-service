import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

public class JedisTest {
    @Test
    public void testJedis(){
        // 1. 建立连接
        Jedis jedis=new Jedis("192.168.100.130",6379);
        jedis.auth("123456");

        jedis.select(1);
        // 2. 存数据
        jedis.set("jedis:name:2","tabayuer");
        System.out.println("存入成功！");
        // 3. 取数据
        String value= jedis.get("jedis:name");
        System.out.println("取出的值："+value);
        // 4. 关闭连接
        jedis.close();
    }
}
