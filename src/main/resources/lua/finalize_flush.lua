-- KEYS[1]: list key
-- KEYS[2]: dirty zset key
-- ARGV[1]: user id
-- ARGV[2]: timestamp millis
local listKey = KEYS[1]
local dirtyKey = KEYS[2]
local userId = ARGV[1]
local now = tonumber(ARGV[2])

local remaining = redis.call('LLEN', listKey)
if remaining == 0 then
    redis.call('DEL', listKey)
    redis.call('ZREM', dirtyKey, userId)
    return 0
else
    redis.call('ZADD', dirtyKey, now, userId)
    return remaining
end