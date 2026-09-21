local key=KEYS[1]
local limitTimes=tonumber(ARGV[1])
local windowSeconds=tonumber(ARGV[2])

if not KEYS[1] or KEYS[1]=="" or not limitTimes or limitTimes <1 or not windowSeconds or windowSeconds<1
        or limitTimes~=math.ceil(limitTimes) or windowSeconds~=math.ceil(windowSeconds) then
    return -1
end

local times=redis.call('incr',key)
if times==1 or redis.call('ttl',key)==-1 then
    redis.call('expire',key,windowSeconds)
end
if times>limitTimes then
    return 0
end

return 1
