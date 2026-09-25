--[[
local key= KEY[1]

local ttl=KEY[2]

if #ARGV==0 or #ARGV%2 ~=0 then
    return -1
end

redis.call('hset',key,unpack(ARGV))

redis.call('expire' ,key,ttl)

return 0]]


local key=KEYS[1]

local ttl=tonumber(ARGV[1])

if not ttl or ttl<0 or #ARGV==1 or #ARGV%2 ~=1 then
    return -1
end

for i=2,#ARGV,2 do
    redis.call('HSET',key,ARGV[i],ARGV[i+1])
end

redis.call('EXPIRE',key,ttl)

return 0