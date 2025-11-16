package com.ptu.ticket.service;

import com.ptu.ticket.entity.TicketOrder;
import com.ptu.ticket.entity.TicketRoute;
import com.ptu.ticket.mapper.TicketRouteMapper;
import com.ptu.ticket.mq.OrderMessage;
import com.ptu.ticket.pattern.BlacklistValidator;
import com.ptu.ticket.pattern.PriceStrategy;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TicketService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final RocketMQTemplate rocketMQTemplate;
    private final TicketRouteMapper ticketRouteMapper;
    private final Map<String, PriceStrategy> priceStrategies;
    private final BlacklistValidator blacklistValidator;

    private static final String TICKET_STOCK_PREFIX = "ticket:stock:";
    private static final String TICKET_INFO_PREFIX = "ticket:info:";
    private static final String LOCK_PREFIX = "lock:ticket:";

    public TicketService(RedisTemplate<String, Object> redisTemplate,
                         RedissonClient redissonClient,
                         RocketMQTemplate rocketMQTemplate,
                         TicketRouteMapper ticketRouteMapper,
                         Map<String, PriceStrategy> priceStrategies,
                         BlacklistValidator blacklistValidator) {
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.rocketMQTemplate = rocketMQTemplate;
        this.ticketRouteMapper = ticketRouteMapper;
        this.priceStrategies = priceStrategies;
        this.blacklistValidator = blacklistValidator;
    }

    // M2: Cache-Aside & Redisson Lock (Prevent Cache Breakdown)
    public TicketRoute getTicketInfo(String trainNumber) {
        String cacheKey = TICKET_INFO_PREFIX + trainNumber;
        TicketRoute route = (TicketRoute) redisTemplate.opsForValue().get(cacheKey);
        
        if (route != null) {
            log.info("Cache hit for {}", trainNumber);
            return route;
        }

        log.info("Cache miss for {}. Acquiring distributed lock...", trainNumber);
        RLock lock = redissonClient.getLock(LOCK_PREFIX + trainNumber);
        try {
            // Lock with watchdog enabled automatically if leaseTime is not set
            if (lock.tryLock(10, TimeUnit.SECONDS)) {
                // Double check
                route = (TicketRoute) redisTemplate.opsForValue().get(cacheKey);
                if (route != null) {
                    return route;
                }
                
                // Read from DB
                // Simulating select by trainNumber (Requires QueryWrapper in reality, simplified here)
                route = ticketRouteMapper.selectById(1L); // Mocking DB read
                if (route != null) {
                    redisTemplate.opsForValue().set(cacheKey, route, 60, TimeUnit.MINUTES);
                }
                return route;
            } else {
                throw new RuntimeException("System busy, please try again later.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // M3: Redis Lua Deduct & RocketMQ Peak Shaving
    public String buyTicket(Long userId, String trainNumber, String userType) {
        // 1. Parameter Validation via Chain of Responsibility
        TicketOrder mockOrder = new TicketOrder();
        mockOrder.setUserId(userId);
        blacklistValidator.validate(mockOrder); // Throws if invalid

        // 2. Price calculation via Strategy Pattern
        TicketRoute route = getTicketInfo(trainNumber); // Cached
        PriceStrategy strategy = priceStrategies.getOrDefault(userType + "PriceStrategy", priceStrategies.get("adultPriceStrategy"));
        Double finalPrice = strategy.calculatePrice(route.getBasePrice());

        // 3. Atomic Stock Deduction via Redis Lua
        String stockKey = TICKET_STOCK_PREFIX + trainNumber;
        String luaScript = "local stock = tonumber(redis.call('get', KEYS[1])) " +
                           "local amount = tonumber(ARGV[1]) " +
                           "if stock == nil then return -1 end " +
                           "if stock >= amount then redis.call('decrby', KEYS[1], amount) return 1 else return 0 end";
        
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(luaScript, Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(stockKey), 1);

        if (result == null || result == 0) {
            throw new RuntimeException("Tickets sold out!");
        } else if (result == -1) {
            throw new RuntimeException("Stock not initialized in Redis!");
        }

        // 4. Send Async Message to RocketMQ (Peak Shaving)
        String orderNo = UUID.randomUUID().toString().replace("-", "");
        OrderMessage msg = new OrderMessage(orderNo, userId, trainNumber, finalPrice);
        rocketMQTemplate.convertAndSend("TICKET_ORDER_TOPIC", msg);

        log.info("Ticket reserved for User {}. Order {} sent to MQ.", userId, orderNo);
        return "Order submitted! Please wait for async processing. OrderNo: " + orderNo;
    }
}
