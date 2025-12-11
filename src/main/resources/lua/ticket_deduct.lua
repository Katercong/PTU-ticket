-- ticket_deduct.lua
-- 通过 Spring DefaultRedisScript 从 classpath 加载执行，保证扣减库存的原子性。
-- KEYS[1] = ticket_stock_key
-- ARGV[1] = deduct_amount

local stock = tonumber(redis.call('get', KEYS[1]))
local amount = tonumber(ARGV[1])

if stock == nil then
    return -1 -- Stock not initialized
end

if stock >= amount then
    redis.call('decrby', KEYS[1], amount)
    return 1 -- Success
else
    return 0 -- Insufficient stock
end
