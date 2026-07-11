-- 滑动窗口限流
-- KEYS[1]: 窗口 key，如 wen-saas-boot:openapi:ratelimit:{tenantId}
-- ARGV[1]: now（毫秒）
-- ARGV[2]: window（毫秒）
-- ARGV[3]: limit（窗口内允许的最大请求数）
-- ARGV[4]: member（本次请求的唯一标识，由调用方生成）
-- 返回: { allowed (1=允许 / 0=拒绝), remaining }
local key    = KEYS[1]
local now    = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit  = tonumber(ARGV[3])
local member = ARGV[4]

-- 1. 清理窗口外的旧请求
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
-- 2. 当前窗口内请求数
local count = redis.call('ZCARD', key)
if count >= limit then
    -- 3. 超限，返回当前剩余（恒 0）
    return { 0, 0 }
end
-- 4. 入窗
redis.call('ZADD', key, now, member)
-- 5. 续 TTL（防止 zset 永久残留）
redis.call('PEXPIRE', key, window)
-- 6. 剩余 = limit - (count + 1)
return { 1, limit - count - 1 }
