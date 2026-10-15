# Agile plan

Nexora was planned and built as Scrum in **six two-week sprints**: a product backlog of user stories grouped into
epics, a sprint goal and a demo at the end of every sprint, and a definition of done that the CI pipeline enforces.
It is a one-person project, so I played product owner, Scrum master and developer, and kept the ceremonies light.

**Product vision**: a marketplace where many independent UAE stores sell to customers in one place, and every store
runs its stock, orders and money in a real inventory management system, overseen by one platform administrator.

**Personas**: Aisha (a customer in Dubai Marina), Yousef (owner of Al Noor Electronics, two warehouses), and the
platform administrator.

## Epics

| Epic | Goal |
|---|---|
| EPIC-1 Accounts and security | customers, stores and one administrator, each with their own sign-in; RS256 JWT |
| EPIC-2 Catalogue and inventory | products, marketplace search, stock per warehouse with a ledger, purchasing, counts |
| EPIC-3 Orders and fulfilment | multi-store checkout, reservations, shipping, cancellations, invoices, reviews, analytics |
| EPIC-4 Payments and finance | card / UPI / cash on delivery, platform fees, refunds, expenses, budgets, profit and loss |
| EPIC-5 Communication | notifications, e-mail outbox, customer-store messages, announcements |
| EPIC-6 Web applications | the marketplace, Seller Central and the separate Control Center |
| EPIC-7 Quality and delivery | tests, Docker, Kubernetes, CI/CD, AWS, Git workflow |

## Sprints and user stories

Points use the Fibonacci scale (1, 2, 3, 5, 8). Every story below is done.

### Sprint 1 — Accounts and security (Auth)
Goal: anyone can sign up as a customer or open a store, and every service trusts the same tokens.

| ID | User story | Points | Epic |
|---|---|---|---|
| NX-101 | As a customer, I want to create an account and sign in, so that I can buy and follow my orders. | 3 | EPIC-1 |
| NX-102 | As a store owner, I want to open a store with my trade licence and VAT number, so that I can sell on Nexora. | 5 | EPIC-1 |
| NX-103 | As a store owner, I want a separate Seller Central sign-in, so that my business account never mixes with shopping. | 2 | EPIC-1 |
| NX-104 | As the platform owner, I want exactly one administrator, created from configuration and signing in on its own page, so that nobody can create administrators through the API. | 5 | EPIC-1 |
| NX-105 | As a developer, I want RS256 tokens with a JWKS endpoint, checked by the gateway and every service, so that no service shares a signing secret. | 8 | EPIC-1 |
| NX-106 | As the administrator, I want every sign-in attempt logged with its IP address and device, so that I can spot password guessing. | 3 | EPIC-1 |

### Sprint 2 — Products and inventory
Goal: a store manages its catalogue and stock like a real inventory system, and customers can find its products.

| ID | User story | Points | Epic |
|---|---|---|---|
| NX-201 | As a store owner, I want products with cost, price and margin, and a switch for whether each is for sale, so that inventory-only items stay private. | 5 | EPIC-2 |
| NX-202 | As a customer, I want to search with filters for department, brand, store, price, rating and stock, so that I find products quickly. | 8 | EPIC-2 |
| NX-203 | As a store owner, I want stock per warehouse with a ledger of every movement, so that I can explain every unit. | 8 | EPIC-2 |
| NX-204 | As a store owner, I want suppliers, purchase orders and goods receipts, so that restocking updates stock and the average cost. | 8 | EPIC-2 |
| NX-205 | As a store owner, I want stock counts and transfers between warehouses, so that the system matches the shelves. | 5 | EPIC-2 |
| NX-206 | As a store owner, I want to upload products from an Excel file, so that I can load my catalogue at once. | 3 | EPIC-2 |
| NX-207 | As a developer, I want product pages cached in Redis, so that the catalogue stays fast under load. | 3 | EPIC-2 |

### Sprint 3 — Orders
Goal: a customer buys from several stores in one checkout and every store fulfils its own order.

| ID | User story | Points | Epic |
|---|---|---|---|
| NX-301 | As a customer, I want one checkout for products of several stores, so that I pay once and each store ships its part. | 8 | EPIC-3 |
| NX-302 | As a customer, I want my items reserved while I pay and never oversold, so that a confirmed order is always fulfilled. | 5 | EPIC-3 |
| NX-303 | As a store owner, I want an order desk to ship with a carrier and tracking number and to mark deliveries, so that customers can follow their parcel. | 5 | EPIC-3 |
| NX-304 | As a customer, I want to cancel an order before it ships, so that I get my money back. | 3 | EPIC-3 |
| NX-305 | As a customer, I want a UAE tax invoice for every order, so that I have proof of purchase with VAT. | 3 | EPIC-3 |
| NX-306 | As a customer, I want to review products I received, so that other shoppers can trust the ratings. | 3 | EPIC-3 |
| NX-307 | As a store owner, I want sales analytics and customer insights, so that I know what sells and who buys. | 5 | EPIC-3 |

### Sprint 4 — Payments
Goal: orders are paid safely with three methods, and stores see their real income and profit.

| ID | User story | Points | Epic |
|---|---|---|---|
| NX-401 | As a customer, I want to pay by card, UPI or cash on delivery, so that I can use the method I trust. | 8 | EPIC-4 |
| NX-402 | As a developer, I want payment methods as strategies and the provider behind a PaymentProcessor interface, so that a real gateway can replace the mock without changing business code. | 5 | EPIC-4 |
| NX-403 | As a customer, I want a declined card to keep my order open for another attempt, so that I do not lose my items. | 3 | EPIC-4 |
| NX-404 | As a store owner, I want to see what I earn after the 5% platform fee, so that I know my real income. | 3 | EPIC-4 |
| NX-405 | As a store owner, I want expenses, monthly budgets with alerts and a profit and loss statement, so that I run the store as a business. | 8 | EPIC-4 |
| NX-406 | As a customer and as a store, I want notifications, e-mails and messages about orders, so that we can talk to each other. | 5 | EPIC-5 |

### Sprint 5 — Testing and Docker
Goal: every rule is covered by automated tests and the whole platform starts with one command.

| ID | User story | Points | Epic |
|---|---|---|---|
| NX-501 | As a developer, I want unit tests with JUnit 5 and Mockito for the business rules, so that changes never break checkout or payments. | 5 | EPIC-7 |
| NX-502 | As a developer, I want MockMvc and Spring Boot integration tests for every API, including security and store isolation. | 5 | EPIC-7 |
| NX-503 | As a developer, I want Testcontainers tests on a real PostgreSQL, so that native queries and locks are tested on the production database. | 3 | EPIC-7 |
| NX-504 | As a developer, I want an end-to-end test through the gateway, so that the services are proven to work together. | 5 | EPIC-7 |
| NX-505 | As a developer, I want every service and web application in a Docker image and the platform in docker compose, so that anyone can run it with one command. | 5 | EPIC-7 |
| NX-506 | As the administrator, I want the Control Center as its own web application, so that it is never served by the marketplace. | 8 | EPIC-6 |

### Sprint 6 — Kubernetes and CI/CD
Goal: the platform runs on Kubernetes and in the cloud, and every change reaches production through the pipeline.

| ID | User story | Points | Epic |
|---|---|---|---|
| NX-601 | As an operator, I want Kubernetes manifests per service with probes, limits and rolling updates, so that the platform heals itself and updates without downtime. | 5 | EPIC-7 |
| NX-602 | As an operator, I want to scale the order service and autoscale it on CPU, so that it handles peak sales. | 3 | EPIC-7 |
| NX-603 | As a developer, I want a pipeline with compile, unit tests, integration tests, code quality, JAR and Docker image stages, so that every change is checked the same way. | 8 | EPIC-7 |
| NX-604 | As a developer, I want deployments only after green builds of main, so that production only gets tested code. | 3 | EPIC-7 |
| NX-605 | As an operator, I want a deployment on AWS (EC2 or EKS), so that the platform runs in the cloud. | 5 | EPIC-7 |
| NX-606 | As a team, I want feature branches merged through pull requests, so that main is always releasable. | 2 | EPIC-7 |

### Velocity

| Sprint | 1 | 2 | 3 | 4 | 5 | 6 |
|---|---|---|---|---|---|---|
| Points done | 26 | 40 | 32 | 32 | 31 | 26 |

Sprint 2 was the largest because the inventory system is the heart of Seller Central; sprint 6 left room for the
documentation and the release.

## Acceptance criteria (examples)

**NX-302 — never oversold**
* Given 5 units in stock, when 10 customers reserve 1 unit each at the same moment, then exactly 5 reservations
  succeed and the others get 409 "Insufficient stock" (`concurrentReservationsNeverOversell`).
* Given a reservation, when the order is not paid within 30 minutes, then the units are available again.

**NX-401 / NX-403 — payments**
* Given a pending order, when the card is declined, then the payment is FAILED, the order stays PENDING and the
  customer is notified; a second attempt with another card confirms it.
* Given a paid order, when the customer cancels before shipping, then the card payment is REFUNDED and the stock
  returns (`scripts/smoke-test.mjs` checks both).

**NX-104 — one administrator**
* Given `ADMIN_PASSWORD` is not set, then no administrator account exists.
* Given the administrator's credentials, when they are used on the marketplace sign-in, then the answer is 401.

## Definition of done
* The code is merged into `develop` through a pull request, and the CI pipeline is green: build, unit and integration
  tests, coverage report, CodeQL, Kubernetes manifests, the end-to-end test and the Docker images.
* New behaviour has tests; errors use the standard JSON body; the endpoint is visible in Swagger.
* No secret is committed; configuration comes from the environment.
* Screens work in light and dark mode and on a phone.

## Ceremonies and board
* **Sprint planning**: pick stories from the top of the backlog up to the last sprint's velocity; write the sprint goal.
* **Daily stand-up** (a written note for a solo project): done yesterday, plan for today, blockers.
* **Sprint review**: demo the increment with the demo data (`scripts/windows/run-local.cmd`).
* **Retrospective**: one thing to keep, one to change (for example: "test against PostgreSQL, not only H2" led to the
  Testcontainers tests).
* **Board** columns: Backlog → Sprint backlog → In progress → In review (pull request) → Done.

The backlog above can be created as GitHub milestones (one per sprint), labels (one per epic) and issues (one per
story, closed because they are done) with the GitHub CLI: `gh auth login`, then `node scripts/agile-board.mjs`.
