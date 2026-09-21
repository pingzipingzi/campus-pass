local key=KEYS[1]
local code=ARGV[1]

local cacheCode=redis.call('get',key)
if not cacheCode then
    return 0
end

if cacheCode ~= code then
    return 0
end

return redis.call('del',key)
