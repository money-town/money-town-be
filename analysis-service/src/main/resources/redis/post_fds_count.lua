-- KEYS[1]=fail KEYS[2]=limit KEYS[3]=recent
-- ARGV[1]=eventScore(occurredAt ms) ARGV[2]=eventId ARGV[3]=eventType
-- ARGV[4]=failWindowMs ARGV[5]=limitWindowMs ARGV[6]=sampleSize
-- ARGV[7]=ttlSecShort(fail/limit) ARGV[8]=ttlSecRecent

local score = tonumber(ARGV[1])
local eventId = ARGV[2]
local eventType = ARGV[3]

-- 1. fail 윈도우 (SUBSCRIPTION_FAILED만)
if eventType == 'SUBSCRIPTION_FAILED' then
    redis.call('ZADD', KEYS[1], 'NX', score, eventId)
    redis.call('EXPIRE', KEYS[1], ARGV[7])
end
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, score - tonumber(ARGV[4]))
local failCount = redis.call('ZCOUNT', KEYS[1], score - tonumber(ARGV[4]), score)


-- 2. limit 윈도우 (SUBSCRIPTION_LIMIT_EXCEEDED)
if eventType == 'SUBSCRIPTION_LIMIT_EXCEEDED' then
    redis.call('ZADD', KEYS[2], 'NX', score, eventId)
    redis.call('EXPIRE', KEYS[2], ARGV[7])
end
redis.call('ZREMRANGEBYSCORE', KEYS[2], 0, score - tonumber(ARGV[5]))
local limitCount = redis.call('ZCOUNT', KEYS[2], score - tonumber(ARGV[5]),score)

-- 3. recent(모든 타입) - NX로 중복 무시, 최근 sampleSize 개만 유지
local sampleSize = tonumber(ARGV[6])
redis.call('ZADD', KEYS[3], 'NX', score, eventId .. '|' .. eventType)
redis.call('EXPIRE', KEYS[3], ARGV[8])
local total = redis.call('ZCARD', KEYS[3])
if total > sampleSize then
    redis.call('ZREMRANGEBYRANK', KEYS[3], 0, total - sampleSize - 1)
end
local members = redis.call('ZRANGE', KEYS[3], 0, -1)
local cancelled = 0
for _, m in ipairs(members) do
    local pipe = string.find(m, '|', 1, true)     -- 1=시작위치, true=평문검색(정규식X)
    local etype = string.sub(m, pipe + 1)
    if etype ==  'SUBSCRIPTION_CANCELLED' then
      cancelled = cancelled + 1
    end
end

return {failCount, limitCount, #members, cancelled}