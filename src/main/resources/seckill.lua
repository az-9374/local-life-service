local voucherId=ARGV[1]
--秒杀卷id
local userId=ARGV[2]
--userId

--11.订单id
local orderId=ARGV[3]

--库存key
local stockKey='seckill:stock:' .. voucherId
--订单key
local orderKey="seckill:order:" .. voucherId

--判断1.库存
if(tonumber(redis.call("get",stockKey))<=0)then
    return 1
end

-- 判断2.一人一单？
if(tonumber(redis.call("sismember",orderKey,userId))==1)then
    return 2
end

--扣库存
redis.call("incrby",stockKey,-1)
--存下单set
redis.call("sadd",orderKey,userId)

--11.把消息发到Stream
redis.call("xadd","stream.orders","*",
      "userId",userId,
      "voucherId",voucherId,
      "id",orderId)

return 0