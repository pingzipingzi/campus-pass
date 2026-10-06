if not ARGV[1] or not ARGV[2] or not KEYS[1] or not KEYS[2] or not KEYS[3] or not ARGV[3]
        or not ARGV[4] or not ARGV[5] or not KEYS[4] then
    return -1
end

local now=tonumber(ARGV[1])
local registrationStartTime=tonumber(redis.call('hget',KEYS[1],'registrationStartTime'))
local registrationEndTime=tonumber(redis.call('hget',KEYS[1],'registrationEndTime'))

if not registrationEndTime or not registrationStartTime or now < registrationStartTime or now >= registrationEndTime then
    return -2
end

local remainCapacity=tonumber(redis.call('hget',KEYS[1],'remainingCapacity'))

if not remainCapacity or remainCapacity<0 then
    return -1
end

local result =redis.call('exists',KEYS[2])
if result==1 then
    return 2
end

redis.call('zadd',KEYS[4],now+30000,ARGV[2])

redis.call('hset',KEYS[2],'requestId',ARGV[2])
redis.call('pexpire',KEYS[2],(registrationEndTime-now)+12*60*60*1000+tonumber(ARGV[3])*1000)

redis.call('hset' ,KEYS[3],'requestId',ARGV[2],'sendTime',ARGV[1],'activityId',ARGV[4],
        'userId',ARGV[5],'isPending',1,'failureTimes',0)
redis.call('pexpire',KEYS[3],(registrationEndTime-now)+12*60*60*1000+tonumber(ARGV[3])*1000)

if remainCapacity ==0 then
    redis.call('hset',KEYS[3],'cacheStatus',1)
    return 1
end

redis.call('hset',KEYS[3],'cacheStatus',0)

redis.call('hincrby',KEYS[1],'remainingCapacity',-1)

return 0
