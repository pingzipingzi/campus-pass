if not KEYS[1] or not ARGV[1] then
    return -1
end

if redis.call('hget',KEYS[1],'isPending')~='1' then
    return -2
end

redis.call('hset',KEYS[1],'isPending',0)

redis.call('zrem','registration:message:pending',ARGV[1])

return 0