# Project: PTU-Ticket High Concurrency Railway Ticketing System

## Architecture
- **Monolithic Spring Boot Application**: Zero microservice components (no Spring Cloud, Nacos, Feign, etc.).
- **Data Flow**:
  - GET `/api/tickets/query` -> Controller -> Service -> Redis (Cache-Aside) -> Redisson Lock (on miss) -> MySQL.
  - POST `/api/tickets/order` -> Controller -> Validation Chain (Chain of Responsibility) -> Ticket Pricing (Strategy) -> Redis Lua Stock Deduction -> RocketMQ -> Async Consumer -> MySQL Order Persist (Idempotence via Unique Constraint).
- **Core Package Layout**:
  - `com.ptu.ticket.controller`: Web interface layer.
  - `com.ptu.ticket.service`: Core business logic services.
  - `com.ptu.ticket.entity`: MyBatis-Plus entity classes.
  - `com.ptu.ticket.mapper`: MyBatis-Plus mapper interfaces.
  - `com.ptu.ticket.mq`: RocketMQ message producers and consumers.
  - `com.ptu.ticket.pattern.chain`: Chain of Responsibility validator.
  - `com.ptu.ticket.pattern.strategy`: Strategy-based pricing system.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|--------------|--------|
| M1 | Project Init & Git Setup | Git repository init, bind remote, verify Docker-based MySQL/Redis/RocketMQ connectivity, commit & push | None | DONE |
| M2 | Ticket Query (Cache-Aside) | Remaining tickets GET endpoint, Cache-Aside logic, Redisson lock for cache rebuild (Conv: 1de06cea-34ed-4fde-abde-d68a0468fc14) | M1 | IN_PROGRESS |
| M3 | Order Placement (Lua + MQ) | Order placement POST endpoint, Redis Lua script for stock deduction, RocketMQ producer/consumer, DB async insertion | M2 | PLANNED |
| M4 | Idempotency & Optimization | MySQL unique constraint validation, joint/covering index optimization, Chain of Responsibility validation, Strategy pricing | M3 | PLANNED |
| M5 | E2E Integration & Audit | Pass unit/integration tests and python concurrent stress test, Forensic Auditor CLEAN status | M4, E2E | PLANNED |
| E2E | E2E Testing Track | Define test infra, create Tiers 1-4 tests, write TEST_INFRA.md, publish TEST_READY.md | M1 | PLANNED |

## Interface Contracts
### Ticket Query Interface
- **Endpoint**: `GET /api/tickets/query`
- **Params**: `fromStation` (String), `toStation` (String), `date` (String, YYYY-MM-DD)
- **Response**: JSON array of tickets.
  - Structure: `[{"id": 1, "fromStation": "A", "toStation": "B", "date": "2026-06-18", "price": 100.0, "stock": 50, "type": "ADULT"}]`

### Ticket Order Interface
- **Endpoint**: `POST /api/tickets/order`
- **Request Body**:
  ```json
  {
    "userId": 1001,
    "ticketId": 1,
    "passengerType": "ADULT"
  }
  ```
- **Response**:
  ```json
  {
    "success": true,
    "orderNo": "ORD20260617150800123",
    "message": "Order is processing"
  }
  ```



