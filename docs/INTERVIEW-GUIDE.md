# Interview guide — how to explain this project

Use this to practise. Every answer points to real code so you can open it and show it.

## 1. The 60-second pitch
> "I built an enterprise order and inventory platform as Spring Boot microservices. There are five business services —
> auth, product-inventory, order, payment and notification — behind a Spring Cloud API gateway, with a React front end.
> Auth issues RS256 JWTs and publishes its public key as a JWKS, so the gateway and every service can verify tokens
> without sharing a secret. The order service orchestrates a saga: it reserves stock with an atomic SQL update so we
> can never oversell, charges the customer through a payment service that uses the Strategy and Factory patterns,
> and compensates — releases stock or refunds — when a step fails. I used PostgreSQL, MySQL and Oracle with JPA and
> Hibernate, Redis for caching, Docker Compose and Kubernetes for deployment, and a GitHub Actions pipeline that runs
> 46 unit/integration tests plus an end-to-end test before deploying to Render."

## 2. Demo script (5 minutes)
1. Open the live site, log in as `customer`, search the catalogue (pagination, filters → `ProductService.search`).
2. Add two products, place an order → show status **PENDING** and the 30-minute payment deadline.
3. Pay with the declined test card `4000 0000 0000 0002` → payment **FAILED**, order still PENDING.
4. Pay with `4111 1111 1111 1111` → **CONFIRMED**; show the status history and the masked card.
5. Log in as `admin` → dashboard (SQL aggregates), ship and deliver the order, show the stock audit trail.
6. Open Swagger at `/swagger-ui.html` on the gateway → show all five APIs in one place.
7. Show GitHub Actions: parallel tests → e2e → Docker images → deploy.

## 3. Questions you will be asked

**Why microservices instead of one application?**
Each business capability (identity, catalogue, orders, payments, notifications) can be developed, deployed and scaled
independently and owns its own database. The order service is the busiest, so it can run more replicas
(`kubectl scale deployment order-service --replicas=3`) without scaling auth. Trade-off: network calls, distributed
transactions and more operations work — which is why I used a saga and timeouts.

**What does the API gateway do?** (`gateway/`)
One public entry point. It routes `/api/**` to the right service (`RoutesConfig`), validates the JWT before the request
reaches a service (`JwtValidationFilter`), handles CORS once (`CorsConfig`), rate-limits login (`LoginRateLimitFilter`,
HTTP 429), adds an `X-Correlation-Id` for tracing (`CorrelationIdFilter`) and aggregates Swagger. It does **not** route
`/internal/**`, so service-to-service endpoints are not exposed.

**How does authentication work? Why RS256 and not HS256?**
Login (`AuthService.login`) checks the BCrypt hash and returns a JWT signed with an RSA private key (`TokenService`).
The public key is published at `/.well-known/jwks.json` (`JwksController`). Services download it once and cache it
(`JwksKeyProvider`) and verify every token themselves (`JwtAuthenticationFilter`). With HS256 every service would need
the same secret — if one service leaks it, anyone can mint tokens. With RS256 only the auth service can sign.
The key pair is stored in the auth database so tokens stay valid after a restart (`SigningKeyService`).

**How do you do authorization?**
The token carries the role. Spring Security turns it into `ROLE_ADMIN` / `ROLE_CUSTOMER`; admin endpoints use
`@PreAuthorize("hasRole('ADMIN')")`. Ownership is checked in the services, e.g. `OrderService.get` returns 404 when a
customer asks for someone else's order (not 403, so we don't reveal that the order exists).

**How does an order get created? What happens if inventory is insufficient?**
`OrderService.placeOrder`: (1) the inventory service reserves all lines in one transaction
(`InventoryService.reserve`) — the conditional `UPDATE ... WHERE available_quantity >= :qty` returns 0 rows when stock is
short, the transaction rolls back and the API returns **409 Conflict** with "requested 3, available 1";
(2) prices come from the inventory service, never from the browser; (3) the order is saved as PENDING. If saving fails,
the reservation is released (compensation).

**How do you stop two people buying the last item at the same time?**
The check and the decrement happen in the same SQL statement, so the database row lock makes it atomic.
`concurrentReservationsNeverOversell` proves it with 10 threads and 5 units. Products in one order are locked in id
order to avoid deadlocks.

**What is a saga and why not a distributed transaction (2PC)?**
Each service has its own database, so one ACID transaction across them isn't possible (2PC is slow and fragile).
A saga is a sequence of local transactions with compensating actions: reserve stock → charge → commit stock; if the
commit fails after payment we refund and cancel (`OrderService.compensatePaidOrder`). The order service is the
orchestrator. Unpaid orders are cancelled by `OrderExpiryJob`; their stock is released by `ReservationExpiryJob`.

**How do you prevent charging a customer twice?**
Two layers. The order has a `@Version` column: `pay()` first sets `paymentInProgress` and a second concurrent click
fails with an optimistic-locking conflict (409). And the payment service is idempotent per order: if a successful
payment exists it returns that payment instead of charging again (`PaymentService.charge`).

**Explain the Strategy and Factory patterns in payments.**
`PaymentProcessor` is the strategy interface; `CardPaymentProcessor`, `UpiPaymentProcessor` and
`CashOnDeliveryProcessor` are implementations. `PaymentProcessorFactory` receives all of them from Spring and returns
the one for the requested method. Adding Apple Pay = one new class, no change to `PaymentService` (Open/Closed).

**Why three databases?**
To show polyglot persistence and that the JPA layer is vendor-independent: auth uses MySQL, payments Oracle, the rest
PostgreSQL. The vendor is chosen with Spring profiles (`application-mysql.properties`, `application-oracle.properties`)
— the Java code is the same. On Render (which only offers PostgreSQL) everything runs on PostgreSQL with one schema per
service.

**Why Redis? What do you cache and how do you keep it correct?**
Product details and the category list are read far more often than they change. `@Cacheable` on
`ProductService.getById` is cache-aside: first read from PostgreSQL, then from Redis. Every stock or product change
evicts the entry (`ProductCache`), and the cache manager is transaction-aware so eviction happens only after commit.
If Redis is down, `LoggingCacheErrorHandler` logs and the request falls back to the database.

**What JPA/Hibernate features did you use?**
Entity relationships (`Order` 1-N `OrderItem`, `Product` N-1 `Category`, `Inventory` 1-1 `Product` with `@MapsId`),
JPQL with `GROUP BY`/`SUM` (`OrderRepository.topProducts`, `dailySales`), dynamic queries with `Specification`,
pagination, `@Version` optimistic locking, `@EntityGraph`, `default_batch_fetch_size` to avoid the N+1 problem,
indexes via `@Index`, `open-in-view=false`.

**What is the N+1 problem and how did you avoid it?**
Loading 10 orders and then lazily loading items for each would run 1 + 10 queries. I set
`hibernate.default_batch_fetch_size=50`, so items for the whole page load in one extra query; product listings load
stock rows with one batched `findAllById`.

**Transactions — where and why?**
`@Transactional` on service methods that change data. In the saga I deliberately do NOT hold a database transaction
while calling another service (`TransactionTemplate` around the local steps only) so a slow network call can't keep a
DB connection and locks open.

**What happens if one service goes down?**
Calls have connect/read timeouts (`ServiceClientFactory`). If inventory is down, placing an order fails fast with 503
and nothing is half-done. Notifications are fire-and-forget (`@Async`), so the notification service being down never
breaks an order. The gateway returns a clean 503/504 JSON (`GatewayErrorHandler`). Kubernetes restarts unhealthy pods
using the liveness/readiness probes. Next step would be a circuit breaker (Resilience4j) and a message broker.

**How would you scale the order service?**
It is stateless (JWT, no HTTP session), so add replicas behind the Kubernetes Service; the k8s manifest already runs
2 replicas. Duplicate clicks are safe because of optimistic locking and idempotent payments. Then: read replicas for
reporting queries, and Kafka for notifications.

**How did you test?**
46 automated tests. Unit tests with JUnit 5 + Mockito (`OrderServiceTest`, `InventoryServiceTest`,
`PaymentProcessorTest`), Spring Boot integration tests with MockMvc and H2 for every API including security
(`*ApiIntegrationTest`), a concurrency test for overselling, a gateway test with a fake backend, and an end-to-end
script (`scripts/smoke-test.sh`, 30 checks) that GitHub Actions runs against real PostgreSQL and Redis.

**Explain your CI/CD pipeline.** (`.github/workflows/ci.yml`)
On every push: six services are built and tested in parallel, the frontend is linted and built, the e2e job starts all
services against PostgreSQL + Redis and runs the smoke test, Docker images are built and pushed to GitHub Container
Registry, and only if everything is green the gateway and frontend are deployed to Render through deploy hooks.

**Docker and Kubernetes — what did you configure?**
Multi-stage Dockerfiles (Maven build → slim JRE, non-root user, memory flags for 512 MB containers).
`docker-compose.yml` runs 11 containers with health checks and startup order. In `k8s/`: Deployments with startup,
readiness and liveness probes, resource requests/limits, ConfigMap for URLs, Secret for passwords, PVCs for databases,
NodePort services for the UI and gateway, all applied with Kustomize.

**How do you debug a problem across services?**
Every request gets an `X-Correlation-Id` at the gateway; it is forwarded to each service and printed in every log
line (`logging.pattern.level`). Search the logs for that id to follow one request through all services.
Actuator `/actuator/health` and `/actuator/metrics` show service state.

**What would you improve with more time?**
Flyway migrations, Kafka with the outbox pattern for events, Resilience4j circuit breakers, Prometheus + Grafana,
refresh tokens, Testcontainers for MySQL/Oracle tests, and a private network on the cloud provider.

## 4. Spring terms they may ask you to distinguish
* **Spring Framework**: the core (dependency injection, AOP, transactions).
* **Spring MVC**: the web layer — `@RestController`, request mapping, validation, exception handlers.
* **Spring Boot**: auto-configuration, starters, embedded Tomcat, `application.properties`, Actuator.
* **Spring Data JPA**: repositories generated from interfaces (`OrderRepository`).
* **JPA**: the Java standard API (annotations, `EntityManager`). **Hibernate**: the JPA implementation that generates
  the SQL.
* **Spring Security**: filter chain, authentication, authorization (`SecurityConfig`, `@PreAuthorize`).
