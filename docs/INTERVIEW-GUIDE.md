# Interview guide — how to explain Nexora

Use this to practise. Every answer points to real code, so you can open it and show it.

## 1. The 60-second pitch
> "I built Nexora, a multi-store marketplace where every store runs its business on a full inventory management
> system, as six Spring Boot microservices behind an API gateway. A customer can buy from several stores in one
> checkout: the order service splits the cart into one order per store and reserves stock with a single conditional SQL
> update, so nothing is ever oversold. Payment goes through a payment service built with the Strategy and Factory
> patterns behind a PaymentProcessor interface, so the mock provider can be swapped for a real gateway with
> configuration. If any step fails, the saga compensates — it releases stock or refunds. Stores get warehouses, a
> stock ledger, purchase orders, stock counts, an order desk, analytics and a profit and loss statement; one
> administrator runs the marketplace from a separate console. Security is RS256 JWT with JWKS. Accounts are on MySQL,
> payments on Oracle and the rest on PostgreSQL, with Redis caching. It is tested with JUnit 5, Mockito, MockMvc and
> Testcontainers plus a 139-check end-to-end test, packaged with Docker, deployable to Kubernetes and AWS, and every
> push goes through a GitHub Actions pipeline."

## 2. Resume bullets
* Designed and built **Nexora**, a multi-vendor marketplace with a seller inventory management system, as **6 Java 21 /
  Spring Boot 4 microservices** behind an API gateway, with three React web applications (marketplace, Seller
  Central, admin console) and 200+ REST endpoints documented with OpenAPI.
* Implemented a **checkout saga across three services** (orders, inventory, payments) with compensating actions,
  idempotent payment outcomes and **atomic stock reservations** (conditional SQL update) proven by a concurrent test:
  10 parallel buyers for 5 units, exactly 5 succeed.
* Built payments with the **Strategy and Factory patterns** behind a **PaymentProcessor** abstraction (mock provider,
  plug-in point for a real gateway), 5% platform fees, refunds, and a monthly **profit and loss** in `BigDecimal`.
* Secured the platform with **Spring Security and RS256 JWT + JWKS** (no shared secret), role-based access, per-store
  data isolation, a separate administrator sign-in, a login audit log and a shared key for service-to-service calls.
* Used **polyglot persistence** with JPA/Hibernate — **MySQL** (accounts), **Oracle** (payments), **PostgreSQL**
  (catalogue, orders, notifications) — plus **Redis** caching, pessimistic and optimistic locking, and Criteria API
  queries for filtered, paginated reports.
* Wrote **148 JUnit 5 / Mockito / MockMvc / Testcontainers tests** and a **139-check end-to-end test** that CI runs
  against the packaged services on PostgreSQL and Redis.
* Containerised all services with **Docker** (multi-stage, non-root), orchestrated with **Docker Compose** and
  **Kubernetes** (probes, resource limits, rolling updates, HorizontalPodAutoscaler), with an **AWS EKS** overlay and
  an EC2 path.
* Automated delivery with **GitHub Actions**: compile, unit and integration tests, JaCoCo coverage, CodeQL, manifest
  validation, Docker images to GitHub Container Registry, and gated deployments; worked in **Scrum sprints** with
  feature branches and pull requests.

## 3. Demo script (8 minutes)
Start everything with `scripts\windows\run-local.cmd`. The demo has four stores, six customers and five months of
history; every demo password is `Demo-2026`.

1. **Marketplace** (http://localhost:3000): search "earbuds", filter by brand and rating, open a product (rating,
   verified reviews with store replies). Sign in as `aisha`.
2. **Checkout across stores**: add earbuds from Al Noor Electronics and a coffee set from Sahara Home, check out — two
   orders, one per store. Pay with the declined card `4000 0000 0000 0002` (order stays open, notification), then with
   `4111 1111 1111 1111`. Show the tax invoice PDF.
3. **Seller Central** (another browser, `alnoor`): the dashboard, the new order in the order desk, ship it with Aramex
   and a tracking number. Then the inventory: stock per warehouse, the ledger with running balances, a purchase order
   that is partly received, the stock count with its differences, reorder suggestions.
4. **Money**: Payments (after the 5% fee), Profit and loss by month (the first months are a small loss, like a real
   young store), budgets with the over-budget alert, download the P&L as Excel.
5. **Control Center** (http://localhost:3001, `admin` + the password from `.env`): the overview, Ctrl+K to find
   Aisha's order, suspend a store with a reason and show its products vanish from the marketplace, publish an
   announcement, the security log with failed sign-ins.
6. **Engineering**: Swagger at http://localhost:8080/swagger-ui.html; GitHub Actions (the stages, coverage summary,
   images); `kubectl -n enterprise-platform scale deployment order-service --replicas=3` on Docker Desktop.

## 4. Questions you will be asked

### Architecture

**Why microservices instead of one application?**
Each business capability — accounts, catalogue and stock, orders, payments and finance, communication — owns its data
and can be deployed and scaled on its own. The order service is the busiest at peak, so it gets more replicas and an
autoscaler without touching the account service. The price is network calls and no cross-service ACID transactions,
which is why I used a saga, timeouts, idempotent calls and retries.

**How did you choose the service boundaries?**
By who owns the data. Stock and the product's cost belong to the product service; an order with its price and cost
snapshot belongs to the order service; money (payments, fees, expenses, budgets) belongs to the payment service. The
profit and loss needs data from two services, so `ProfitAndLossService` asks the order service for a sales summary
(`/internal/sellers/{id}/sales-summary`) and combines it with its own fees and expenses.

**What does the API gateway do?** (`gateway/`)
It is the only public API. `RoutesConfig` maps `/api/**` to the right service; `JwtValidationFilter` rejects invalid
tokens before they reach a service (`PublicPaths` lists what visitors may browse); `CorsConfig` handles CORS once;
`LoginRateLimitFilter` limits sign-ins (20 a minute, 5 for the administrator, then 429); `CorrelationIdFilter` adds an
`X-Correlation-Id`; Swagger shows all services in one UI. It never routes `/internal/**`. `GatewayIntegrationTest`
starts fake backends to prove where each path goes.

### Security

**How does authentication work? Why RS256 and not HS256?**
`AuthService.login` checks the BCrypt hash and `TokenService` signs a JWT with an RSA private key. The public key is
published at `/.well-known/jwks.json` (`JwksController`); the gateway and every service download and cache it
(`JwksKeyProvider`) and verify each token themselves (`JwtAuthenticationFilter`). With HS256 every service would hold
the signing secret, so one leak lets anyone mint tokens; with RS256 only the account service can sign. The key pair is
stored in the database (`SigningKeyService`), so tokens survive restarts and every replica signs with the same key.

**How are customers, stores and the administrator separated?**
Three roles: `CUSTOMER`, `SELLER`, `ADMIN`. The marketplace sign-in takes an account type, and a customer cannot sign
in as a store or the other way round. The administrator has a different endpoint (`AdminAuthController`,
`/api/admin/auth/login`) and a different web application; the marketplace sign-in answers 401 for the administrator,
exactly as for a wrong password, so it does not even reveal that the account exists. Admin tokens last 2 hours and live
in `sessionStorage`, so closing the tab ends the session.

**Why only one administrator, and where does its password come from?**
`AdminAccountInitializer` creates it at start-up from `ADMIN_PASSWORD`; there is no API to create administrators.
Changing the variable and restarting rotates the password; without it there is no administrator at all. Locally the
run script generates a random password into the git-ignored `.env`; in Kubernetes it comes from a Secret, in CI from
the job, on Render from an environment variable. No default password exists in the code.

**How do you stop one store from seeing another store's data?**
Every Seller Central endpoint takes the store id from the token, never from the request, and every query filters by
it. Asking for another store's product, order or conversation returns **404, not 403**, so we don't reveal that it
exists. `ProductResourceControllerTest` and the integration tests check this; the smoke test does it end to end.

**How do services trust each other?**
Calls between services carry the caller's JWT (the service acts on behalf of the user) and, for `/internal/**`, a
shared key in `X-Internal-Key` (`InternalApiKeyFilter`) — so a stolen user token alone cannot call an internal
endpoint, and the gateway does not route them anyway.

**What happens when the administrator suspends a store?**
`AdminUserService` disables the account (no sign-in, gone from the store directory) and calls the product service,
which hides every product of the store in one update and evicts the cached pages. The reason is kept with the account
and shown in the Control Center; the store sees "This account has been suspended" when it signs in. Reactivating
reverses both.

### Orders, stock and payments

**What happens at checkout with products from several stores?** (`OrderService.placeOrder`)
1. Look up every product in the product service: owning store, live price, cost, whether it can be bought.
2. Group the lines by store and reserve the stock for each store's order.
3. If one store is short, release the reservations already made (compensation) and answer 409.
4. Save all orders in one local transaction with prices and unit costs from the server — never from the browser.

**How do you stop two people buying the last unit?** (`InventoryRepository.reserve`)
The check and the change are one statement:
`update Inventory set available = available - :qty, reserved = reserved + :qty where productId = :id and available >= :qty`.
The database row lock makes it atomic; 0 rows updated means not enough stock (409). `concurrentReservationsNeverOversell`
fires 10 threads at 5 units. Physical stock changes (receiving, counting, transferring) use `SELECT ... FOR UPDATE`
instead, because they change several warehouse rows under one lock — two locking strategies, each where it fits.

**Walk me through a payment.** (`PaymentService.pay`, `OrderService.startPayment` / `applyPaymentOutcome`)
`POST /api/payments` → the payment service asks the order service for a payment intent: the order must be PENDING,
inside its 30-minute window and not already being paid (an `@Version` column turns a double click into 409). The
strategy for the method validates the details, the processor charges, the payment is saved with the 5% fee, and the
outcome goes back to the order service, which commits the reserved stock and confirms the order. If the stock can no
longer be committed, the order is cancelled and the money refunded.

**What is a saga and why not a distributed transaction?**
Each service has its own database, so one ACID transaction across them is impossible (two-phase commit is slow,
blocks, and most modern stacks don't support it). A saga is a series of local transactions with compensating actions:
reserve → charge → commit stock; on failure, release or refund. The order service orchestrates it. Unpaid orders
expire (`OrderExpiryJob`), abandoned reservations are released (`ReservationExpiryJob`), and if the order service is
down after a successful charge, `OrderUpdateRetryJob` keeps reporting the outcome until it is applied.

**How do you avoid charging twice?**
Three layers: one payment attempt per order at a time (`@Version`); the payment service returns the existing
successful payment instead of charging again; and the order service treats a repeated outcome for the same payment as
a replay and returns the same answer. Every retry is safe.

**Strategy, Factory and PaymentProcessor — where and why?**
`PaymentStrategy` has `CardPayment`, `UpiPayment` and `CashOnDelivery`: each validates its own details and decides
how to charge (cash on delivery is authorised now and collected at delivery). `PaymentStrategyFactory` picks the
strategy for the method. The provider is a separate abstraction, `PaymentProcessor`: `MockPaymentProcessor` today,
`FutureRealPaymentProcessor` as the plug-in point for Network International or Checkout.com, chosen by
`PaymentProcessorFactory` from `app.payments.provider`. Adding Apple Pay is one new strategy class; switching
providers is configuration — Open/Closed in practice.

**What happens when an order is cancelled?** (`OrderService.cancel`)
Customers can cancel before shipping, stores their own orders, the administrator any order (prefixed "Nexora support").
A card payment is refunded through the payment service's internal endpoint, cash on delivery is voided, the stock goes
back, and the status history records who, when and why. Delivered orders answer 409.

### Inventory management

**What makes Seller Central an inventory system and not just a product list?**
Stock per warehouse with bin locations; every change is a ledger row (`StockLedger`, `StockMovement`) with the
running balance after it; purchase orders from suppliers received in one or several goods receipts; stock counts that
post the differences; transfers between warehouses; reorder suggestions from the average daily sales and days of
cover (`ReplenishmentService`); document numbers per store (`DocumentNumberService`); an Excel bulk upload that
validates every row first and is all-or-nothing (`ProductImportService`).

**How do you value stock?**
Weighted average cost: receiving 7 units at 1,700 onto 3 units at 1,800 gives (3 × 1,800 + 7 × 1,700) / 10 = 1,730
(`Product`). Each order line stores the unit cost at the moment of sale, so later cost changes never rewrite the
profit of past sales — the same reason banks store the exchange rate on a transaction.

**Which warehouse ships an order?**
`WarehouseAllocationStrategy` decides; `DefaultWarehouseFirstAllocation` takes the default warehouse first, then the
others. A nearest-warehouse or first-expiry-first-out strategy would plug in without touching the ledger.

### Money

**How is profit and loss calculated?** (`ProfitAndLossService`)
Revenue − cost of goods sold = gross profit; − platform fees − operating expenses = net profit, per month. Stock
purchases are not expenses; they reach profit as cost of goods when the units sell. Money is `BigDecimal` with two
decimals and `HALF_UP` everywhere — never `double`. Prices include 5% UAE VAT, and the tax invoice shows the VAT part.

**How do budgets and alerts work?**
A store sets a monthly limit per expense category. Saving an expense compares spending before and after; crossing 80%
or 100% publishes a `BudgetAlertEvent`, handled by `BudgetAlertListener` with `@TransactionalEventListener(AFTER_COMMIT)`
— a rolled-back expense never sends an alert.

### Communication

**How are e-mails sent reliably?** (`EmailOutbox`)
The e-mail is saved in the database in the same transaction as the event; after commit it is sent, and a scheduled job
retries failures with a counter and the last error. Nothing is lost if the mail server is down, and nothing is sent for
a rolled-back transaction — the outbox pattern. The Control Center shows the outbox.

**Why a pessimistic lock in messaging when orders use optimistic locking?**
Both sides of a conversation update the same row (unread counters, last message) at the same moment. With optimistic
locking one would fail — bad for a chat — so `ConversationRepository` uses `PESSIMISTIC_WRITE` and they wait a few
milliseconds. For payments a fast failure is what we want, so orders use `@Version`.

### Data

**Why three databases?** Polyglot persistence, and proof that the JPA layer is vendor-independent: accounts on MySQL,
payments on Oracle, the rest on PostgreSQL, chosen with Spring profiles; the Java code is identical. Each service has its
own schema; on Render everything runs on one PostgreSQL with a schema per service.

**How do you keep the Redis cache correct?** `@Cacheable` product pages and the home page (cache-aside). Every change
evicts the entry after commit (`ProductCache`); entries also expire (10 minutes, home page 60 seconds). If Redis is
down, `LoggingCacheErrorHandler` logs it and the database answers.

**Which JPA / Hibernate features did you use?** Relationships, JPQL aggregates (`GROUP BY`, `SUM`), the Criteria API
for the filtered lists (`SellerProductQuery`; Spring Data `Specification`s in `OrderFilter` and `PaymentFilter`), pagination, `@Version` and
`PESSIMISTIC_WRITE`, conditional bulk updates, unique constraints and indexes, `default_batch_fetch_size` against N+1,
and `open-in-view=false`.

**Transactions — where, and where deliberately not?** `@Transactional` on service methods that change data, but never
around a call to another service: the saga uses `TransactionTemplate` for the local steps only, so a slow network call
never holds a database connection and its locks.

### Errors and logging

**How do you handle errors?** Every service has a `GlobalExceptionHandler` that turns exceptions into one JSON shape
(`ApiError`: status, error, message, path, correlationId, fieldErrors). Validation errors are 400 with a message per
field, business conflicts 409 (insufficient stock, duplicate SKU, an order that can't be cancelled), other stores' data
404, and unexpected errors 500 without a stack trace. Calls to other services have timeouts and become a clean 503.

**How do you debug a problem across services?** The gateway gives every request an `X-Correlation-Id`, every service
puts it in the MDC so it is on every log line, and it is in every error body. Search the logs for that id.

### Testing and delivery

**How did you test?** 148 JUnit tests: unit tests with Mockito (`OrderServiceTest`: checkout split, compensation,
payment outcomes; `PaymentStrategyTest`; `InventoryServiceTest`), MockMvc web tests (`ProductResourceControllerTest`),
Spring Boot integration tests for every API including security and store isolation, Testcontainers tests on a real
PostgreSQL, the concurrency test, and `scripts/smoke-test.mjs` — 139 checks through the gateway on the real services.

**Explain your CI/CD pipeline.** On every push and pull request: each Java service compiles, runs unit tests, then
integration tests (with JaCoCo coverage in the job summary), and packages its JAR; both web apps are linted,
format-checked and built; CodeQL scans the code; the Kubernetes manifests are rendered and validated with kubeconform;
the end-to-end test runs the packaged JARs against PostgreSQL and Redis; then the eight Docker images are built and,
on `main`, pushed to GitHub Container Registry. `cd.yml` deploys only after CI passes on `main`.

**How is the live demo hosted, and what was hard about it?** On Render: the gateway and the five services as Docker
web services, one PostgreSQL (a schema per service), Key Value for the cache, and the two web apps as static sites.
Free instances sleep after 15 minutes without visitors. The live demo failed with 502s although every deploy was
green; the logs showed the services were never started again, and the response header `x-render-routing: no-deploy`
pointed to the cause: only a request from outside Render wakes a sleeping instance, so the gateway's calls could
never wake a service. I proved it by waking two services from a browser while the gateway kept calling all four:
only those two started. The fix is on the client side: while the marketplace or the Control Center is open and
visible, the browser pings every service directly every four minutes (`VITE_WAKE_URLS`), the API client retries
for up to three minutes while showing "Waking Nexora up", the gateway's `/docs` page wakes the services before it
opens Swagger UI, and the demo loader does the same. On a paid plan the instances never sleep and none of this runs.

**Docker and Kubernetes — what did you configure?** Multi-stage Dockerfiles (Maven build → slim JRE, non-root user,
memory flags). Compose runs 12 containers with health checks and start-up order. In `k8s/`: a namespace, a ConfigMap,
a Secret generated by kustomize from a git-ignored file, PVCs for the databases, and per service a Deployment (startup,
readiness and liveness probes, requests and limits, zero-downtime rolling updates) and a Service; an HPA scales
`order-service` from 2 to 5 pods at 70% CPU. `deploy/aws` reuses all of it for EKS.

**What happens if a service goes down?** Its health check fails and Kubernetes replaces the pod; callers time out
quickly and answer 503 instead of hanging; checkout fails before anything is half-done; payment outcomes are retried;
e-mails wait in the outbox. Next steps would be Resilience4j circuit breakers and a message broker.

### Front end

**What did you build on the front end?** Three React 19 applications: the marketplace and Seller Central (one app,
lazy-loaded so shoppers never download the Seller Central code) and the Control Center as a separate app with its own
nginx container, `noindex` and `X-Frame-Options: DENY`. Filters live in the URL so every view can be bookmarked and
shared, charts use a colour-blind-safe palette with legends and tooltips, everything has a dark mode and works on a
phone.

**What would you improve with more time?** Kafka with the outbox pattern for events, Resilience4j, Flyway migrations,
OpenTelemetry tracing with Prometheus and Grafana, refresh tokens and MFA for the administrator, a real payment gateway
behind `PaymentProcessor`, S3 for product images, and managed databases (RDS) in production.

## 5. Banking-relevant points to mention
* **Money**: `BigDecimal`, scale 2, `HALF_UP`, never floating point; totals computed on the server only.
* **Idempotency**: a payment is never charged twice, and every retry is safe.
* **Audit trails**: order status history (who, when, why), the stock ledger, payment records with masked
  instruments, the sign-in log with IP addresses.
* **Reconciliation**: every payment carries amount, fee and net, and can be matched to its order; refunds and voids
  are explicit states.
* **PCI-DSS awareness**: card numbers and CVVs are never stored.
* **Least privilege**: roles, per-store data filtering, a separate administrator sign-in, internal APIs behind a key.

## 6. Spring terms they may ask you to distinguish
* **Spring Framework**: dependency injection, AOP, transactions. **Spring Boot**: auto-configuration, starters,
  embedded Tomcat, Actuator. **Spring MVC**: `@RestController`, validation, exception handlers.
* **JPA** is the standard API (annotations, `EntityManager`); **Hibernate** implements it and generates the SQL;
  **Spring Data JPA** generates repositories from interfaces.
* **Spring Security**: the filter chain, authentication (who you are) and authorization (what you may do) —
  `SecurityConfig` URL rules plus `@PreAuthorize` on methods.
