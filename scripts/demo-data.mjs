#!/usr/bin/env node
// Fills Nexora with a realistic demo through the public API gateway, exactly as real users would:
// four stores with inventory, suppliers, purchase orders, warehouses, counts, expenses and budgets,
// six customers who order from several stores, pay (card, bank transfer, cash on delivery), get deliveries,
// write reviews and message the stores. Nothing is written to a database directly.
//
// Usage:  node scripts/demo-data.mjs [gateway-url]          (Node 18+, no npm install needed)
//         ADMIN_PASSWORD=... node scripts/demo-data.mjs     (also publishes a marketplace announcement)
//         WAKE_URLS=https://a.onrender.com,... node ...     (free Render: wake these services directly first)
// Running it again does nothing if the demo stores already exist.

const GATEWAY = (process.argv[2] || process.env.GATEWAY_URL || "http://localhost:8080").replace(/\/$/, "");
const PASSWORD = process.env.DEMO_PASSWORD || "Demo-2026";
const GOOD_CARD = { method: "CARD", cardNumber: "4111 1111 1111 1111", expiry: "12/29", cvv: "123" };
const DECLINED_CARD = { method: "CARD", cardNumber: "4000 0000 0000 0002", expiry: "12/29", cvv: "123" };
// Free Render services sleep after 15 minutes without visitors, and only a request from outside Render wakes one:
// when the gateway calls a sleeping service, Render answers 502 and the service stays asleep. render-demo.cmd
// therefore lists the public address of every service; they are woken directly and kept awake while the demo loads.
const WAKE_URLS = (process.env.WAKE_URLS || "")
  .split(",")
  .map((url) => url.trim().replace(/\/$/, ""))
  .filter(Boolean);

let calls = 0;
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const log = (text) => console.log(`  ${text}`);
const step = (text) => console.log(`\n== ${text}`);

/** One API call; waits while a service wakes up or restarts (502/503/504, up to 8 minutes) and fails loudly on anything else. */
async function api(path, { method = "GET", token, body, expect = [200, 201, 204] } = {}) {
  const started = Date.now();
  const retry = () => Date.now() - started < 8 * 60000;
  for (;;) {
    calls++;
    let response;
    try {
      response = await fetch(`${GATEWAY}${path}`, {
        method,
        headers: { "Content-Type": "application/json", Accept: "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch (error) {
      if (retry()) {
        await sleep(5000);
        continue;
      }
      throw new Error(`${method} ${path}: ${error.message}`);
    }
    if ([502, 503, 504].includes(response.status) && retry()) {
      await sleep(5000);
      continue;
    }
    const text = await response.text();
    const data = text && /^\s*[{[]/.test(text) ? JSON.parse(text) : text;
    if (!expect.includes(response.status)) {
      const error = new Error(`${method} ${path} -> ${response.status}: ${typeof data === "string" ? data.slice(0, 300) : data.message || JSON.stringify(data)}`);
      error.status = response.status;
      throw error;
    }
    return data;
  }
}

let keepAwake;

/** Repeats a check every 5 seconds until it succeeds; fails after 10 minutes. */
async function waitFor(what, check) {
  const started = Date.now();
  while (!(await check())) {
    if (Date.now() - started > 10 * 60000) throw new Error(`${what} did not start within 10 minutes`);
    await sleep(5000);
  }
}

/** True when the service at this address answers its health check (Render holds the request while it starts). */
async function healthy(url) {
  try {
    const response = await fetch(`${url}/actuator/health`, { headers: { Accept: "application/json" }, signal: AbortSignal.timeout(150000) });
    return response.ok && (await response.text()).includes('"UP"');
  } catch {
    return false;
  }
}

/**
 * A sleeping service needs about two minutes to start. On Render every service is woken directly (WAKE_URLS) and
 * pinged every 4 minutes until the demo is loaded; then each of the five routes of the gateway must answer
 * (every service serves its API description at /api-docs/<name>) before the first real call.
 */
async function wakeUp() {
  if (WAKE_URLS.length > 0) {
    await Promise.all(WAKE_URLS.map((url) => waitFor(new URL(url).hostname, () => healthy(url))));
    keepAwake = setInterval(() => WAKE_URLS.forEach((url) => healthy(url)), 4 * 60000);
    log(`${WAKE_URLS.length} services awake (kept awake until the demo is loaded)`);
  }
  await Promise.all(
    ["auth", "inventory", "orders", "payments", "notifications"].map((name) =>
      waitFor(`the ${name} service`, async () => {
        try {
          const response = await fetch(`${GATEWAY}/api-docs/${name}`, { signal: AbortSignal.timeout(150000) });
          await response.arrayBuffer();
          return response.status < 500;
        } catch {
          return false;
        }
      }),
    ),
  );
  log("all five services are up");
}

const daysFromNow = (days) => new Date(Date.now() + days * 86400000).toISOString().slice(0, 10);
const monthOffset = (delta) => {
  const now = new Date();
  return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + delta, 1)).toISOString().slice(0, 7);
};
const dayInMonth = (month, day) => {
  const candidate = `${month}-${String(day).padStart(2, "0")}`;
  return candidate > daysFromNow(0) ? daysFromNow(0) : candidate;
};
const pick = (list, index) => list[index % list.length];
// How far back demo-backdate.sql spreads the orders (keep the two files in step).
const HISTORY_DAYS = 160;

// ------------------------------------------------------------------ the demo world

const STORES = [
  {
    username: "alnoor",
    fullName: "Yousef Al Noor",
    storeName: "Al Noor Electronics",
    city: "Dubai",
    phone: "+971 4 388 2100",
    storeDescription: "Phones, audio and computer accessories from brands we have sold in Deira for twenty years.",
    tradeLicenseNumber: "DED-784512",
    vatNumber: "100398765400003",
    warehouses: [{ code: "JAFZA", name: "Jebel Ali free zone", city: "Dubai", address: "Plot S30402, JAFZA South" }],
    suppliers: [
      { key: "gulfdist", name: "Gulf Distribution Trading", contactName: "Sameer Patel", email: "orders@gulfdist.example", phone: "+971 4 222 3344", city: "Dubai", leadTimeDays: 5, paymentTermsDays: 30 },
      { key: "shenzhen", name: "Shenzhen Bright Accessories", contactName: "Lily Chen", email: "sales@brightacc.example", phone: "+86 755 8800 1234", city: "Shenzhen", country: "China", leadTimeDays: 21, paymentTermsDays: 0 },
    ],
    products: [
      { sku: "ANE-EAR-01", name: "Wireless noise-cancelling earbuds", brand: "Sonora", category: "Electronics", price: 349, compareAt: 429, cost: 190, stock: 42, supplier: "gulfdist", description: "Active noise cancelling, 30 hours with the charging case and an IPX4 rating for the gym." },
      { sku: "ANE-PWR-20", name: "20,000 mAh power bank with USB-C fast charge", brand: "Voltix", category: "Electronics", price: 149, cost: 72, stock: 65, supplier: "shenzhen", description: "Charges a phone four times; 22.5 W fast charging and a small display that shows what is left." },
      { sku: "ANE-CHG-65", name: "65 W GaN wall charger, three ports", brand: "Voltix", category: "Electronics", price: 119, compareAt: 139, cost: 58, stock: 38, supplier: "shenzhen", description: "Charges a laptop, a phone and earbuds at once. UK plug." },
      { sku: "ANE-SPK-02", name: "Portable Bluetooth speaker, waterproof", brand: "Sonora", category: "Electronics", price: 229, cost: 128, stock: 4, reorderPoint: 6, supplier: "gulfdist", description: "Rich sound for the beach or the majlis; 18 hours of play and an IP67 rating." },
      { sku: "ANE-CAB-3M", name: "Braided USB-C to USB-C cable, 3 m", brand: "Voltix", category: "Electronics", price: 39, cost: 12, stock: 220, reorderPoint: 40, reorderQuantity: 200, supplier: "shenzhen", description: "100 W, nylon braided, tested for 20,000 bends." },
      { sku: "ANE-KBD-01", name: "Bilingual Arabic-English wireless keyboard", brand: "Typewell", category: "Computers & Office", price: 179, cost: 95, stock: 27, supplier: "gulfdist", description: "Laser-etched Arabic and English keys, quiet switches and a battery that lasts a year." },
      { sku: "ANE-MSE-02", name: "Ergonomic vertical mouse", brand: "Typewell", category: "Computers & Office", price: 129, compareAt: 159, cost: 61, stock: 31, supplier: "gulfdist", description: "Holds your hand in a natural handshake position. Silent clicks, three DPI levels." },
      { sku: "ANE-MON-27", name: "27-inch QHD monitor, USB-C 65 W", brand: "Clarity", category: "Computers & Office", price: 1299, cost: 940, stock: 9, reorderPoint: 4, supplier: "gulfdist", description: "2560 x 1440 IPS panel; one USB-C cable charges your laptop and carries the picture." },
      { sku: "ANE-WEB-4K", name: "4K webcam with privacy shutter", brand: "Clarity", category: "Computers & Office", price: 289, cost: 160, stock: 0, supplier: "gulfdist", description: "Sharp video calls in low light, two microphones and a sliding cover." },
      { sku: "ANE-HUB-07", name: "7-in-1 USB-C hub", brand: "Voltix", category: "Computers & Office", price: 159, cost: 66, stock: 54, supplier: "shenzhen", description: "HDMI 4K, two USB-A, SD and microSD, 100 W pass-through charging." },
      { sku: "ANE-DEMO-UNIT", name: "Display stand for the shop counter", brand: "Al Noor", category: null, price: 60, cost: 35, stock: 3, listed: false, description: "Inventory only: shop fittings that are not for sale." },
    ],
  },
  {
    username: "saharahome",
    fullName: "Layla Haddad",
    storeName: "Sahara Home & Living",
    city: "Sharjah",
    phone: "+971 6 544 7700",
    storeDescription: "Handpicked homeware: Arabic coffee sets, linen, lanterns and everything for a welcoming home.",
    tradeLicenseNumber: "SEDD-20841",
    vatNumber: "100512348900003",
    warehouses: [{ code: "SHJ-IND", name: "Industrial Area 13 store room", city: "Sharjah", address: "Warehouse 8, Industrial Area 13" }],
    suppliers: [
      { key: "karak", name: "Karak Ceramics LLC", contactName: "Huda Saleh", email: "huda@karakceramics.example", phone: "+971 6 533 1200", city: "Sharjah", leadTimeDays: 7, paymentTermsDays: 30 },
      { key: "textiles", name: "Al Waha Textiles", contactName: "Rashid Omar", email: "sales@alwaha.example", phone: "+971 4 266 9001", city: "Dubai", leadTimeDays: 10, paymentTermsDays: 45 },
    ],
    products: [
      { sku: "SHL-DLH-SET", name: "Brass dallah with six finjan cups", brand: "Sahara", category: "Home & Kitchen", price: 245, compareAt: 299, cost: 120, stock: 18, supplier: "karak", description: "Traditional Arabic coffee pot in hand-finished brass, with six porcelain cups." },
      { sku: "SHL-LAN-L", name: "Moroccan lantern, large", brand: "Sahara", category: "Home & Kitchen", price: 185, cost: 84, stock: 22, supplier: "karak", description: "Pierced metal lantern that throws patterned light; for candles or an LED bulb." },
      { sku: "SHL-LIN-K", name: "Stonewashed linen bedding set, king", brand: "Oasis Linen", category: "Home & Kitchen", price: 499, compareAt: 590, cost: 260, stock: 12, supplier: "textiles", description: "Duvet cover, fitted sheet and two pillowcases in breathable stonewashed linen." },
      { sku: "SHL-RUG-160", name: "Hand-woven kilim rug, 160 x 230 cm", brand: "Sahara", category: "Home & Kitchen", price: 899, cost: 520, stock: 5, reorderPoint: 3, supplier: "textiles", description: "Flat-woven wool in desert tones. Every rug is slightly different." },
      { sku: "SHL-MUG-4", name: "Stoneware mugs, set of 4", brand: "Karak Studio", category: "Home & Kitchen", price: 95, cost: 38, stock: 48, supplier: "karak", description: "Reactive glaze in sand and teal; dishwasher and microwave safe." },
      { sku: "SHL-DIF-01", name: "Bakhoor burner, electric", brand: "Sahara", category: "Home & Kitchen", price: 135, cost: 61, stock: 30, supplier: "karak", description: "Warms bakhoor without charcoal; two heat settings and an auto-off timer." },
      { sku: "SHL-TWL-6", name: "Turkish cotton towel bundle, 6 pieces", brand: "Oasis Linen", category: "Home & Kitchen", price: 219, cost: 102, stock: 3, reorderPoint: 8, supplier: "textiles", description: "Two bath, two hand and two face towels in long-staple cotton." },
      { sku: "SHL-CUS-2", name: "Embroidered floor cushions, pair", brand: "Sahara", category: "Home & Kitchen", price: 329, compareAt: 379, cost: 170, stock: 14, supplier: "textiles", description: "Majlis-style cushions with hand embroidery; removable covers." },
    ],
  },
  {
    username: "oudamber",
    fullName: "Mariam Al Qasimi",
    storeName: "Oud & Amber Beauty",
    city: "Abu Dhabi",
    phone: "+971 2 641 9090",
    storeDescription: "Perfume oils, oud and skincare made in the Emirates.",
    tradeLicenseNumber: "ADDED-CN-3321",
    vatNumber: "",
    warehouses: [],
    suppliers: [
      { key: "attar", name: "Emirates Attar House", contactName: "Khalid Rahman", email: "supply@attarhouse.example", phone: "+971 2 674 5511", city: "Abu Dhabi", leadTimeDays: 4, paymentTermsDays: 15 },
    ],
    products: [
      { sku: "OAB-OUD-12", name: "Cambodian oud oil, 3 ml", brand: "Oud & Amber", category: "Beauty & Personal Care", price: 450, cost: 210, stock: 16, supplier: "attar", description: "Aged oud oil with a smoky, sweet finish. One drop lasts the day." },
      { sku: "OAB-PRF-50", name: "Amber and rose eau de parfum, 50 ml", brand: "Oud & Amber", category: "Beauty & Personal Care", price: 295, compareAt: 340, cost: 118, stock: 34, supplier: "attar", description: "Taif rose over warm amber and a touch of saffron." },
      { sku: "OAB-BKH-250", name: "Bakhoor chips, 250 g", brand: "Oud & Amber", category: "Beauty & Personal Care", price: 85, cost: 31, stock: 75, supplier: "attar", description: "Agarwood chips soaked in perfume oils for the home." },
      { sku: "OAB-SKN-SER", name: "Date seed facial serum, 30 ml", brand: "Nakheel Skin", category: "Beauty & Personal Care", price: 159, cost: 54, stock: 41, supplier: "attar", description: "Lightweight serum with date seed oil and niacinamide for daily hydration." },
      { sku: "OAB-SKN-SPF", name: "Mineral sunscreen SPF 50, 50 ml", brand: "Nakheel Skin", category: "Beauty & Personal Care", price: 119, cost: 44, stock: 6, reorderPoint: 10, supplier: "attar", description: "Zinc oxide sunscreen that does not leave a white cast; reef-friendly." },
      { sku: "OAB-GFT-SET", name: "Discovery set of five perfume oils", brand: "Oud & Amber", category: "Beauty & Personal Care", price: 199, cost: 77, stock: 25, supplier: "attar", description: "Five 2 ml roll-ons to find your signature scent; gift boxed." },
    ],
  },
  {
    username: "falconsports",
    fullName: "Daniel Okafor",
    storeName: "Falcon Sports Hub",
    city: "Ajman",
    phone: "+971 6 711 4040",
    storeDescription: "Gear for padel, running, cycling and the desert: tested by our team on the Ajman corniche.",
    tradeLicenseNumber: "AJ-552190",
    vatNumber: "100776512300003",
    warehouses: [],
    suppliers: [
      { key: "athletica", name: "Athletica Middle East", contactName: "Grace Thomas", email: "trade@athletica.example", phone: "+971 4 399 1000", city: "Dubai", leadTimeDays: 6, paymentTermsDays: 30 },
      { key: "desertgear", name: "Desert Gear Trading", contactName: "Faisal Ali", email: "faisal@desertgear.example", phone: "+971 6 744 2020", city: "Ajman", leadTimeDays: 3, paymentTermsDays: 7 },
    ],
    products: [
      { sku: "FSH-PAD-PRO", name: "Carbon padel racket, pro", brand: "Smash", category: "Sports & Outdoors", price: 689, compareAt: 799, cost: 390, stock: 11, supplier: "athletica", description: "3K carbon face and EVA soft core for control and power." },
      { sku: "FSH-PAD-BALL", name: "Padel balls, tube of 3", brand: "Smash", category: "Sports & Outdoors", price: 35, cost: 14, stock: 140, reorderPoint: 30, reorderQuantity: 120, supplier: "athletica", description: "Pressurised balls approved for club play." },
      { sku: "FSH-RUN-HYD", name: "Running hydration vest, 5 L", brand: "Dune Runner", category: "Sports & Outdoors", price: 249, cost: 118, stock: 19, supplier: "desertgear", description: "Two soft flasks, phone pocket and reflective trims for early-morning runs." },
      { sku: "FSH-BOT-1L", name: "Insulated steel bottle, 1 litre", brand: "Dune Runner", category: "Sports & Outdoors", price: 89, cost: 33, stock: 86, supplier: "desertgear", description: "Keeps water cold for 24 hours, even in a car in July." },
      { sku: "FSH-YOG-MAT", name: "Non-slip yoga mat, 6 mm", brand: "Smash", category: "Sports & Outdoors", price: 139, compareAt: 169, cost: 52, stock: 33, supplier: "athletica", description: "Grippy natural rubber on top and a cushioned base." },
      { sku: "FSH-CAM-CHR", name: "Folding camping chair with cooler", brand: "Desert Gear", category: "Sports & Outdoors", price: 159, cost: 71, stock: 2, reorderPoint: 5, supplier: "desertgear", description: "For desert camping and the beach; side cooler bag and cup holder." },
      { sku: "FSH-BIK-LGT", name: "Rechargeable bike light set", brand: "Desert Gear", category: "Sports & Outdoors", price: 99, cost: 36, stock: 44, supplier: "desertgear", description: "800 lumen front and a bright rear light; USB-C charging." },
    ],
  },
];

const CUSTOMERS = [
  { username: "aisha", fullName: "Aisha Al Mansoori", phone: "+971 50 214 8833", address: { line1: "Marina Gate 2, Dubai Marina", line2: "Apartment 1804", city: "Dubai", region: "Dubai" } },
  { username: "rahul", fullName: "Rahul Menon", phone: "+971 55 902 1177", address: { line1: "Al Majaz 3, Corniche Street", line2: "Flat 506", city: "Sharjah", region: "Sharjah" } },
  { username: "fatima", fullName: "Fatima Khan", phone: "+971 52 448 6620", address: { line1: "Al Reem Island, Sun Tower", line2: "Apartment 2210", city: "Abu Dhabi", region: "Abu Dhabi" } },
  { username: "omar", fullName: "Omar Haddad", phone: "+971 56 330 7781", address: { line1: "Al Nuaimiya 1, Sheikh Khalifa Street", line2: "Villa 14", city: "Ajman", region: "Ajman" } },
  { username: "maria", fullName: "Maria Santos", phone: "+971 58 771 0042", address: { line1: "JLT Cluster D, Saba Tower 1", line2: "Office 902", city: "Dubai", region: "Dubai" } },
  { username: "hamad", fullName: "Hamad Al Shamsi", phone: "+971 50 660 9134", address: { line1: "Al Nakheel, Corniche Road", line2: "Villa 3", city: "Ras Al Khaimah", region: "Ras Al Khaimah" } },
];

// ------------------------------------------------------------------ helpers

async function signUp(account) {
  try {
    const auth = await api("/api/auth/register", { method: "POST", body: { ...account, email: `${account.username}@demo.nexora.ae`, password: PASSWORD }, expect: [201] });
    return { token: auth.accessToken, user: auth.user, created: true };
  } catch (error) {
    if (error.status !== 409) throw error;
    const auth = await api("/api/auth/login", { method: "POST", body: { username: account.username, password: PASSWORD, accountType: account.accountType } });
    return { token: auth.accessToken, user: auth.user, created: false };
  }
}

async function placeOrder(customer, lines, payment, label) {
  const checkout = await api("/api/orders", {
    method: "POST",
    token: customer.token,
    body: { items: lines.map(([product, quantity]) => ({ productId: product.id, quantity })), address: customer.address },
    expect: [200, 201],
  });
  const ids = checkout.orders.map((o) => o.id);
  let results = [];
  if (payment) {
    results = await api("/api/payments/checkout", { method: "POST", token: customer.token, body: { orderIds: ids, ...payment, cardHolder: customer.fullName } });
  }
  log(`${customer.fullName}: ${label} (${checkout.orders.map((o) => o.orderNumber).join(", ")}) ${!payment ? "not paid yet" : !results.every((r) => r.success) ? "payment declined" : payment.method === "COD" ? "cash on delivery" : "paid"}`);
  return { orders: checkout.orders, results };
}

// ------------------------------------------------------------------ main

async function main() {
  console.log(`Nexora demo data -> ${GATEWAY}`);
  step("Waiting for the platform");
  await wakeUp();
  const categories = await api("/api/categories");
  const categoryId = Object.fromEntries(categories.map((c) => [c.name, c.id]));
  log(`${categories.length} marketplace departments`);

  step("Stores (Seller Central accounts)");
  const stores = [];
  for (const store of STORES) {
    const { warehouses, suppliers, products, ...profile } = store;
    const account = await signUp({ accountType: "SELLER", ...profile });
    const existing = await api("/api/seller/products?size=1", { token: account.token });
    if (!account.created && existing.totalElements > 0) {
      console.log(`\nThe demo stores already exist (${store.storeName} has products). Nothing to do.`);
      console.log(`Sign in with any demo account and the password ${PASSWORD}.`);
      return;
    }
    stores.push({ ...store, token: account.token, id: account.user.id, supplierIds: {}, productIds: {}, products: [] });
    log(`${store.storeName} (${store.username})`);
  }

  step("Warehouses, suppliers and products");
  for (const store of stores) {
    for (const w of store.warehouses) await api("/api/seller/warehouses", { method: "POST", token: store.token, body: w, expect: [200, 201] });
    for (const s of store.suppliers) {
      const { key, ...body } = s;
      const created = await api("/api/seller/suppliers", { method: "POST", token: store.token, body: { country: "United Arab Emirates", ...body }, expect: [200, 201] });
      store.supplierIds[key] = created.id;
    }
    for (const p of STORES.find((s) => s.username === store.username).products) {
      const created = await api("/api/seller/products", {
        method: "POST",
        token: store.token,
        expect: [200, 201],
        body: {
          sku: p.sku,
          name: p.name,
          brand: p.brand,
          description: p.description,
          unit: "PIECE",
          categoryId: p.category ? categoryId[p.category] : null,
          price: p.price,
          compareAtPrice: p.compareAt || null,
          costPrice: p.cost,
          reorderPoint: p.reorderPoint ?? 5,
          reorderQuantity: p.reorderQuantity ?? 20,
          preferredSupplierId: p.supplier ? store.supplierIds[p.supplier] : null,
          listed: p.listed !== false,
          initialStock: p.stock,
        },
      });
      store.productIds[p.sku] = created.id;
      store.products.push(created);
    }
    log(`${store.storeName}: ${store.products.length} products, ${store.suppliers.length} suppliers`);
  }
  const [alnoor, sahara, oud, falcon] = stores;
  const product = (store, sku) => store.products.find((p) => p.sku === sku);

  step("Purchasing: purchase orders and deliveries");
  for (const store of stores) {
    const supplierKeys = Object.keys(store.supplierIds);
    const lines = store.products.filter((p) => p.listed).slice(0, 3);
    // a delivered order
    let po = await api("/api/seller/purchase-orders", {
      method: "POST",
      token: store.token,
      expect: [200, 201],
      body: { supplierId: store.supplierIds[supplierKeys[0]], expectedDate: daysFromNow(2), notes: "Restock for the weekend", lines: lines.map((p) => ({ productId: p.id, quantity: 12, unitCost: p.costPrice })) },
    });
    po = await api(`/api/seller/purchase-orders/${po.id}/submit`, { method: "POST", token: store.token });
    await api(`/api/seller/purchase-orders/${po.id}/receive`, {
      method: "POST",
      token: store.token,
      body: { lines: po.lines.map((l) => ({ lineId: l.id, quantity: l.quantityOrdered })), supplierReference: `DN-${po.number.slice(-4)}`, note: "Checked on arrival" },
    });
    // a partly delivered order
    po = await api("/api/seller/purchase-orders", {
      method: "POST",
      token: store.token,
      expect: [200, 201],
      body: { supplierId: store.supplierIds[pick(supplierKeys, 1)], expectedDate: daysFromNow(4), lines: lines.slice(0, 2).map((p) => ({ productId: p.id, quantity: 20, unitCost: p.costPrice })) },
    });
    po = await api(`/api/seller/purchase-orders/${po.id}/submit`, { method: "POST", token: store.token });
    await api(`/api/seller/purchase-orders/${po.id}/receive`, { method: "POST", token: store.token, body: { lines: [{ lineId: po.lines[0].id, quantity: 8 }], supplierReference: `DN-${po.number.slice(-4)}-A` } });
    // an order on its way, and a draft
    const low = store.products.filter((p) => p.stockStatus !== "IN_STOCK").slice(0, 2);
    if (low.length) {
      po = await api("/api/seller/purchase-orders", {
        method: "POST",
        token: store.token,
        expect: [200, 201],
        body: { supplierId: store.supplierIds[supplierKeys[0]], expectedDate: daysFromNow(6), lines: low.map((p) => ({ productId: p.id, quantity: p.reorderQuantity || 20, unitCost: p.costPrice })) },
      });
      await api(`/api/seller/purchase-orders/${po.id}/submit`, { method: "POST", token: store.token });
    }
    await api("/api/seller/purchase-orders", {
      method: "POST",
      token: store.token,
      expect: [200, 201],
      body: { supplierId: store.supplierIds[pick(supplierKeys, 1)], notes: "Draft: confirm prices first", lines: [{ productId: store.products[store.products.length - 2].id, quantity: 10, unitCost: store.products[store.products.length - 2].costPrice }] },
    });
    log(`${store.storeName}: received, partly received, ordered and draft purchase orders`);
  }

  step("Warehouse moves, adjustments and a stock count");
  const alnoorWarehouses = await api("/api/seller/warehouses", { token: alnoor.token });
  const main = alnoorWarehouses.find((w) => w.defaultWarehouse);
  const jafza = alnoorWarehouses.find((w) => w.code === "JAFZA");
  await api("/api/seller/transfers", {
    method: "POST",
    token: alnoor.token,
    expect: [200, 201],
    body: { fromWarehouseId: main.id, toWarehouseId: jafza.id, note: "Bulk stock to the free zone", lines: [{ productId: product(alnoor, "ANE-CAB-3M").id, quantity: 120 }, { productId: product(alnoor, "ANE-PWR-20").id, quantity: 25 }] },
  });
  await api(`/api/seller/inventory/${product(alnoor, "ANE-CAB-3M").id}/bin`, { method: "PUT", token: alnoor.token, body: { warehouseId: main.id, binLocation: "A-02-1" } });
  await api(`/api/seller/inventory/${product(alnoor, "ANE-EAR-01").id}/bin`, { method: "PUT", token: alnoor.token, body: { warehouseId: main.id, binLocation: "B-01-3" } });
  await api("/api/seller/inventory/adjustments", { method: "POST", token: alnoor.token, expect: [200, 201], body: { productId: product(alnoor, "ANE-CHG-65").id, warehouseId: main.id, quantityChange: -2, reason: "DAMAGED", note: "Cracked casing, returned by courier" } });
  await api("/api/seller/inventory/adjustments", { method: "POST", token: sahara.token, expect: [200, 201], body: { productId: product(sahara, "SHL-MUG-4").id, quantityChange: -1, reason: "DAMAGED", note: "Broken in the store room" } });
  await api("/api/seller/inventory/adjustments", { method: "POST", token: oud.token, expect: [200, 201], body: { productId: product(oud, "OAB-BKH-250").id, quantityChange: -3, reason: "INTERNAL_USE", note: "Used for the shop" } });
  let count = await api("/api/seller/stock-counts", { method: "POST", token: alnoor.token, expect: [200, 201], body: { warehouseId: main.id, categoryId: categoryId["Computers & Office"], note: "Monthly count, computers aisle" } });
  count = await api(`/api/seller/stock-counts/${count.id}`, { token: alnoor.token });
  // two differences: units missing from one shelf, one extra unit found on another
  const stocked = count.items.filter((line) => line.expectedQuantity > 2);
  const missing = stocked[0]?.id;
  const found = stocked[1]?.id;
  await api(`/api/seller/stock-counts/${count.id}/lines`, {
    method: "PUT",
    token: alnoor.token,
    body: {
      lines: count.items.map((line) => ({
        lineId: line.id,
        countedQuantity: line.id === missing ? line.expectedQuantity - 2 : line.id === found ? line.expectedQuantity + 1 : line.expectedQuantity,
      })),
    },
  });
  await api(`/api/seller/stock-counts/${count.id}/complete`, { method: "POST", token: alnoor.token });
  await api("/api/seller/stock-counts", { method: "POST", token: falcon.token, expect: [200, 201], body: { note: "Year-end count (in progress)" } });
  log("transfer to JAFZA, shelf locations, damaged stock, one completed and one open count");

  step("Customers");
  const customers = [];
  for (const c of CUSTOMERS) {
    const account = await signUp({ accountType: "CUSTOMER", username: c.username, fullName: c.fullName, phone: c.phone });
    const address = { recipient: c.fullName, phone: c.phone, country: "United Arab Emirates", ...c.address };
    await api("/api/users/me/address", { method: "PUT", token: account.token, body: address });
    customers.push({ ...c, token: account.token, id: account.user.id, address });
    log(c.fullName);
  }
  const [aisha, rahul, fatima, omar, maria, hamad] = customers;

  step("Orders and payments");
  const paid = [];
  const keep = (result) => {
    result.orders.forEach((o) => paid.push(o));
    return result;
  };
  keep(await placeOrder(aisha, [[product(alnoor, "ANE-EAR-01"), 1], [product(sahara, "SHL-DLH-SET"), 1], [product(oud, "OAB-PRF-50"), 2]], GOOD_CARD, "earbuds, a dallah set and perfume from three stores"));
  keep(await placeOrder(rahul, [[product(alnoor, "ANE-MON-27"), 1], [product(alnoor, "ANE-HUB-07"), 1], [product(alnoor, "ANE-KBD-01"), 1]], GOOD_CARD, "a home-office setup"));
  keep(await placeOrder(fatima, [[product(oud, "OAB-OUD-12"), 1], [product(oud, "OAB-SKN-SER"), 2], [product(oud, "OAB-GFT-SET"), 1]], { method: "UPI", upiId: "fatima.khan@adcb" }, "oud and skincare by bank transfer"));
  keep(await placeOrder(omar, [[product(falcon, "FSH-PAD-PRO"), 1], [product(falcon, "FSH-PAD-BALL"), 4]], { method: "COD" }, "a padel racket, cash on delivery"));
  keep(await placeOrder(maria, [[product(sahara, "SHL-LIN-K"), 1], [product(sahara, "SHL-MUG-4"), 2], [product(sahara, "SHL-LAN-L"), 1]], GOOD_CARD, "bedding, mugs and a lantern"));
  keep(await placeOrder(hamad, [[product(falcon, "FSH-CAM-CHR"), 1], [product(falcon, "FSH-BOT-1L"), 2], [product(alnoor, "ANE-SPK-02"), 1]], GOOD_CARD, "camping gear and a speaker"));
  keep(await placeOrder(aisha, [[product(alnoor, "ANE-PWR-20"), 1], [product(alnoor, "ANE-CAB-3M"), 2]], { method: "COD" }, "a power bank and cables, cash on delivery"));
  keep(await placeOrder(rahul, [[product(falcon, "FSH-RUN-HYD"), 1], [product(falcon, "FSH-BIK-LGT"), 1], [product(oud, "OAB-SKN-SPF"), 1]], GOOD_CARD, "running gear and sunscreen"));
  keep(await placeOrder(fatima, [[product(sahara, "SHL-CUS-2"), 1], [product(sahara, "SHL-DIF-01"), 1]], GOOD_CARD, "floor cushions and a bakhoor burner"));
  keep(await placeOrder(maria, [[product(alnoor, "ANE-MSE-02"), 1], [product(falcon, "FSH-YOG-MAT"), 1], [product(oud, "OAB-BKH-250"), 2]], { method: "UPI", upiId: "maria.santos@enbd" }, "a mouse, a yoga mat and bakhoor"));
  keep(await placeOrder(omar, [[product(oud, "OAB-PRF-50"), 1], [product(sahara, "SHL-RUG-160"), 1]], GOOD_CARD, "perfume and a kilim rug"));
  keep(await placeOrder(hamad, [[product(alnoor, "ANE-EAR-01"), 2], [product(alnoor, "ANE-CHG-65"), 1]], GOOD_CARD, "two pairs of earbuds and a charger"));
  // a declined card, then a successful retry on the same orders
  const retry = await placeOrder(rahul, [[product(sahara, "SHL-TWL-6"), 1], [product(oud, "OAB-GFT-SET"), 1]], DECLINED_CARD, "towels and a gift set (card declined)");
  await api("/api/payments/checkout", { method: "POST", token: rahul.token, body: { orderIds: retry.orders.map((o) => o.id), ...GOOD_CARD, cardHolder: rahul.fullName } });
  retry.orders.forEach((o) => paid.push(o));
  log(`${rahul.fullName}: paid the declined orders with another card`);
  // left waiting for payment: these show under "Awaiting payment" for 30 minutes
  await placeOrder(maria, [[product(falcon, "FSH-BOT-1L"), 1]], null, "a bottle, not paid yet");
  // bought and then cancelled by the customer (refunded)
  const cancelled = await placeOrder(aisha, [[product(sahara, "SHL-MUG-4"), 1]], GOOD_CARD, "mugs she will cancel");
  await api(`/api/orders/${cancelled.orders[0].id}/cancel`, { method: "PUT", token: aisha.token, body: { reason: "I ordered by mistake" } });
  log(`${aisha.fullName}: cancelled ${cancelled.orders[0].orderNumber}; the payment was refunded`);

  step("Order history (spread over the last three months by scripts/demo-backdate.sql)");
  // a small deterministic random generator, so every run creates the same history
  let seed = 20261014;
  const random = () => {
    seed = (seed * 1103515245 + 12345) % 2147483648;
    return seed / 2147483648;
  };
  const listed = stores.flatMap((store) => store.products.filter((p) => p.listed));
  const methods = [GOOD_CARD, GOOD_CARD, GOOD_CARD, { method: "COD" }, GOOD_CARD, { method: "UPI", upiId: "demo.customer@fab" }];
  let history = 0;
  for (let i = 0; i < 120; i++) {
    const customer = pick(customers, Math.floor(random() * customers.length));
    const wanted = 1 + Math.floor(random() * 3);
    const lines = [];
    for (let j = 0; j < wanted; j++) {
      const candidate = listed[Math.floor(random() * listed.length)];
      if (lines.some(([p]) => p.id === candidate.id)) continue;
      const fresh = await api(`/api/products/${candidate.id}`);
      if (fresh.availableQuantity < 4) continue;
      lines.push([candidate, 1 + Math.floor(random() * 2)]);
    }
    if (!lines.length) continue;
    const payment = pick(methods, i);
    const total = lines.reduce((sum, [p, quantity]) => sum + p.price * quantity, 0);
    const result = await api("/api/orders", {
      method: "POST",
      token: customer.token,
      expect: [200, 201],
      body: { items: lines.map(([p, quantity]) => ({ productId: p.id, quantity })), address: customer.address },
    });
    await api("/api/payments/checkout", {
      method: "POST",
      token: customer.token,
      body: { orderIds: result.orders.map((o) => o.id), ...(payment.method === "COD" && total > 4500 ? GOOD_CARD : payment), cardHolder: customer.fullName },
    });
    result.orders.forEach((o) => paid.push({ ...o, past: true }));
    history += result.orders.length;
  }
  log(`${history} more orders from returning customers`);

  step("Expenses and budgets (in proportion to each store's sales)");
  // Fixed costs are the same every month; marketing, shipping and packaging follow sales. demo-backdate.sql spreads the
  // orders over the last HISTORY_DAYS with sales growing month by month, so a young store makes a small loss in its
  // first month and a profit after that, like a real one.
  const fixedCosts = [
    ["SALARIES", 0.55, "Staff salaries", "Payroll", 25],
    ["RENT", 0.35, "Store room rent", "Landlord", 1],
    ["UTILITIES", 0.06, "Electricity and water", "DEWA", 12],
    ["SOFTWARE", 0.04, "Accounting software", "Zoho", 5],
  ];
  const variableCosts = [
    ["MARKETING", 0.5, "Instagram and Google ads", "Meta", 8],
    ["SHIPPING", 0.32, "Courier invoices", "Aramex", 15],
    ["PACKAGING", 0.18, "Boxes, tape and tissue paper", "Gulf Packaging", 18],
  ];
  // Share of the orders older than `days` under the age curve of demo-backdate.sql (age = 3 + 160 * (1 - sqrt(random()))).
  const olderThan = (days) => (1 - Math.min(Math.max((days - 3) / HISTORY_DAYS, 0), 1)) ** 2;
  const ageOf = (date) => Math.max(0, (Date.now() - date.getTime()) / 86400000);
  const monthShare = (delta) => {
    const now = new Date();
    const start = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + delta, 1));
    const end = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + delta + 1, 1));
    return olderThan(ageOf(end)) - olderThan(ageOf(start));
  };
  const budgetFactor = { SALARIES: 1.1, RENT: 1.05, MARKETING: 0.85, SHIPPING: 1.4, UTILITIES: 1.3 };
  const costOf = Object.fromEntries(stores.flatMap((store) => store.products.map((p) => [p.id, Number(p.costPrice)])));
  const months = Math.ceil(HISTORY_DAYS / 30.4);
  for (const store of stores) {
    const grossProfit = paid
      .filter((o) => o.sellerId === store.id)
      .flatMap((o) => o.items)
      .reduce((sum, item) => sum + (Number(item.unitPrice) - (costOf[item.productId] || 0)) * item.quantity, 0);
    // running the store costs about 42% of what it earns over the whole period
    const fixed = Math.max(250, (grossProfit / (HISTORY_DAYS / 30.4)) * 0.26);
    const variable = grossProfit * 0.16;
    for (let delta = -months + 1; delta <= 0; delta++) {
      const month = monthOffset(delta);
      const lines = [
        ...fixedCosts.map(([category, weight, ...rest]) => [category, fixed * weight, ...rest]),
        ...variableCosts.map(([category, weight, ...rest]) => [category, Math.max(15, variable * monthShare(delta) * weight), ...rest]),
      ];
      for (const [i, [category, planned, description, vendor, day]] of lines.entries()) {
        const amount = Math.max(15, Math.round(planned * (0.92 + ((i - delta) % 4) * 0.05)));
        if (delta === 0 && budgetFactor[category]) {
          // this month's budgets: marketing is set a little low, so the budget alerts have something to show
          await api("/api/seller/budgets", { method: "PUT", token: store.token, body: { month, category, amount: Math.max(50, Math.round(amount * budgetFactor[category])) } });
        }
        if (`${month}-${String(day).padStart(2, "0")}` > daysFromNow(0)) continue; // not due yet this month
        await api("/api/seller/expenses", {
          method: "POST",
          token: store.token,
          expect: [200, 201],
          body: { category, amount, description: `${description}, ${month}`, vendor, reference: `INV-${month.replace("-", "")}-${i + 1}`, expenseDate: dayInMonth(month, day) },
        });
      }
    }
    log(`${store.storeName}: AED ${Math.round(fixed).toLocaleString("en")} of fixed costs a month, the rest grows with sales`);
  }

  step("Shipping and delivery");
  const carriers = ["Aramex", "Emirates Post", "DHL Express", "Quiqup", "iMile"];
  const storeById = Object.fromEntries(stores.map((s) => [s.id, s]));
  let shipped = 0;
  let delivered = 0;
  for (const [index, order] of paid.entries()) {
    const store = storeById[order.sellerId];
    const current = await api(`/api/seller/orders/${order.id}`, { token: store.token });
    if (current.status !== "CONFIRMED") continue;
    if (!order.past && index % 6 === 5) continue; // a few stay "to ship" so the seller has work today
    await api(`/api/seller/orders/${order.id}/ship`, { method: "PUT", token: store.token, body: { carrier: pick(carriers, index), trackingNumber: `${pick(["AWB", "EP", "DHL", "QQ", "IM"], index)}${String(48210377 + index * 7919).slice(0, 9)}` } });
    shipped++;
    if (order.past || index % 4 !== 3) {
      await api(`/api/seller/orders/${order.id}/deliver`, { method: "PUT", token: store.token });
      delivered++;
    }
  }
  log(`${shipped} orders shipped, ${delivered} delivered`);

  step("Reviews and replies");
  const comments = {
    5: [
      "Exactly as described and delivered the next day. Will buy again.",
      "Beautiful quality, my family loved it.",
      "Great value, the store answered all my questions quickly.",
      "Arrived well packed and earlier than promised. Very happy.",
      "Second time ordering from this store and again no complaints.",
      "Better than the one I saw at the mall for more money.",
      "Bought it as an Eid gift and it was a big hit.",
    ],
    4: [
      "Very good, the box was a little dented but the product is perfect.",
      "Works well. Delivery took two days to Abu Dhabi.",
      "Good quality for the price. The instructions could be clearer.",
      "Nice product, slightly smaller than I imagined.",
      "Does the job well. Five stars if the delivery were a day faster.",
      "Solid and well made. The colour is a little different from the photo.",
    ],
    3: [
      "It is fine for the price. I expected the colour to be a bit darker.",
      "Average. It works, but the finish is not as premium as it looks online.",
      "OK for daily use. It took four days to arrive.",
      "Decent, but it runs small. Check the measurements before ordering.",
      "Not bad, not great. The packaging was damaged but the item was fine.",
      "Does what it says. I expected a bit more for this price.",
    ],
    2: [
      "Smaller than I thought from the photos. The store offered to help though.",
      "Stopped working properly after a week. Waiting for the store to replace it.",
      "The quality does not match the price. The store answered quickly at least.",
      "Delivery was late and one piece was missing; the store sent it later.",
    ],
  };
  const ratings = [5, 4, 5, 3, 5, 4, 2, 5, 4, 5];
  let reviews = 0;
  for (const [i, customer] of customers.entries()) {
    const pending = await api("/api/reviews/pending", { token: customer.token });
    for (const [j, item] of pending.entries()) {
      if ((i + j) % 5 === 4) continue; // not everyone reviews everything
      const rating = pick(ratings, i * 3 + j);
      // Offset by product and customer, so two customers never post the same words on one product.
      const comment = pick(comments[rating], item.productId + i);
      await api("/api/reviews", { method: "POST", token: customer.token, expect: [200, 201], body: { orderId: item.orderId, productId: item.productId, rating, comment } });
      reviews++;
    }
  }
  const thanks = [
    "Thank you, {name}! We are happy you like it.",
    "Thank you for the kind words, {name}. See you again soon!",
    "Thanks {name}, we have shared your feedback with the whole team.",
    "Glad it arrived in good shape, {name}. Enjoy it!",
  ];
  const apologies = [
    "Thank you for the honest feedback, {name}. We have sent you a message to make it right.",
    "Sorry it was not perfect, {name}. Message us and we will exchange it.",
    "Thanks for telling us, {name}. We are checking this with our supplier.",
  ];
  let replies = 0;
  for (const store of stores) {
    const list = await api("/api/seller/reviews?size=20", { token: store.token });
    for (const [i, review] of list.reviews.content.entries()) {
      if (i % 2 === 1 && review.rating >= 4) continue;
      const name = review.customerName.split(" ")[0];
      const text = pick(review.rating <= 3 ? apologies : thanks, review.id).replace("{name}", name);
      await api(`/api/seller/reviews/${review.id}/reply`, { method: "PUT", token: store.token, body: { reply: text } });
      replies++;
    }
  }
  log(`${reviews} reviews, ${replies} replies from the stores`);

  step("Messages between customers and stores");
  const threads = [
    [maria, alnoor, product(alnoor, "ANE-MON-27"), "Hello, does this monitor work with a MacBook Air over USB-C?", "Yes, one USB-C cable carries the picture and charges the MacBook at 65 W."],
    [omar, falcon, product(falcon, "FSH-PAD-PRO"), "Is the racket suitable for a beginner?", "It is quite stiff. For your first year we would suggest a softer core; happy to recommend one."],
    [fatima, oud, product(oud, "OAB-OUD-12"), "Can you gift wrap the oud oil with a card?", "Of course, gift wrapping is free. Write the card message here and we will add it."],
    [hamad, sahara, product(sahara, "SHL-RUG-160"), "Do you have this rug in 200 x 300 cm?", null],
  ];
  for (const [customer, store, item, question, answer] of threads) {
    const started = await api("/api/conversations", { method: "POST", token: customer.token, expect: [200, 201], body: { sellerId: store.id, message: question, productId: item.id, productName: item.name } });
    if (answer) await api(`/api/conversations/${started.conversation.id}/messages`, { method: "POST", token: store.token, expect: [200, 201], body: { message: answer } });
  }
  const spam = await api("/api/conversations", { method: "POST", token: rahul.token, expect: [200, 201], body: { sellerId: falcon.id, message: "Where is my order? It says shipped two days ago." } });
  await api(`/api/conversations/${spam.conversation.id}/messages`, { method: "POST", token: falcon.token, expect: [200, 201], body: { message: "Please pay the courier directly on WhatsApp and we will deliver faster." } });
  await api(`/api/conversations/${spam.conversation.id}/report`, { method: "POST", token: rahul.token, body: { reason: "The store asked me to pay outside Nexora" } });
  log(`${threads.length + 1} conversations, one reported to Nexora support`);

  step("Today's orders (still waiting for the stores)");
  const todays = [
    [aisha, alnoor, [["ANE-HUB-07", 1], ["ANE-MSE-02", 1]], GOOD_CARD, "a hub and a mouse to ship today"],
    [omar, alnoor, [["ANE-KBD-01", 1]], { method: "COD" }, "a keyboard to ship today, cash on delivery"],
    [maria, sahara, [["SHL-LAN-L", 1]], GOOD_CARD, "a lantern to ship today"],
    [hamad, alnoor, [["ANE-PWR-20", 1]], null, "a power bank, not paid yet"],
  ];
  for (const [customer, store, items, payment, label] of todays) {
    const lines = [];
    for (const [sku, quantity] of items) {
      const fresh = await api(`/api/products/${product(store, sku).id}`);
      if (fresh.availableQuantity > quantity) lines.push([product(store, sku), quantity]);
    }
    if (lines.length) await placeOrder(customer, lines, payment, label);
  }

  step("Store settings");
  await api("/api/seller/store/settings", { method: "PUT", token: oud.token, body: { vacationMode: false, lowStockAlerts: true } });

  if (process.env.ADMIN_PASSWORD) {
    step("Marketplace announcement (administrator)");
    const admin = await api("/api/admin/auth/login", { method: "POST", body: { username: process.env.ADMIN_USERNAME || "admin", password: process.env.ADMIN_PASSWORD } });
    await api("/api/admin/announcements", {
      method: "POST",
      token: admin.accessToken,
      expect: [200, 201],
      body: { title: "Free delivery across the UAE.", message: "Every store on Nexora delivers free of charge, from Ras Al Khaimah to Abu Dhabi.", audience: "EVERYONE", level: "INFO", active: true },
    });
    log("published to everyone");
  }

  clearInterval(keepAwake);
  console.log(`\nDone: ${calls} API calls.`);
  console.log(`Sign in with any of these and the password ${PASSWORD}:`);
  console.log(`  Seller Central  ${STORES.map((s) => s.username).join(", ")}`);
  console.log(`  Customers       ${CUSTOMERS.map((c) => c.username).join(", ")}`);
}

main().catch((error) => {
  console.error(`\nDemo data stopped: ${error.message}`);
  process.exit(1);
});
