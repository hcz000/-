-- KEYS[1]: reply count key
-- KEYS[2]: dirty zset key
-- ARGV[1]: delta (integer)
-- ARGV[2]: postings id
-- ARGV[3]: timestamp millis
local delta = tonumber(ARGV[1])
local postingsId = ARGV[2]
local ts = tonumber(ARGV[3])
local current = tonumber(redis.call("GET", KEYS[1]) or "0")
local next = current + delta
if next < 0 then
  next = 0
end
redis.call("SET", KEYS[1], tostring(next))
if postingsId ~= false and ts ~= nil then
  redis.call("ZADD", KEYS[2], ts, postingsId)
end
return next
