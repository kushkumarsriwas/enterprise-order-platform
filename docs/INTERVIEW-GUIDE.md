# Interview guide — how to explain this project

Use this to practise. Every answer points to real code, so you can open it and show it.

## 1. The 60-second pitch
> "I built Nexora Commerce, a multi-vendor marketplace with a back office for every merchant, as Spring Boot
> microservices. Customers shop from many stores in one cart; at checkout the order service splits the cart into one
> order per store, reserves stock with an atomic SQL update so nothing is ever oversold, and charges the customer
> through a payment service built with the Strategy and Factory patterns. If any step fails it compensates: releases
> stock or refunds. Merchants get inventory management with weighted average cost, order fulfilment with courier
> tracking, analytics, expenses, monthly budgets, a profit and loss statement and PDF/Excel reports. Security is RS256
> JWT with a JWKS endpoint, so the gateway and every service verify tokens without a shared secret, and every merchant
> only ever sees their own data. I used PostgreSQL, MySQL and Oracle with JPA/Hibernate, Redis for caching, Docker,
> Kubernetes, and a GitHub Actions pipeline that runs 64 automated tests and a 92-step end-to-end test before
> deploying to Render."

## 2. Demo script (7 minutes)
1. **Merchant**: sign up on the Merchant tab (store name, city, trade licence). Add a product with cost 420 and price
   699 — the form shows the 39.9% margin live. Add a second product with "List on marketplace" switched off: it is
   inventory only.
2. **Inventory**: restock 8 units at a new cost and show the weighted average cost change, then the Stock movements tab
   (audit trail) and the Purchases tab.
3. **Customer** (another browser or a private window): search the marketplace, open the product, add 2 to the cart,
   check out, pay with the declined card `4000 0000 0000 0002` (order stays unpaid), then with `4111 1111 1111 1111`.
   Use "Contact seller" on the order.
4. **Merchant**: the dashboard now shows revenue and gross profit; reply to the message; open the order, ship it with
   Aramex and a tracking number, mark it delivered. The customer sees the tracking steps and can review the product.
5. **Finance**: add a marketing expense and a budget (alert at 80%), show the profit and loss statement and the
   payments received after the 5% platform fee; download the P&L as PDF and Excel from Reports.
6. **Admin**: sign in as `admin` — GMV, platform commission, users; suspend a store and show it disappears.
7. **Engineering**: Swagger on the gateway (`/swagger-ui.html`) shows every API; GitHub Actions shows parallel tests →
   e2e → Docker images → deploy.

## 3. Questions you will be asked

### Architecture

**Why microservices instead of one application?**
Each business capability — accounts, catalogue and stock, orders, payments and finance, notifications and messages —
is developed, deployed and scaled independently and owns its own database. The order service is the busiest, so it
can run more replicas (`kubectl scale deployment order-service --replicas=3`) without scaling the account service.
Trade-off: network calls, no cross-service ACID transactions and more operations work — which is why I used a saga,
timeouts and idempotency.

**How did you choose the service boundaries?**
By who owns the data. Stock and the product's cost belong to the inventory service; an order and its price/cost
snapshot belong to the order service; money (payments, fees, expenses, budgets) belongs to the payment and finance
service; messages live with notifications because both are "communication". The profit and loss statement needs data
from two services, so the finance service asks the order service for a monthly sales summary
(`/internal/merchants/{id}/sales-summary`) and combines it with its own fees and expenses (`ProfitAndLossService`).

**What does the API gateway do?** (`gateway/`)
One public entry point. It routes `/api/**` to the right service (`RoutesConfig` — the merchant console alone is spread
over three services), validates the JWT before the request reaches a service (`JwtValidationFilter`, with
`PublicPaths` for the marketplace pages visitors can browse), handles CORS once (`CorsConfig`), rate-limits login
(`LoginRateLimitFilter`, HTTP 429), adds an `X-Correlation-Id` (`CorrelationIdFilter`) and aggregates Swagger. It never
routes `/internal/**`. `GatewayIntegrationTest` starts one fake backend per service to prove where each path goes.

### Security

**How does authentication work? Why RS256 and not HS256?**
`AuthService.login` checks the BCrypt hash and returns a JWT signed with an RSA private key (`TokenService`). The public
key is published at `/.well-known/jwks.json` (`JwksController`). The gateway and every service download it, cache it
(`JwksKeyProvider`) and verify each token themselves (`JwtAuthenticationFilter`). With HS256 every service would need
the same secret — one leak and anyone can mint tokens. With RS256 only the account service can sign. The key pair is
stored in the database so tokens survive a restart (`SigningKeyService`).

**Customers and merchants — how are they separated?**
Three roles: `CUSTOMER`, `MERCHANT`, `ADMIN`. Registration takes an `accountType`; a merchant must give a store name
(unique), phone and city, stored in `MerchantProfile` (one-to-one with `User` via `@MapsId`). Login has two portals: the
customer portal rejects business accounts and the merchant portal rejects customers (`AuthService.login`). The token
carries the role and the store name, so services know who is calling without another lookup.

**How do you stop one merchant from seeing another merchant's data?**
Every merchant endpoint takes the merchant id **from the token, never from the request**, and every query filters by
it: `findByIdAndMerchantId`, `findByMerchantId`... Asking for another store's order or product returns **404, not
403**, so we don't even reveal that it exists. Integration tests check this for products, orders, expenses, budgets,
payments and conversations (e.g. `merchantsOnlySeeAndChangeTheirOwnInventory`).

**What happens when an admin suspends a store?**
The account service disables the account (no more sign-in, hidden from the store directory) in its own transaction,
then calls the inventory service's internal endpoint, which hides every product of that store with one `UPDATE`
(`merchant_active = false`) and evicts the cached product pages (`MerchantStatusController`). The products are no longer
searchable, viewable or purchasable. If the inventory service is down, the admin gets a 503 and simply retries — the
call is idempotent.

**What can a customer never see?**
The cost price (`ProductResponse` has no cost field; `MerchantProductResponse` has), inventory-only products (the
public search only returns listed products of active stores; their product page is 404), the platform fee on their
payment (`PaymentResponse` vs `MerchantPaymentResponse`), and other customers' orders.

### Orders, stock and payments

**What happens at checkout with products from several stores?** (`OrderService.placeOrder`)
1. Look up every product in the inventory service: owning store, live price, whether it can be bought.
2. Group the lines by store and reserve stock **per store** (each store will ship its own parcel).
3. If any store is out of stock, release the reservations already made — compensation — and return 409.
4. Save all orders in one local transaction, with prices **and unit costs** from the server, never from the browser.
The customer then pays every order in one step (`POST /api/orders/pay` → `OrderService.payMany`).

**How do you stop two people buying the last item at the same time?**
The check and the decrement are one SQL statement:
`UPDATE stock_levels SET available = available - :qty, reserved = reserved + :qty WHERE product_id = :id AND available >= :qty`.
The row lock makes it atomic; 0 rows updated means "not enough stock". `concurrentReservationsNeverOversell` proves it
with 10 threads and 5 units. Lines are locked in product-id order so two orders can't deadlock.

**What is a saga and why not a distributed transaction (2PC)?**
Each service has its own database, so one ACID transaction across them isn't possible (2PC is slow, blocks, and most
modern stores don't support it). A saga is a sequence of local transactions with compensating actions: reserve stock
→ charge → commit stock; if the commit fails after a successful payment, we refund and cancel
(`OrderService.compensatePaidOrder`). The order service is the orchestrator. Unpaid orders are cancelled by
`OrderExpiryJob`, and abandoned reservations are released by `ReservationExpiryJob`.

**How do you prevent charging a customer twice?**
Two layers. The order has a `@Version` column; `pay()` first sets `paymentInProgress`, so a second concurrent click
fails with an optimistic-locking conflict (409). And the payment service is idempotent per order: if a successful
payment already exists it returns it instead of charging again (`PaymentService.charge`).

**Strategy and Factory in payments?**
`PaymentProcessor` is the strategy interface; `CardPaymentProcessor`, `UpiPaymentProcessor` and
`CashOnDeliveryProcessor` implement it. `PaymentProcessorFactory` receives all of them from Spring and returns the one
for the requested method. Adding Apple Pay or Tabby = one new class, no change to `PaymentService` (Open/Closed).

**How does fulfilment work?**
The merchant ships a paid order with a courier and tracking number (`OrderService.ship`), then marks it delivered
(`deliver`) — for cash on delivery this also captures the payment. A merchant can cancel before shipping: the customer
is refunded and the stock returns to the shelf. Every change is in `OrderStatusHistory` (who, when, note) and the
customer is notified.

### Inventory and accounting

**How do you value stock?** (`InventoryService.restock`)
Weighted average cost. Restocking 7 units at 1,700 onto 3 units at 1,800 gives (3 × 1,800 + 7 × 1,700) / 10 = 1,730.
Each restock is saved as a `StockPurchase` (supplier, invoice number) and a `StockMovement`, so the stock has a full
audit trail: opening stock, restocks, counts, reservations, sales, releases.

**Why does an order line store the unit cost?**
Profit must not change when the merchant later changes the cost or the price. The unit cost at the moment of sale is
copied into `OrderItem.unitCost`, so cost of goods sold and margins in the analytics, the P&L and the sales report are
historically correct — the same reason banks store the exchange rate on a transaction.

**How is profit and loss calculated?** (`ProfitAndLossService`)
Revenue (paid and cash-on-delivery orders) − cost of goods sold = gross profit; − platform fees (5% recorded on each
payment) − operating expenses = net profit, per month. Stock purchases are not expenses; they become cost of goods when
the items sell. Money is `BigDecimal` with two decimals and `HALF_UP` rounding everywhere — never `double`.

**How do budgets and alerts work?** (`ExpenseService`, `BudgetService`)
A merchant sets a monthly limit per expense category (unique per month and category in the database). When an expense
is saved, the service compares the category's spending before and after it; crossing 80% or 100% publishes a
`BudgetAlertEvent`. `BudgetAlertListener` is a `@TransactionalEventListener(AFTER_COMMIT)`, so a rolled-back expense
never sends an alert, and the notification is sent asynchronously.

**How do you generate PDF and Excel reports?**
Each report is built once as a small model (`Report`: summary figures + tables). `ExcelReportWriter` writes it with
Apache POI (a summary sheet and one sheet per table, number formats for money). `PdfReportWriter` is a small
dependency-free PDF writer: headers repeated on every page, page numbers, automatic landscape for wide tables, numbers
never truncated. The browser downloads them with the JWT (`download()` in `frontend/src/api.js`).

### Messaging and notifications

**How does "Contact seller" work?** (`MessagingService`)
One conversation per customer and store (unique constraint). Before starting one, the service checks the store with
the account service's public directory (`StoreDirectoryClient`) — outside the database transaction — so a customer can
only message a real, active store and the store name comes from a trusted source. Each side has its own unread counter.

**Why a pessimistic lock there, when orders use optimistic locking?**
When both sides send a message at the same moment, both update the same conversation row (counters, last message). With
optimistic locking one of them would fail with a version conflict — bad for a chat. `SELECT ... FOR UPDATE`
(`@Lock(PESSIMISTIC_WRITE)`) makes them wait a few milliseconds and apply one after the other. For payments a fast
failure is what we want, so orders use `@Version`.

**Why don't notifications ever break an order?**
They are fire-and-forget: `@Async` on another thread, errors logged and ignored, and published after commit. A new
message only notifies the receiver when their unread count goes from 0 to 1, so a burst of messages is one notification.

### Data

**Why three databases?**
Polyglot persistence and proof that the JPA layer is vendor-independent: accounts on MySQL, payments and finance on
Oracle, the rest on PostgreSQL. The vendor is chosen with Spring profiles; the Java code is identical. On Render
(PostgreSQL only) everything runs on PostgreSQL with one schema per service.

**A bug you found and fixed?**
Expense search found nothing in the Oracle-compatible test database. Hibernate generates `LIKE ? ESCAPE ''`, and in
Oracle an empty string is `NULL`, so the whole condition became unknown. I switched to an explicit escape character
and escape `%` and `_` in the user's text, which also stops "50%" from acting as a wildcard (`ExpenseService.list`).

**Why Redis? How do you keep the cache correct?**
Product pages and the category list are read far more often than they change. `@Cacheable` on `ProductService.getById`
is cache-aside. Every stock or product change evicts the entry (`ProductCache`); the cache manager is transaction-aware,
so eviction happens only after commit. If Redis is down, `LoggingCacheErrorHandler` logs and we read the database.

**What JPA/Hibernate features did you use?**
Relationships (`Order` 1-N `OrderItem`, `MerchantProfile` 1-1 `User` with `@MapsId`, `Message` N-1 `Conversation`),
JPQL aggregates with `GROUP BY`, `SUM`, `year()`/`month()` (daily and monthly sales, fees per month, expenses per
category), dynamic filters with `Specification`, pagination, `@Version` and `PESSIMISTIC_WRITE` locking, unique
constraints, indexes, `default_batch_fetch_size` against N+1, `open-in-view=false`.

**What is the N+1 problem and how did you avoid it?**
Loading 10 orders and then lazily loading items for each runs 1 + 10 queries. With
`hibernate.default_batch_fetch_size=50` the items of the whole page load in one extra query.

**Transactions — where, and where deliberately not?**
`@Transactional` on service methods that change data. I never hold a database transaction open while calling another
service: the saga uses `TransactionTemplate` around the local steps only, the P&L calls the order service before
touching its own database, and the messaging service checks the store before its transaction starts. A slow network
call can't keep a connection and locks open.

### Operations

**What happens if one service goes down?**
Every call has connect/read timeouts (`ServiceClientFactory`) and network errors become a clean 503 ("Order service is
not reachable right now"). If inventory is down, checkout fails fast and nothing is half-done. Notifications can't
break anything. Kubernetes restarts unhealthy pods using liveness/readiness probes. Next: Resilience4j circuit
breakers and a message broker.

**How would you scale it?**
Services are stateless (JWT, no HTTP session), so add replicas behind the Kubernetes Service. Duplicate requests are
safe because of optimistic locking and idempotent payments. Then: read replicas for analytics, Kafka for events.

**How did you test?**
64 automated tests: unit tests with JUnit 5 + Mockito (`OrderServiceTest` — checkout split, compensation, payments,
fulfilment; `InventoryServiceTest` — reservations, weighted average cost), Spring Boot integration tests with MockMvc
and H2 for every API including security and data isolation, a concurrency test for overselling, a gateway routing
test, and `scripts/smoke-test.sh` (92 checks: two stores, multi-store checkout, declined and successful payments,
fulfilment, cash-on-delivery cancellation, reviews, messages, restocking, budgets, fees, P&L, reports, suspension)
that GitHub Actions runs against real PostgreSQL and Redis. I also drove the whole UI in a headless browser.

**Explain your CI/CD pipeline.** (`.github/workflows/ci.yml`)
On every push: six services are built and tested in parallel, the frontend is linted and built, the e2e job starts all
services against PostgreSQL + Redis and runs the smoke test, Docker images are pushed to GitHub Container Registry,
and only if everything is green the gateway and frontend are deployed to Render through deploy hooks.

**Docker and Kubernetes — what did you configure?**
Multi-stage Dockerfiles (Maven build → slim JRE, non-root user, memory flags for 512 MB containers).
`docker-compose.yml` runs 11 containers with health checks and startup order. In `k8s/`: Deployments with startup,
readiness and liveness probes, resource requests/limits, ConfigMap for URLs, Secret for passwords, PVCs for databases,
NodePort services for the UI and gateway, applied with Kustomize.

**How do you debug a problem across services?**
Every request gets an `X-Correlation-Id` at the gateway; it is forwarded to each service and printed in every log line.
Search the logs for that id to follow one request through all services.

### Front end

**What did you do on the front end?**
React 19 with Vite. Two experiences in one app: a storefront for customers and visitors (browsing needs no account) and
a back office for merchants and admins. The consoles are lazy-loaded with `React.lazy`, so shoppers don't download the
chart library (the storefront bundle is about 95 KB gzipped). Charts use one colour-blind-safe palette, have a legend
and tooltips, and every chart can switch to a table view for accessibility.

**What would you improve with more time?**
Flyway migrations, Kafka with the outbox pattern, Resilience4j, Prometheus + Grafana, refresh tokens, Testcontainers for
MySQL/Oracle, 5% UAE VAT on invoices, merchant payouts (settlement batches), product images upload to object storage,
and a private network on the cloud provider.

## 4. Banking-relevant points to mention
* **Money**: `BigDecimal`, scale 2, `HALF_UP`, never floating point; totals computed on the server only.
* **Idempotency**: a payment is never charged twice for the same order.
* **Audit trails**: order status history (who/when/why), stock movements, payment records with masked instruments.
* **Reconciliation**: payments (amount, fee, net) can be matched against orders; refunds and voids are explicit states.
* **PCI-DSS awareness**: card numbers and CVVs are never stored.
* **Least privilege and isolation**: role checks plus per-merchant data filtering; internal APIs not exposed.

## 5. Spring terms they may ask you to distinguish
* **Spring Framework**: the core (dependency injection, AOP, transactions).
* **Spring MVC**: the web layer — `@RestController`, request mapping, validation, exception handlers.
* **Spring Boot**: auto-configuration, starters, embedded Tomcat, `application.properties`, Actuator.
* **Spring Data JPA**: repositories generated from interfaces (`OrderRepository`).
* **JPA**: the Java standard API (annotations, `EntityManager`). **Hibernate**: the JPA implementation that generates SQL.
* **Spring Security**: filter chain, authentication, authorization (`SecurityConfig`, `@PreAuthorize`).
