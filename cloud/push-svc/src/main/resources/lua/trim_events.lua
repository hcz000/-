-- KEYS[1]: list key
-- ARGV[1]: count to trim
local key = KEYS[1]
local count = tonumber(ARGV[1])
for i = 1, count do
    redis.call('LPOP', key)
end
return count
