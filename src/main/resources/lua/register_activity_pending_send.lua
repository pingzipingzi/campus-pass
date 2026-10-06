if not ARGV[1] or not ARGV[2] or not KEYS[1] then
    return -1
end

local expiration= tonumber(redis.call('zscore','registration:message:pending',ARGV[1]))

if not expiration or redis.call('exists',KEYS[1]) ==0 then
    return -2
end

local now=tonumber(ARGV[2])

if expiration> now then
    return -1
end

local failureTimes =tonumber(redis.call('hget',KEYS[1],'failureTimes'))

if failureTimes >= 5 then
    return -3
end

redis.call('zadd','registration:message:pending',now+2^(failureTimes+1)*30000,ARGV[1])

redis.call('hset',KEYS[1],'failureTimes',failureTimes+1)

return 0