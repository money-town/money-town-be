-- KEYS[1] = fds:req:{userId} KEYS[2] = fds:offering:{userId}
-- ARGV[1] = now(ms) ARGV[2] = requestId ARGV[3] = assetId
-- ARGV[4] = rapidWindowMs ARGV[5] = burstWindowMs ARGV[6] = ttlSec

local now = tonumber(ARGV[1])
local rapidFrom = now - tonumber(ARGV[4])
local burstFrom = now - tonumber(ARGV[5])

redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, burstFrom)
redis.call('ZADD', KEYS[1], now, ARGV[2])
redis.call('EXPIRE', KEYS[1], ARGV[6])

redis.call('ZREMRANGEBYSCORE', KEYS[2], 0, burstFrom)
redis.call('ZADD', KEYS[2], now, ARGV[3])
redis.call('EXPIRE', KEYS[2], ARGV[6])

local rapid = redis.call('ZCOUNT', KEYS[1], rapidFrom, now)
local burst = redis.call('ZCARD', KEYS[1])
local offerings = redis.call('ZCARD', KEYS[2])

return {rapid, burst, offerings}