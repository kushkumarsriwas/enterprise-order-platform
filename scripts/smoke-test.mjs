#!/usr/bin/env node
// End-to-end test of Nexora through the API gateway, used by CI and after every local start.
// Two stores list products with the /api/products REST resource, a customer buys from both in one checkout
// (one order per store), pays, gets a delivery, cancels the other order (refund), reviews and messages a store,
// and the administrator signs in on the separate console sign-in, suspends a store and brings it back.
// Every service takes part: auth, product + inventory, order, payment, notification and the gateway.
//
// Usage:  node scripts/smoke-test.mjs [gateway-url]          (Node 18+, nothing to install; default http://localhost:8080)
//         ADMIN_PASSWORD=... node scripts/smoke-test.mjs     (adds the administrator checks and suspends the test accounts)
//         KEEP_TEST_DATA=1 node scripts/smoke-test.mjs       (keeps the test products and accounts)
// Exit code 0 when every check passes, 1 at the first failure.

const GATEWAY = (process.argv[2] || process.env.GATEWAY_URL || "http://localhost:8080").replace(/\/$/, "");
const ADMIN_USERNAME = process.env.ADMIN_USERNAME || "admin";
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || "";
const KEEP_TEST_DATA = process.env.KEEP_TEST_DATA === "1";
const PASSWORD = "Smoke-Test-2026";
const GOOD_CARD = { method: "CARD", cardNumber: "4111 1111 1111 1111", expiry: "12/29", cvv: "123" };
const DECLINED_CARD = { method: "CARD", cardNumber: "4000 0000 0000 0002", expiry: "12/29", cvv: "123" };

const started = Date.now();
let passed = 0;
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const step = (title) => console.log(`\n== ${title}`);
const ok = (label) => {
  passed++;
  console.log(`  ok    ${label}`);
};

class CheckFailed extends Error {}

function fail(label, detail) {
  throw new CheckFailed(`${label}\n        ${detail}`);
}

/** One HTTP call through the gateway; returns { status, data, headers } and never throws on an HTTP error status. */
async function call(method, path, { token, body } = {}) {
  const response = await fetch(`${GATEWAY}${path}`, {
    method,
    headers: {
      Accept: "application/json",
      ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let data = text;
  if (text && /^\s*[{[]/.test(text)) {
    try {
      data = JSON.parse(text);
    } catch {
      // keep the text
    }
  }
  return { status: response.status, data, headers: response.headers };
}

const describe = (data) => (typeof data === "string" ? data.slice(0, 300) : data?.message || JSON.stringify(data).slice(0, 300));

/** Calls the API and checks the HTTP status; returns the response body. */
async function expectStatus(expected, label, method, path, options) {
  const response = await call(method, path, options);
  if (response.status !== expected) fail(label, `${method} ${path}: expected ${expected}, got ${response.status}: ${describe(response.data)}`);
  ok(`${label} (${response.status})`);
  return response.data;
}

/** Same check, also returning the headers (for Location). */
async function expectResponse(expected, label, method, path, options) {
  const response = await call(method, path, options);
  if (response.status !== expected) fail(label, `${method} ${path}: expected ${expected}, got ${response.status}: ${describe(response.data)}`);
  ok(`${label} (${response.status})`);
  return response;
}

/** Compares two values; numbers (and numeric strings such as "2500.00") are compared as numbers. */
function equal(actual, expected, label) {
  const numeric = typeof expected === "number";
  const same = numeric ? Number(actual) === expected : actual === expected;
  if (!same) fail(label, `expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
  ok(`${label}: ${numeric ? expected : JSON.stringify(expected)}`);
}

function check(condition, label, detail = "") {
  if (!condition) fail(label, detail || "condition is false");
  ok(label);
}

/** Retries a check for up to 15 seconds: notifications are written after the request that causes them. */
async function eventually(label, probe) {
  let last;
  for (let attempt = 0; attempt < 30; attempt++) {
    last = await probe();
    if (last.done) {
      ok(label);
      return;
    }
    await sleep(500);
  }
  fail(label, last.detail || "did not happen within 15 seconds");
}

async function waitForGateway() {
  for (let attempt = 1; attempt <= 120; attempt++) {
    try {
      const response = await call("GET", "/api/products?size=1");
      if (response.status === 200) {
        ok(`marketplace answers through the gateway (after ${attempt} ${attempt === 1 ? "try" : "tries"})`);
        return;
      }
    } catch {
      // gateway not listening yet
    }
    await sleep(5000);
  }
  fail("marketplace answers through the gateway", `${GATEWAY} did not answer within 10 minutes`);
}

async function main() {
  console.log(`Nexora smoke test -> ${GATEWAY}`);
  const id = Date.now().toString(36);
  const address = { recipient: "Kush Kumar", phone: "+971 52 111 2222", line1: "Marina Gate 1, Dubai Marina", line2: "Apartment 1204", city: "Dubai", region: "Dubai" };

  step("Platform");
  await waitForGateway();
  const docs = await call("GET", "/v3/api-docs/swagger-config");
  check(docs.status === 200 || (await call("GET", "/swagger-ui.html")).status < 400, "Swagger UI is served by the gateway");
  const categories = await expectStatus(200, "marketplace departments", "GET", "/api/categories");
  const department = (name) => categories.find((c) => c.name === name)?.id ?? categories[0].id;

  step("Accounts: two stores, two customers, separate sign-in pages");
  const storeA = { username: `smk${id}a`, storeName: `Smoke Gadgets ${id}` };
  const storeB = { username: `smk${id}b`, storeName: `Smoke Home ${id}` };
  const register = (account) =>
    expectStatus(201, account.label, "POST", "/api/auth/register", {
      body: { password: PASSWORD, email: `${account.username}@smoke.nexora.test`, ...account.body, username: account.username },
    });
  let auth = await register({
    label: "store A signs up (Seller Central)",
    username: storeA.username,
    body: { accountType: "SELLER", fullName: "Omar Haddad", phone: "+971 50 123 4567", storeName: storeA.storeName, storeDescription: "Phones and accessories", city: "Dubai", tradeLicenseNumber: `DED-${id}` },
  });
  const sellerA = { token: auth.accessToken, id: auth.user.id };
  auth = await register({
    label: "store B signs up",
    username: storeB.username,
    body: { accountType: "SELLER", fullName: "Layla Haddad", phone: "+971 55 765 4321", storeName: storeB.storeName, city: "Sharjah" },
  });
  const sellerB = { token: auth.accessToken, id: auth.user.id };
  await expectStatus(409, "store names are unique", "POST", "/api/auth/register", {
    body: { accountType: "SELLER", username: `smk${id}c`, email: `smk${id}c@smoke.nexora.test`, password: PASSWORD, storeName: storeA.storeName, phone: "+971 50 000 0000", city: "Dubai" },
  });
  auth = await register({ label: "customer signs up (marketplace)", username: `smk${id}k`, body: { accountType: "CUSTOMER", fullName: "Kush Kumar", phone: "+971 52 111 2222" } });
  const customer = { token: auth.accessToken, id: auth.user.id, username: `smk${id}k` };
  auth = await register({ label: "second customer signs up", username: `smk${id}m`, body: { accountType: "CUSTOMER", fullName: "Maria Santos" } });
  const other = { token: auth.accessToken, id: auth.user.id };
  await expectStatus(403, "a customer cannot use the Seller Central sign-in", "POST", "/api/auth/login", {
    body: { username: customer.username, password: PASSWORD, accountType: "SELLER" },
  });
  await expectStatus(403, "a store cannot use the customer sign-in", "POST", "/api/auth/login", {
    body: { username: storeA.username, password: PASSWORD, accountType: "CUSTOMER" },
  });
  const directory = await expectStatus(200, "public store directory", "GET", `/api/sellers?search=${encodeURIComponent(storeA.storeName)}`);
  equal(directory.content[0]?.storeName, storeA.storeName, "store found by name");
  await expectStatus(401, "orders need a token", "GET", "/api/orders");
  await expectStatus(403, "customers cannot use the administrator APIs", "GET", "/api/admin/users", { token: customer.token });

  step("Products: the /api/products REST resource (GET public, POST/PUT/DELETE for stores)");
  const phone = {
    sku: `PHN-${id}`,
    name: "Galaxy S25 Ultra 256 GB",
    brand: "Samsung",
    description: "Titanium frame, 256 GB",
    categoryId: department("Electronics"),
    price: 2500,
    costPrice: 1800,
    reorderPoint: 2,
    reorderQuantity: 10,
    listed: true,
  };
  let response = await expectResponse(201, "store A creates a product (price 2,500, cost 1,800, 5 in stock)", "POST", "/api/products", {
    token: sellerA.token,
    body: { ...phone, initialStock: 5 },
  });
  const p1 = response.data.id;
  equal(response.headers.get("location"), `/api/products/${p1}`, "Location of the new product");
  response = await expectResponse(201, "store A adds an inventory-only item (not for sale)", "POST", "/api/products", {
    token: sellerA.token,
    body: { sku: `BOX-${id}`, name: "Shipping boxes, pack of 50", price: 90, costPrice: 40, initialStock: 10, listed: false },
  });
  const p2 = response.data.id;
  equal(response.headers.get("location"), `/api/seller/products/${p2}`, "Location of a product that has no public page");
  const lamp = await expectStatus(201, "store B creates a product (3 in stock)", "POST", "/api/products", {
    token: sellerB.token,
    body: { sku: `LMP-${id}`, name: "Brass table lamp", brand: "Sahara", categoryId: department("Home & Kitchen"), price: 120, costPrice: 70, initialStock: 3, reorderPoint: 1, listed: true },
  });
  const p3 = lamp.id;
  await expectStatus(403, "customers cannot create products", "POST", "/api/products", { token: customer.token, body: { ...phone, sku: `X-${id}` } });
  const invalid = await expectStatus(400, "invalid product is rejected", "POST", "/api/products", { token: sellerA.token, body: { sku: "no spaces!", name: "", price: 0 } });
  check(invalid.fieldErrors?.price && invalid.fieldErrors?.name && invalid.fieldErrors?.costPrice, "the error names each invalid field", JSON.stringify(invalid));
  await expectStatus(409, "SKUs are unique within a store", "POST", "/api/products", { token: sellerA.token, body: { ...phone, initialStock: 1 } });
  let page = await expectStatus(200, "product page (cached in Redis)", "GET", `/api/products/${p1}`);
  check(page.costPrice === undefined, "the cost price is never shown to customers", JSON.stringify(page));
  await expectStatus(404, "a store cannot change another store's product", "PUT", `/api/products/${p1}`, { token: sellerB.token, body: phone });
  await expectStatus(200, "store A updates its product", "PUT", `/api/products/${p1}`, {
    token: sellerA.token,
    body: { ...phone, name: "Galaxy S25 Ultra 256 GB, titanium grey", compareAtPrice: 2799 },
  });
  page = await expectStatus(200, "product page after the update", "GET", `/api/products/${p1}`);
  equal(page.name, "Galaxy S25 Ultra 256 GB, titanium grey", "the cache was refreshed");
  check(page.discountPercent > 0, `the sale price shows a discount (${page.discountPercent}%)`);
  const storeProducts = await expectStatus(200, "store A on the marketplace", "GET", `/api/products?sellerId=${sellerA.id}`);
  equal(storeProducts.totalElements, 1, "only products for sale are listed");
  await expectStatus(404, "an inventory-only product has no public page", "GET", `/api/products/${p2}`);

  step("Inventory: GET and PUT /api/inventory/{productId}");
  let stock = await expectStatus(200, "store A reads the stock of its product", "GET", `/api/inventory/${p1}`, { token: sellerA.token });
  equal(stock.availableQuantity, 5, "available");
  await expectStatus(404, "another store cannot read it", "GET", `/api/inventory/${p1}`, { token: sellerB.token });
  await expectStatus(403, "customers cannot change stock", "PUT", `/api/inventory/${p1}`, { token: customer.token, body: { onHand: 99 } });
  stock = await expectStatus(200, "stock take: one more unit found on the shelf", "PUT", `/api/inventory/${p1}`, {
    token: sellerA.token,
    body: { onHand: 6, reason: "FOUND", note: "Smoke test count" },
  });
  equal(stock.onHandQuantity, 6, "on hand after the count");

  step("Orders: one checkout across two stores, stock reserved, no overselling");
  const checkout = await expectStatus(201, "checkout: 2 phones from store A and 1 lamp from store B", "POST", "/api/orders", {
    token: customer.token,
    body: { items: [{ productId: p1, quantity: 2 }, { productId: p3, quantity: 1 }], address },
  });
  equal(checkout.orders.length, 2, "orders created (one per store)");
  equal(checkout.grandTotal, 5120, "total calculated by the server (2 x 2,500 + 120)");
  const orderA = checkout.orders.find((o) => o.sellerId === sellerA.id);
  const orderB = checkout.orders.find((o) => o.sellerId === sellerB.id);
  equal(orderA.status, "PENDING", "new order waits for payment");
  stock = await expectStatus(200, "store B's stock", "GET", `/api/inventory/${p3}`, { token: sellerB.token });
  equal(stock.reservedQuantity, 1, "lamp reserved for the order");
  const oversold = await expectStatus(409, "insufficient stock: 3 lamps wanted, 2 available", "POST", "/api/orders", {
    token: customer.token,
    body: { items: [{ productId: p3, quantity: 3 }], address },
  });
  check(/stock|available/i.test(oversold.message || ""), `the 409 explains why ("${oversold.message}")`);
  await expectStatus(200, "GET /api/orders/{id}", "GET", `/api/orders/${orderA.id}`, { token: customer.token });
  await expectStatus(404, "another customer cannot see the order", "GET", `/api/orders/${orderA.id}`, { token: other.token });
  const mine = await expectStatus(200, "GET /api/orders/customer/{customerId}", "GET", `/api/orders/customer/${customer.id}`, { token: customer.token });
  equal(mine.totalElements, 2, "the customer's orders");
  await expectStatus(403, "another customer's order history is private", "GET", `/api/orders/customer/${customer.id}`, { token: other.token });

  step("Payments: POST /api/payments (mock provider), declined card, then a paid checkout");
  const declined = await expectStatus(201, "a declined card is recorded", "POST", "/api/payments", {
    token: customer.token,
    body: { orderId: orderA.id, ...DECLINED_CARD, cardHolder: "Kush Kumar" },
  });
  equal(declined.status, "FAILED", "payment status");
  equal(declined.orderConfirmed, false, "order not confirmed by a declined card");
  const paid = await expectStatus(200, "pay both orders of the checkout with another card", "POST", "/api/payments/checkout", {
    token: customer.token,
    body: { orderIds: [orderA.id, orderB.id], ...GOOD_CARD, cardHolder: "Kush Kumar" },
  });
  check(paid.length === 2 && paid.every((r) => r.success), "both payments succeeded", JSON.stringify(paid));
  const paymentA = paid.find((r) => r.orderId === orderA.id).payment;
  const payment = await expectStatus(200, "GET /api/payments/{id}", "GET", `/api/payments/${paymentA.id}`, { token: customer.token });
  equal(payment.status, "SUCCESS", "payment status");
  check(payment.maskedInstrument?.endsWith("1111") && !payment.maskedInstrument.includes("4111 1111"), `only the last four digits are kept (${payment.maskedInstrument})`);
  await expectStatus(404, "another customer cannot see the payment", "GET", `/api/payments/${paymentA.id}`, { token: other.token });
  let order = await expectStatus(200, "order after payment", "GET", `/api/orders/${orderA.id}`, { token: customer.token });
  equal(order.status, "CONFIRMED", "order status");
  stock = await expectStatus(200, "store A's stock after the sale", "GET", `/api/inventory/${p1}`, { token: sellerA.token });
  equal(stock.availableQuantity, 4, "available (6 - 2)");

  step("Fulfilment by the store");
  const sellerOrders = await expectStatus(200, "store A's orders", "GET", "/api/seller/orders", { token: sellerA.token });
  equal(sellerOrders.totalElements, 1, "orders of store A");
  await expectStatus(404, "store B cannot see store A's order", "GET", `/api/seller/orders/${orderA.id}`, { token: sellerB.token });
  await expectStatus(403, "customers cannot ship orders", "PUT", `/api/seller/orders/${orderA.id}/ship`, { token: customer.token, body: { carrier: "Aramex", trackingNumber: "AWB1" } });
  await expectStatus(200, "store A ships with a tracking number", "PUT", `/api/seller/orders/${orderA.id}/ship`, {
    token: sellerA.token,
    body: { carrier: "Aramex", trackingNumber: `AWB${id}`.toUpperCase() },
  });
  await expectStatus(200, "store A marks it delivered", "PUT", `/api/seller/orders/${orderA.id}/deliver`, { token: sellerA.token });
  order = await expectStatus(200, "the customer tracks the order", "GET", `/api/orders/${orderA.id}`, { token: customer.token });
  equal(order.status, "DELIVERED", "status");
  equal(order.trackingNumber, `AWB${id}`.toUpperCase(), "tracking number");
  await expectStatus(409, "a delivered order cannot be cancelled", "PUT", `/api/orders/${orderA.id}/cancel`, { token: customer.token, body: { reason: "Too late" } });

  step("Cancellations: PUT /api/orders/{id}/cancel refunds the card, the store cancels cash on delivery");
  const cancelled = await expectStatus(200, "the customer cancels the paid lamp order", "PUT", `/api/orders/${orderB.id}/cancel`, {
    token: customer.token,
    body: { reason: "Ordered by mistake" },
  });
  equal(cancelled.status, "CANCELLED", "status");
  let payments = await expectStatus(200, "payments of the cancelled order", "GET", `/api/payments/order/${orderB.id}`, { token: customer.token });
  check(payments.some((p) => p.status === "REFUNDED"), "the card payment was refunded", JSON.stringify(payments.map((p) => p.status)));
  stock = await expectStatus(200, "store B's stock", "GET", `/api/inventory/${p3}`, { token: sellerB.token });
  equal(stock.availableQuantity, 3, "the lamp is back in stock");
  const cod = await expectStatus(201, "a second lamp is ordered", "POST", "/api/orders", { token: customer.token, body: { items: [{ productId: p3, quantity: 1 }], address } });
  const orderC = cod.orders[0];
  const codPayment = await expectStatus(201, "cash on delivery", "POST", "/api/payments", { token: customer.token, body: { orderId: orderC.id, method: "COD" } });
  equal(codPayment.orderConfirmed, true, "order confirmed without paying upfront");
  await expectStatus(200, "store B cancels it (damaged in the store room)", "PUT", `/api/seller/orders/${orderC.id}/cancel`, {
    token: sellerB.token,
    body: { reason: "Damaged in the store room" },
  });
  payments = await expectStatus(200, "payments of that order", "GET", `/api/payments/order/${orderC.id}`, { token: customer.token });
  check(payments.every((p) => p.status === "CANCELLED"), "the cash payment was cancelled, nothing to refund", JSON.stringify(payments.map((p) => p.status)));
  stock = await expectStatus(200, "store B's stock", "GET", `/api/inventory/${p3}`, { token: sellerB.token });
  equal(stock.availableQuantity, 3, "the lamp is back in stock again");

  step("Reviews and messages");
  await expectStatus(403, "only delivered purchases can be reviewed", "POST", "/api/reviews", { token: customer.token, body: { orderId: orderC.id, productId: p3, rating: 5 } });
  await expectStatus(201, "verified review of the phone", "POST", "/api/reviews", {
    token: customer.token,
    body: { orderId: orderA.id, productId: p1, rating: 5, comment: "Genuine phone, delivered the next day." },
  });
  await expectStatus(409, "one review per product", "POST", "/api/reviews", { token: customer.token, body: { orderId: orderA.id, productId: p1, rating: 1 } });
  const reviews = await expectStatus(200, "public reviews of the product", "GET", `/api/reviews?productId=${p1}`);
  equal(reviews.averageRating, 5, "average rating");
  const conversation = await expectStatus(201, "the customer asks store A a question", "POST", "/api/conversations", {
    token: customer.token,
    body: { sellerId: sellerA.id, message: "Does it come with a UAE warranty?", productId: p1, productName: page.name },
  });
  const unread = await expectStatus(200, "store A's unread messages", "GET", "/api/conversations/unread-count", { token: sellerA.token });
  equal(unread.unread, 1, "unread");
  await expectStatus(201, "store A replies", "POST", `/api/conversations/${conversation.conversation.id}/messages`, {
    token: sellerA.token,
    body: { message: "Yes, two years of official UAE warranty." },
  });
  await expectStatus(404, "other stores cannot read the conversation", "GET", `/api/conversations/${conversation.conversation.id}`, { token: sellerB.token });

  step("Notifications (written by the notification service after each event)");
  const typesOf = async (token) => {
    const list = await call("GET", "/api/notifications?size=50", { token });
    return list.status === 200 ? list.data.content.map((n) => n.type) : [];
  };
  await eventually("store A is told about the new order (NEW_ORDER)", async () => {
    const types = await typesOf(sellerA.token);
    return { done: types.includes("NEW_ORDER"), detail: `store A has ${JSON.stringify(types)}` };
  });
  await eventually("the customer is told about the declined card, the delivery and the refund", async () => {
    const types = await typesOf(customer.token);
    const wanted = ["PAYMENT_FAILED", "ORDER_DELIVERED", "ORDER_CANCELLED"];
    return { done: wanted.every((t) => types.includes(t)), detail: `the customer has ${JSON.stringify(types)}` };
  });
  await eventually("store B is told that the customer cancelled", async () => {
    const types = await typesOf(sellerB.token);
    return { done: types.includes("ORDER_CANCELLED"), detail: `store B has ${JSON.stringify(types)}` };
  });

  step("Store finance: earnings and profit and loss");
  const earnings = await expectStatus(200, "store A's payments", "GET", "/api/seller/payments/summary", { token: sellerA.token });
  equal(earnings.grossSales, 5000, "gross sales");
  equal(earnings.platformFees, 250, "Nexora's fee (5%)");
  equal(earnings.netEarnings, 4750, "paid out to the store");
  const pl = await expectStatus(200, "store A's profit and loss", "GET", "/api/seller/finance/profit-loss", { token: sellerA.token });
  equal(pl.grossProfit, 1400, "gross profit (2 x (2,500 - 1,800))");
  equal(pl.netProfit, 1150, "net profit (1,400 - 250 fee)");

  step("Gateway");
  await expectStatus(404, "internal service APIs are not routed by the gateway", "POST", `/internal/payments/order/${orderA.id}/refund`, { token: customer.token, body: {} });

  if (ADMIN_PASSWORD) {
    step("Administrator: the separate Control Center sign-in");
    await expectStatus(401, "the administrator cannot use the marketplace sign-in", "POST", "/api/auth/login", { body: { username: ADMIN_USERNAME, password: ADMIN_PASSWORD } });
    await expectStatus(401, "a store cannot use the Control Center sign-in", "POST", "/api/admin/auth/login", { body: { username: storeA.username, password: PASSWORD } });
    await expectStatus(401, "wrong administrator password", "POST", "/api/admin/auth/login", { body: { username: ADMIN_USERNAME, password: `${ADMIN_PASSWORD}-wrong` } });
    const admin = await expectStatus(200, "administrator signs in", "POST", "/api/admin/auth/login", { body: { username: ADMIN_USERNAME, password: ADMIN_PASSWORD } });
    equal(admin.expiresIn, 7200, "console session length in seconds");
    const token = admin.accessToken;
    await expectStatus(200, "marketplace overview", "GET", "/api/admin/sales/overview", { token });
    const found = await expectStatus(200, "find the order", "GET", `/api/admin/orders?q=${encodeURIComponent(orderA.orderNumber)}`, { token });
    equal(found.totalElements, 1, "orders found");
    const stores = await expectStatus(200, "find the test stores", "GET", `/api/admin/users?role=SELLER&search=smk${id}`, { token });
    equal(stores.totalElements, 2, "stores found");
    const failures = await expectStatus(200, "security log", "GET", `/api/admin/security/logins?success=false&search=${customer.username}`, { token });
    check(failures.totalElements >= 1, "the failed sign-in is in the security log");
    await expectStatus(403, "stores cannot use the administrator APIs", "GET", "/api/admin/users", { token: sellerA.token });
    await expectStatus(200, "the administrator suspends store B", "PUT", `/api/admin/users/${sellerB.id}/status`, { token, body: { active: false, reason: "Smoke test: suspension" } });
    await expectStatus(403, "a suspended store cannot sign in", "POST", "/api/auth/login", { body: { username: storeB.username, password: PASSWORD, accountType: "SELLER" } });
    await expectStatus(404, "its store page is gone", "GET", `/api/sellers/${sellerB.id}`);
    const hidden = await expectStatus(200, "its products", "GET", `/api/products?sellerId=${sellerB.id}`);
    equal(hidden.totalElements, 0, "products of a suspended store on the marketplace");
    await expectStatus(200, "the administrator reactivates store B", "PUT", `/api/admin/users/${sellerB.id}/status`, { token, body: { active: true } });
    const back = await expectStatus(200, "its products", "GET", `/api/products?sellerId=${sellerB.id}`);
    equal(back.totalElements, 1, "products back on the marketplace");

    if (!KEEP_TEST_DATA) {
      step("Clean-up: products deleted with DELETE /api/products/{id}, test accounts suspended");
      await cleanUpProducts();
      for (const [label, userId] of [["store A", sellerA.id], ["store B", sellerB.id], ["customer", customer.id], ["second customer", other.id]]) {
        await expectStatus(200, `test account suspended: ${label}`, "PUT", `/api/admin/users/${userId}/status`, { token, body: { active: false, reason: "Smoke test account" } });
      }
    }
  } else {
    console.log("\n  (ADMIN_PASSWORD is not set: administrator checks skipped)");
    if (!KEEP_TEST_DATA) {
      step("Clean-up: products deleted with DELETE /api/products/{id}");
      await cleanUpProducts();
    }
  }

  async function cleanUpProducts() {
    await expectStatus(204, "store A deletes the phone (archived: orders keep it)", "DELETE", `/api/products/${p1}`, { token: sellerA.token });
    await expectStatus(204, "store A deletes the boxes", "DELETE", `/api/products/${p2}`, { token: sellerA.token });
    await expectStatus(204, "store B deletes the lamp", "DELETE", `/api/products/${p3}`, { token: sellerB.token });
    await expectStatus(404, "a deleted product leaves the marketplace", "GET", `/api/products/${p1}`);
  }

  console.log(`\nAll ${passed} checks passed in ${((Date.now() - started) / 1000).toFixed(1)} s.`);
}

main().catch((error) => {
  if (error instanceof CheckFailed) {
    console.error(`  FAIL  ${error.message}`);
    console.error(`\n${passed} checks passed before the failure.`);
  } else {
    console.error(`\nSmoke test stopped: ${error.stack || error.message}`);
  }
  process.exit(1);
});
