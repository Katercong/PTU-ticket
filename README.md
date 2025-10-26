# PTU-Ticket High Concurrency Ticketing System

This project is a monolithic Spring Boot application simulating a high-concurrency railway ticketing system.

## Infrastructure Prerequisites
The application relies on MySQL, Redis, and RocketMQ services.

### 1. MySQL (Port 3306)
MySQL should be running and accessible on port 3306.
Configuration:
- URL: `jdbc:mysql://127.0.0.1:3306/ptu_ticket`
- Username: `root`
- Password: Defaults to `123456` (overridden via `MYSQL_PWD` environment variable)

### 2. Redis (Port 6379)
Redis should be running natively or via docker, listening on port 6379.

### 3. RocketMQ (Port 9876, 10911)
RocketMQ is run via Docker Compose.

To start RocketMQ:
```bash
docker compose up -d
```
This starts:
- Nameserver (`rmqnamesrv`) listening on port `9876`
- Broker (`rmqbroker`) listening on port `10911` with `brokerIP1` advertised as `127.0.0.1` so that the local Java application can connect to the broker correctly.

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
