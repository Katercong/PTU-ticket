package com.ptu.ticket;

import com.ptu.ticket.entity.Ticket;
import com.ptu.ticket.service.TicketService;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration"
})
@Import(TicketQueryIntegrationTest.MockConfig.class)
public class TicketQueryIntegrationTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String TICKET_QUERY_PREFIX = "ticket:query:";

    @TestConfiguration
    static class MockConfig {
        @Bean
        public RocketMQTemplate rocketMQTemplate() {
            return Mockito.mock(RocketMQTemplate.class);
        }
    }

    @BeforeEach
    public void setUp() {
        redisTemplate.delete(TICKET_QUERY_PREFIX + "Beijing:Shanghai:2026-06-17");
        redisTemplate.delete(TICKET_QUERY_PREFIX + "Beijing:Shanghai:2026-06-18");
        redisTemplate.delete(TICKET_QUERY_PREFIX + "Beijing:Shanghai:2026-06-25");
    }

    @Test
    public void testCacheAsideFlow() {
        String from = "Beijing";
        String to = "Shanghai";
        String date = "2026-06-17";
        String cacheKey = TICKET_QUERY_PREFIX + from + ":" + to + ":" + date;

        // Ensure cache is empty
        redisTemplate.delete(cacheKey);

        // 1. First query (Cache Miss, reads from DB and caches it)
        List<Ticket> tickets1 = ticketService.queryTickets(from, to, date);
        assertNotNull(tickets1);
        assertFalse(tickets1.isEmpty());
        assertEquals(2, tickets1.size()); // Beijing to Shanghai on 2026-06-17 has ADULT and STUDENT

        // Verify it is cached in Redis now
        List<Ticket> cachedTickets = (List<Ticket>) redisTemplate.opsForValue().get(cacheKey);
        assertNotNull(cachedTickets);
        assertEquals(2, cachedTickets.size());

        // 2. Second query (Cache Hit, reads from Redis)
        List<Ticket> tickets2 = ticketService.queryTickets(from, to, date);
        assertNotNull(tickets2);
        assertEquals(2, tickets2.size());
    }

    @Test
    public void testCachePenetrationProtection() {
        String from = "Beijing";
        String to = "Shanghai";
        String date = "2026-06-25"; // No tickets in DB for this date
        String cacheKey = TICKET_QUERY_PREFIX + from + ":" + to + ":" + date;

        // Ensure cache is empty
        redisTemplate.delete(cacheKey);

        // 1. Query for non-existent tickets
        List<Ticket> tickets = ticketService.queryTickets(from, to, date);
        assertNotNull(tickets);
        assertTrue(tickets.isEmpty());

        // 2. Verify empty list is cached to protect against cache penetration
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        assertNotNull(cached);
        assertTrue(cached instanceof List);
        assertTrue(((List<?>) cached).isEmpty());
    }

    @Test
    public void testConcurrentRebuildWithRedissonLock() throws InterruptedException, ExecutionException {
        String from = "Beijing";
        String to = "Shanghai";
        String date = "2026-06-18";
        String cacheKey = TICKET_QUERY_PREFIX + from + ":" + to + ":" + date;

        // Ensure cache is empty
        redisTemplate.delete(cacheKey);

        int threadsCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadsCount);
        List<Callable<List<Ticket>>> tasks = new ArrayList<>();

        for (int i = 0; i < threadsCount; i++) {
            tasks.add(() -> ticketService.queryTickets(from, to, date));
        }

        // Submit all tasks concurrently
        List<Future<List<Ticket>>> futures = executorService.invokeAll(tasks);

        for (Future<List<Ticket>> future : futures) {
            List<Ticket> result = future.get();
            assertNotNull(result);
            assertEquals(2, result.size());
        }

        executorService.shutdown();
        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));

        // Verify it is cached in Redis
        assertNotNull(redisTemplate.opsForValue().get(cacheKey));
    }
}