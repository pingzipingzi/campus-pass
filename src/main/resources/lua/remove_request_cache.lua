if not KEYS[1] or not KEYS[2] or not ARGV[1] then
    return -1
end

redis.call('del',KEYS[1])

if redis.call('exists',KEYS[2])==0 then
    return -2
end

redis.call('hincrby',KEYS[2],'remainingCapacity',tonumber(ARGV[1]))

return 0
