# PTU-Ticket High Concurrency Ticketing System

This project is a monolithic Spring Boot application simulating a high-concurrency railway ticketing system.

## Infrastructure Prerequisites
The application relies on MySQL, Redis, and RocketMQ services. All services are verified to be fully up and responsive:

### 1. MySQL (Port 3306)
MySQL is running natively on the host, listening on port 3306.
Connectivity status: **Active & Responsive** (TcpTestSucceeded: True)

### 2. Redis (Port 6379)
Redis is running natively on the host, listening on port 6379.
Connectivity status: **Active & Responsive** (TcpTestSucceeded: True)

### 3. RocketMQ (Port 9876, 10911)
RocketMQ is run via Docker Compose using the stable version `4.9.4` (for compatibility with Spring Boot RocketMQ Starter 2.3.0).

To start RocketMQ:
```bash
docker compose up -d
```
This starts:
- Nameserver (`rmqnamesrv`) listening on port `9876`. Connectivity status: **Active & Responsive**
- Broker (`rmqbroker`) listening on port `10911`. Connectivity status: **Active & Responsive** (configured with `brokerIP1 = 127.0.0.1` so that the local Spring Boot application can connect correctly).

Both Namesrv and Broker allocate 256MB memory locally to ensure smooth operations.

## Compilation and Build
The application uses Java 17 and Maven.

To compile:
```bash
mvn clean compile
```

To run:
```bash
mvn spring-boot:run
```
