if not KEYS[1] or not KEYS[2] or not KEYS[3]  or not ARGV[1] or not ARGV[2] or not ARGV[3] then
    return -1
end

if redis.call('exists',KEYS[3])==1 then
    return 1
end

local requestId=redis.call('hget',KEYS[2],'requestId')

if not requestId or requestId~=ARGV[2] then
    return -2
end

if redis.call('exists',KEYS[1]) ==0 then
    return -3
end

if tonumber(ARGV[1])==0 then
    redis.call('hincrby',KEYS[1],"remainingCapacity",1)
end

redis.call('set',KEYS[3],1)

redis.call('pexpireat',KEYS[3],(ARGV[3])+12*60*60*1000)

redis.call('del',KEYS[2])

return 0