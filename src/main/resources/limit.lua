-- 秒杀限流--在指定时间内只能有limit次秒杀请求
local key=KEYS[1]
local limit=tonumber(ARGV[1])
local expire=tonumber(ARGV[2])
local current=tonumber(redis.call('get',key)or '0')
if(current +1 >limit) then
    return 0
else
    redis.call("incr",key)
    if(current==0) then
        redis.call('expire',key,expire)
    end
    return 1
end
