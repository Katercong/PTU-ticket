package com.ptu.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ptu.ticket.entity.Ticket;
import com.ptu.ticket.entity.TicketOrder;
import com.ptu.ticket.entity.TicketRoute;
import com.ptu.ticket.mapper.TicketMapper;
import com.ptu.ticket.mapper.TicketRouteMapper;
import com.ptu.ticket.mq.OrderMessage;
import com.ptu.ticket.pattern.BlacklistValidator;
import com.ptu.ticket.pattern.OrderValidator;
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
    private final OrderValidator orderValidatorChain;

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
                             BlacklistValidator blacklistValidator,
                             OrderValidator orderValidatorChain) {
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.rocketMQTemplate = rocketMQTemplate;
        this.ticketRouteMapper = ticketRouteMapper;
        this.ticketMapper = ticketMapper;
        this.priceStrategies = priceStrategies;
        this.blacklistValidator = blacklistValidator;
        this.orderValidatorChain = orderValidatorChain;
    }

    @Override
    public TicketRoute getTicketInfo(String trainNumber) {
        String cacheKey = TICKET_INFO_PREFIX + trainNumber;
        // 1. 查缓存
        TicketRoute route = (TicketRoute) redisTemplate.opsForValue().get(cacheKey);
        
        if (route != null) {
            log.info("命中缓存：{}", trainNumber);
            return route;
        }

        log.info("缓存未命中：{}。准备获取分布式锁重建缓存...", trainNumber);
        // 2. 缓存击穿防护：获取 Redisson 分布式锁
        RLock lock = redissonClient.getLock(LOCK_PREFIX + trainNumber);
        try {
            // 尝试加锁。如果不设置 leaseTime，Redisson 的看门狗(Watchdog)会自动每 10 秒续期一次
            if (lock.tryLock(10, TimeUnit.SECONDS)) {
                // 双重检查锁定 (Double Check)
                route = (TicketRoute) redisTemplate.opsForValue().get(cacheKey);
                if (route != null) {
                    return route;
                }
                
                // 从数据库读取
                route = ticketRouteMapper.selectById(1L);
                if (route != null) {
                    // 将查询结果放入 Redis 缓存并设置 60 分钟过期时间
                    redisTemplate.opsForValue().set(cacheKey, route, 60, TimeUnit.MINUTES);
                }
                return route;
            } else {
                throw new RuntimeException("系统正忙，请稍后再试。");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("线程被中断", e);
        } finally {
            // 确保只有持有锁的当前线程才能解锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public String buyTicket(Long userId, String trainNumber, String userType) {
        // 1. 责任链模式：参数校验 + 黑名单校验（链头触发，按顺序执行所有节点）
        TicketOrder mockOrder = new TicketOrder();
        mockOrder.setUserId(userId);
        mockOrder.setTrainNumber(trainNumber);
        orderValidatorChain.validate(mockOrder);

        // 2. 策略模式：动态票价计算
        TicketRoute route = getTicketInfo(trainNumber);
        PriceStrategy strategy = priceStrategies.getOrDefault(userType + "PriceStrategy", priceStrategies.get("adultPriceStrategy"));
        Double finalPrice = strategy.calculatePrice(route.getBasePrice());

        // 3. Lua 脚本：原子扣减 Redis 库存（防止超卖）
        String stockKey = TICKET_STOCK_PREFIX + trainNumber;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // 从 classpath 加载 ticket_deduct.lua，保证脚本可审计、可复用
        script.setScriptSource(new org.springframework.scripting.support.ResourceScriptSource(
                new org.springframework.core.io.ClassPathResource("lua/ticket_deduct.lua")));
        script.setResultType(Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(stockKey), 1);

        if (result == null || result == 0) {
            throw new RuntimeException("手慢了，车票已售罄！");
        } else if (result == -1) {
            throw new RuntimeException("Redis 中尚未初始化该车次库存！");
        }

        // 4. RocketMQ 异步发送订单消息（流量削峰）
        String orderNo = UUID.randomUUID().toString().replace("-", "");
        OrderMessage msg = new OrderMessage(orderNo, userId, trainNumber, finalPrice);
        rocketMQTemplate.convertAndSend("TICKET_ORDER_TOPIC", msg);

        log.info("用户 {} 抢票成功，已发送订单 {} 到 MQ 异步落库。", userId, orderNo);
        return "下单请求已受理！请稍候在订单中心查看。订单号: " + orderNo;
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