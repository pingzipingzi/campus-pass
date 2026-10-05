
local key=KEYS[1]

local ttl=tonumber(ARGV[1])

if not ttl or ttl<0 or #ARGV==1 or #ARGV%2 ~=1 then
    return -1
end

local isExisted = redis.call('exists',key)

if isExisted~=0 then
    return -2
end

for i=2,#ARGV,2 do
    redis.call('HSET',key,ARGV[i],ARGV[i+1])
end

redis.call('EXPIRE',key,ttl)

return 0