package com.ptu.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ptu.ticket.entity.Ticket;
import com.ptu.ticket.entity.TicketOrder;
import com.ptu.ticket.entity.TicketRoute;
import com.ptu.ticket.mapper.TicketMapper;
import com.ptu.ticket.mapper.TicketRouteMapper;
import com.ptu.ticket.mq.OrderMessage;
import com.ptu.ticket.pattern.BlacklistValidator;
import com.ptu.ticket.pattern.PriceStrategy;
import com.ptu.ticket.service.TicketService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TicketServiceImpl implements TicketService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final RocketMQTemplate rocketMQTemplate;
    private final TicketRouteMapper ticketRouteMapper;
    private final TicketMapper ticketMapper;
    private final Map<String, PriceStrategy> priceStrategies;
    private final BlacklistValidator blacklistValidator;

    private static final String TICKET_STOCK_PREFIX = "ticket:stock:";
    private static final String TICKET_INFO_PREFIX = "ticket:info:";
    private static final String LOCK_PREFIX = "lock:ticket:";
    
    private static final String TICKET_QUERY_PREFIX = "ticket:query:";
    private static final String LOCK_QUERY_PREFIX = "lock:ticket:query:";
    
    private final Random random = new Random();

    public TicketServiceImpl(RedisTemplate<String, Object> redisTemplate,
                             RedissonClient redissonClient,
                             RocketMQTemplate rocketMQTemplate,
                             TicketRouteMapper ticketRouteMapper,
                             TicketMapper ticketMapper,
                             Map<String, PriceStrategy> priceStrategies,
                             BlacklistValidator blacklistValidator) {
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.rocketMQTemplate = rocketMQTemplate;
        this.ticketRouteMapper = ticketRouteMapper;
        this.ticketMapper = ticketMapper;
        this.priceStrategies = priceStrategies;
        this.blacklistValidator = blacklistValidator;
    }

    @Override
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
            if (lock.tryLock(10, TimeUnit.SECONDS)) {
                route = (TicketRoute) redisTemplate.opsForValue().get(cacheKey);
                if (route != null) {
                    return route;
                }
                
                route = ticketRouteMapper.selectById(1L);
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

    @Override
    public String buyTicket(Long userId, String trainNumber, String userType) {
        TicketOrder mockOrder = new TicketOrder();
        mockOrder.setUserId(userId);
        blacklistValidator.validate(mockOrder);

        TicketRoute route = getTicketInfo(trainNumber);
        PriceStrategy strategy = priceStrategies.getOrDefault(userType + "PriceStrategy", priceStrategies.get("adultPriceStrategy"));
        Double finalPrice = strategy.calculatePrice(route.getBasePrice());

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

        String orderNo = UUID.randomUUID().toString().replace("-", "");
        OrderMessage msg = new OrderMessage(orderNo, userId, trainNumber, finalPrice);
        rocketMQTemplate.convertAndSend("TICKET_ORDER_TOPIC", msg);

        log.info("Ticket reserved for User {}. Order {} sent to MQ.", userId, orderNo);
        return "Order submitted! Please wait for async processing. OrderNo: " + orderNo;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Ticket> queryTickets(String fromStation, String toStation, String date) {
        String cacheKey = TICKET_QUERY_PREFIX + fromStation + ":" + toStation + ":" + date;
        
        List<Ticket> tickets = (List<Ticket>) redisTemplate.opsForValue().get(cacheKey);
        if (tickets != null) {
            log.info("Cache hit for query: {}", cacheKey);
            return tickets;
        }

        log.info("Cache miss for query: {}. Acquiring distributed lock...", cacheKey);
        
        RLock lock = redissonClient.getLock(LOCK_QUERY_PREFIX + fromStation + ":" + toStation + ":" + date);
        try {
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                tickets = (List<Ticket>) redisTemplate.opsForValue().get(cacheKey);
                if (tickets != null) {
                    log.info("Cache hit on double-check for query: {}", cacheKey);
                    return tickets;
                }

                LocalDate localDate = LocalDate.parse(date);
                QueryWrapper<Ticket> wrapper = new QueryWrapper<>();
                wrapper.eq("from_station", fromStation)
                       .eq("to_station", toStation)
                       .eq("date", localDate);
                tickets = ticketMapper.selectList(wrapper);

                long ttl;
                if (tickets == null || tickets.isEmpty()) {
                    tickets = new ArrayList<>();
                    ttl = 300 + random.nextInt(60);
                    log.info("Database returned empty for query: {}. Caching empty list, TTL={}s", cacheKey, ttl);
                } else {
                    ttl = 1800 + random.nextInt(300);
                    log.info("Database returned {} records for query: {}. Caching with TTL={}s", tickets.size(), cacheKey, ttl);
                }

                redisTemplate.opsForValue().set(cacheKey, tickets, ttl, TimeUnit.SECONDS);
                return tickets;
            } else {
                log.warn("Failed to acquire lock for query: {}", cacheKey);
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
}