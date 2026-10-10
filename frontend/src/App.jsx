import { useCallback, useEffect, useMemo, useState } from "react";
import "./App.css";
import { LayoutDashboard, Package, ShoppingCart, CreditCard, Users, Bell, Settings, RefreshCw, Plus, Search, ChevronDown, ArrowUpRight, Sparkles, Pencil, Trash2, X, Menu, Check, ShieldCheck, CircleHelp, LogOut, UserRound, Activity } from "lucide-react";

const API_BASE = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");
const money = (n) => new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(Number(n) || 0);
const pick = (obj, keys, fallback = "") => {
  for (const key of keys) if (obj?.[key] !== undefined && obj?.[key] !== null && obj?.[key] !== "") return obj[key];
  return fallback;
};
const asArray = (data) => Array.isArray(data) ? data : Array.isArray(data?.content) ? data.content : Array.isArray(data?.data) ? data.data : Array.isArray(data?.items) ? data.items : [];
const initials = (name = "User") => name.split(/[\s@._-]+/).filter(Boolean).slice(0, 2).map((x) => x[0]).join("").toUpperCase();
const iconMap = { Overview: LayoutDashboard, Products: Package, Orders: ShoppingCart, Payments: CreditCard, Customers: Users, Notifications: Bell, Settings: Settings };

function App() {
  const [darkMode, setDarkMode] = useState(() => localStorage.getItem("eop_theme") === "dark");
  useEffect(() => { localStorage.setItem("eop_theme", darkMode ? "dark" : "light"); }, [darkMode]);
  const [page, setPage] = useState("Overview");
  const [products, setProducts] = useState([]);
  const [orders, setOrders] = useState([]);
  const [payments, setPayments] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [token, setToken] = useState(() => localStorage.getItem("eop_token") || "");
  const [user, setUser] = useState(() => {
    try { return JSON.parse(localStorage.getItem("eop_user") || "null"); } catch { return null; }
  });
  const [loading, setLoading] = useState(false);
  const [apiState, setApiState] = useState("checking");
  const [notice, setNotice] = useState(null);
  const [search, setSearch] = useState("");
  const [showAuth, setShowAuth] = useState(false);
  const [authMode, setAuthMode] = useState("login");
  const [authForm, setAuthForm] = useState({ username: "", email: "", password: "" });
  const [showProductForm, setShowProductForm] = useState(false);
  const [productForm, setProductForm] = useState({ name: "", description: "", price: "", stock: "" });
  const [saving, setSaving] = useState(false);
  const [selectedProduct, setSelectedProduct] = useState(null);
  const [mobileMenu, setMobileMenu] = useState(false);

  const request = useCallback(async (path, options = {}) => {
    const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
    if (token) headers.Authorization = `Bearer ${token}`;
    const response = await fetch(`${API_BASE}${path}`, { ...options, headers });
    const raw = await response.text();
    let body = null;
    try { body = raw ? JSON.parse(raw) : null; } catch { body = raw; }
    if (!response.ok) {
      if (response.status === 401 && path !== "/api/auth/login") {
        setApiState("unauthorized");
      }
      throw new Error(body?.message || body?.error || (typeof body === "string" && body) || `Request failed (${response.status})`);
    }
    return body;
  }, [token]);

  const loadProducts = useCallback(async () => {
    try { setProducts(asArray(await request("/api/products"))); } catch (e) { setProducts([]); throw e; }
  }, [request]);
  const loadOrders = useCallback(async () => {
try {
      const data = await request("/api/orders");
      setOrders(asArray(data));
    } catch { setOrders([]); }
  }, [request, token]);
  const loadPayments = useCallback(async () => {
try {
      const data = await request("/api/payments");
      setPayments(asArray(data));
    } catch { setPayments([]); }
  }, [request, token]);
  const loadNotifications = useCallback(async () => {
try {
      const data = await request(`/api/notifications/user/${user?.id || user?.userId || 1}`);
      setNotifications(asArray(data));
    } catch { setNotifications([]); }
  }, [request, token]);

  const refresh = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      const loadedProducts = asArray(await request("/api/products"));
      setProducts(loadedProducts);
      if (!quiet) setNotice({ type: "success", text: "Data refreshed successfully. " + loadedProducts.length + " products loaded." });
      await Promise.all([loadOrders(), loadPayments(), loadNotifications()]);
      setApiState("online");
    } catch {
      setApiState("offline");
    } finally {
      setLoading(false);
    }
  }, [loadProducts, loadOrders, loadPayments, loadNotifications]);

  useEffect(() => { refresh(true); }, [refresh]);
  useEffect(() => {
    if (!notice) return;
    const id = setTimeout(() => setNotice(null), 4200);
    return () => clearTimeout(id);
  }, [notice]);

  const productStats = useMemo(() => {
    const totalValue = products.reduce((sum, p) => sum + (Number(p.price) || 0) * (Number(p.stockQuantity ?? p.stock ?? p.quantity) || 0), 0);
    const lowStock = products.filter((p) => Number(p.stockQuantity ?? p.stock ?? p.quantity ?? 999) < 10).length;
    return { count: products.length, totalValue, lowStock };
  }, [products]);
  const filteredProducts = useMemo(() => products.filter((p) => {
    const q = search.toLowerCase();
    return [p.name, p.title, p.description, p.category, p.id, p.sku].some((v) => String(v ?? "").toLowerCase().includes(q));
  }), [products, search]);
  const recentOrders = orders.slice(0, 6);
  const displayName = pick(user, ["fullName", "name", "username", "email"], "Workspace member");
  const orderRevenue = orders.reduce((sum, o) => sum + Number(pick(o, ["totalAmount", "total", "amount", "price"], 0)), 0);

  async function submitAuth(event) {
    event.preventDefault();
    setSaving(true);
    try {
      const endpoint = authMode === "login" ? "/api/auth/login" : "/api/auth/register";
      const result = await request(endpoint, { method: "POST", body: JSON.stringify(authForm) });
      const newToken = pick(result, ["token", "accessToken", "jwt", "access_token"]);
      if (newToken) {
        localStorage.setItem("eop_token", newToken);
        setToken(newToken);
      }
      const profile = result?.user || result?.data || result;
      localStorage.setItem("eop_user", JSON.stringify(profile));
      setUser(profile);
      setShowAuth(false);
      setNotice({ type: "success", text: authMode === "login" ? "Welcome back. You are signed in." : "Account created successfully." });
    } catch (e) {
      setNotice({ type: "error", text: e.message || "Authentication failed." });
    } finally { setSaving(false); }
  }

  function signOut() {
    localStorage.removeItem("eop_token");
    localStorage.removeItem("eop_user");
    setToken(""); setUser(null); setOrders([]); setPayments([]); setNotifications([]);
    setNotice({ type: "success", text: "You have been signed out." });
    setPage("Overview");
  }

  async function saveProduct(event) {
    event.preventDefault();
    setSaving(true);
    const payload = { name: productForm.name.trim(), description: productForm.description.trim(), price: Number(productForm.price), stockQuantity: Number(productForm.stock), sku: selectedProduct?.sku || (productForm.sku || ("SKU-" + Date.now())) };
    try {
      if (selectedProduct) {
        await request(`/api/products/${selectedProduct.id}`, { method: "PUT", body: JSON.stringify({ ...selectedProduct, ...payload }) });
        setNotice({ type: "success", text: "Product updated." });
      } else {
        await request("/api/products", { method: "POST", body: JSON.stringify(payload) });
        setNotice({ type: "success", text: "Product created." });
      }
      setShowProductForm(false); setSelectedProduct(null);
      setProductForm({ name: "", description: "", price: "", stock: "" });
      await loadProducts();
    } catch (e) { setNotice({ type: "error", text: `Could not save product: ${e.message}` }); }
    finally { setSaving(false); }
  }

  const [pendingDelete, setPendingDelete] = useState(null);



  async function deleteProduct(product) {
    setPendingDelete(product);
  }

  async function confirmDeleteProduct() {
    const product = pendingDelete;
    if (!product) return;
    setPendingDelete(null);
    try {
      await request(`/api/products/${product.id}`, { method: "DELETE" });
      setProducts((prev) => prev.filter((p) => p.id !== product.id));
      setNotice({ type: "success", text: "Product deleted." });
    } catch (e) { setNotice({ type: "error", text: `Could not delete product: ${e.message}` }); }
  }

  async function markOrderPaid(order) {
    if (!token) { setAuthMode("login"); setShowAuth(true); return; }
    try {
      const updated = await request(`/api/orders/${order.id}/paid`, { method: "PATCH" });
      setOrders((prev) => prev.map((o) => String(o.id) === String(order.id) ? { ...o, ...updated } : o));
      setNotice({ type: "success", text: `Order #${order.id} marked as paid.` });
    } catch (e) { setNotice({ type: "error", text: `Could not update order: ${e.message}` }); }
  }

  async function createOrder(product) {
    if (!token) {
      setAuthMode("login"); setShowAuth(true);
      setNotice({ type: "info", text: "Sign in first to place an order." });
      return;
    }
    const quantity = 1;
    const payload = { productId: product.id, quantity, items: [{ productId: product.id, quantity }], totalAmount: Number(product.price) || 0 };
    try {
      await request("/api/orders", { method: "POST", body: JSON.stringify(payload) });
      setNotice({ type: "success", text: "Order submitted successfully." });
      setPage("Orders");
      await loadOrders();
    } catch (e) {
      setNotice({ type: "error", text: `Order was not created: ${e.message}. Check the order service request format.` });
    }
  }

  function editProduct(product) {
    setSelectedProduct(product);
    setProductForm({ name: pick(product, ["name", "title"]), description: pick(product, ["description"]), price: String(pick(product, ["price"], "")), stock: String(pick(product, ["stockQuantity", "stock", "quantity"], 0)) });
    setShowProductForm(true);
  }

  const statusClass = (value) => {
    const s = String(value || "pending").toLowerCase();
    if (["paid", "completed", "delivered", "success", "active"].some((x) => s.includes(x))) return "status-good";
    if (["cancel", "fail", "error", "rejected"].some((x) => s.includes(x))) return "status-bad";
    return "status-warn";
  };

  return (
    <div className={`app-shell ${darkMode ? "dark-mode" : ""}`}>
      <aside className={`sidebar ${mobileMenu ? "sidebar-open" : ""}`}>
        <div className="brand">
          <div className="brand-mark"><span>◈</span></div>
          <div><div className="brand-name">NEXORA</div><div className="brand-caption">COMMERCE CLOUD</div></div>
          <button className="mobile-close" onClick={() => setMobileMenu(false)} aria-label="Close menu">×</button>
        </div>
        <div className="workspace-switch"><div className="workspace-icon">N</div><div className="workspace-text"><strong>Northstar Store</strong><span>Enterprise workspace</span></div><span className="chevron">⌄</span></div>
        <div className="nav-label">WORKSPACE</div>
        <nav className="nav-list">
          {["Overview", "Products", "Orders", "Payments", "Customers", "Notifications"].map((item) => (
            <button key={item} className={`nav-item ${page === item ? "active" : ""}`} onClick={() => { setPage(item); setMobileMenu(false); }}>
              <span className="nav-icon">{(() => { const Icon = iconMap[item]; return <Icon size={18} strokeWidth={1.8} />; })()}</span><span>{item}</span>
              {item === "Orders" && orders.length > 0 && <span className="nav-count">{orders.length}</span>}
              {item === "Notifications" && notifications.length > 0 && <span className="nav-dot" />}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="upgrade-card"><div className="upgrade-orb">✦</div><strong>Built to scale.</strong><p>Your commerce operations, connected in one place.</p><div className="upgrade-progress"><span /></div><small>WORKSPACE OVERVIEW <b>LIVE</b></small></div>
          <button className={`nav-item ${page === "Settings" ? "active" : ""}`} onClick={() => setPage("Settings")}><span className="nav-icon">{(() => { const Icon = iconMap.Settings; return <Icon size={18} strokeWidth={1.8} />; })()}</span><span>Settings</span></button>
          <div className="sidebar-footer"><div className="avatar avatar-small">{initials(displayName)}</div><div className="footer-user"><strong>{displayName}</strong><span>{token ? "Authenticated session" : "Guest workspace"}</span></div><button className="more-button" onClick={() => token ? signOut() : (setAuthMode("login"), setShowAuth(true))} title={token ? "Sign out" : "Sign in"}>{token ? "↗" : "→"}</button></div>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <div className="topbar-left"><button className="mobile-menu" onClick={() => setMobileMenu(true)} aria-label="Open menu">☰</button><div className="breadcrumbs"><span>Workspace</span><b>/</b><strong>{page}</strong></div></div>
          <div className="topbar-actions">
            <div className={`connection-pill ${apiState}`}><span className="connection-dot" />{apiState === "online" ? "API connected" : apiState === "checking" ? "Connecting…" : apiState === "unauthorized" ? "Sign-in required" : "API unavailable"}</div>
            <button className="icon-button" title="Refresh data" onClick={() => refresh()} disabled={loading}>{loading ? <span className="spinner" /> : "↻"}</button>
            <button className="help-button" onClick={() => setNotice({ type: "info", text: "Nexora Commerce Cloud — enterprise order platform demo." })}>? <span>Help</span></button>
            {token ? <button className="profile-button" onClick={signOut}><span className="avatar">{initials(displayName)}</span><span className="profile-label">{displayName}</span><span>⌄</span></button> : <button className="button button-primary button-small" onClick={() => { setAuthMode("login"); setShowAuth(true); }}>Sign in <span>↗</span></button>}
          </div>
        </header>

        <div className="content-wrap">
          {page === "Overview" && <section className="page-content">
            <div className="welcome-row"><div><div className="eyebrow"><span className="eyebrow-line" /> OPERATIONS CENTER</div><h1>Good to see you<span className="title-period">.</span></h1><p className="page-subtitle">Your commerce operation at a glance. Here's what's happening today.</p></div><div className="welcome-actions"><button className="button button-secondary" onClick={() => refresh()}><span>↻</span> Refresh data</button><button className="button button-primary" onClick={() => { setSelectedProduct(null); setProductForm({ name: "", description: "", price: "", stock: "" }); setShowProductForm(true); }}>＋ Add product</button></div></div>
            {apiState !== "online" && <div className={`system-banner ${apiState === "checking" ? "banner-neutral" : "banner-warn"}`}><span className="banner-symbol">{apiState === "checking" ? "◌" : "!"}</span><div><strong>{apiState === "checking" ? "Connecting to your services" : "Backend connection needs attention"}</strong><p>{apiState === "checking" ? "Fetching live product and operations data." : "The dashboard is ready, but live data requires the Spring Boot API gateway to be running and reachable at this origin."}</p></div><button onClick={() => refresh()}>{apiState === "checking" ? "Checking…" : "Retry connection"} →</button></div>}
            <div className="hero-panel"><div className="hero-copy"><div className="hero-tag"><span className="pulse-dot" /> COMMERCE INTELLIGENCE</div><h2>Every order.<br /><em>One clear picture.</em></h2><p>Make smarter decisions with a unified view of products, orders and payments — built for teams that move fast.</p><button className="hero-link" onClick={() => setPage("Products")}>Explore product catalog <span>↗</span></button><div className="hero-footnote"><span className="hero-mini-dot" /> LIVE WORKSPACE <span className="hero-foot-sep">/</span> REAL-TIME DATA</div></div><div className="hero-art" aria-hidden="true"><div className="art-grid" /><div className="art-ring ring-one" /><div className="art-ring ring-two" /><div className="art-ring ring-three" /><div className="art-core"><span>✳</span></div><div className="art-float float-top"><span className="float-icon green">↗</span><div><small>CATALOG VALUE</small><strong>{money(productStats.totalValue)}</strong></div></div><div className="art-float float-bottom"><span className="float-icon violet">▦</span><div><small>ACTIVE PRODUCTS</small><strong>{productStats.count} items</strong></div><span className="float-status">LIVE</span></div><div className="art-orbit-dot dot-one" /><div className="art-orbit-dot dot-two" /></div></div>
            <div className="section-heading"><div><h2>Performance snapshot</h2><p>Key indicators across your workspace</p></div><span className="period-label"><span className="period-dot" /> ALL-TIME DATA</span></div>
            <div className="metrics-grid">
              <Metric label="Catalog products" value={productStats.count.toLocaleString("en-IN")} change="LIVE INVENTORY" icon="▦" tone="purple" foot={`${productStats.lowStock} items with low stock`} />
              <Metric label="Orders recorded" value={orders.length.toLocaleString("en-IN")} change={"ALL-TIME DATA"} icon="⇄" tone="blue" foot="Across all available statuses" />
              <Metric label="Order value" value={money(orderRevenue)} change="ORDER TOTALS" icon="↗" tone="green" foot="Sum of loaded order amounts" />
              <Metric label="Payment records" value={payments.length.toLocaleString("en-IN")} change={"PAYMENT SERVICE"} icon="◈" tone="orange" foot="Payment records returned by API" />
            </div>
            <div className="overview-lower">
              <div className="panel recent-panel"><div className="panel-heading"><div><h3>Recent orders</h3><p>Your latest order activity</p></div><button className="text-link" onClick={() => setPage("Orders")}>View all <span>→</span></button></div>
                {recentOrders.length ? <div className="table-scroll"><table><thead><tr><th>ORDER</th><th>AMOUNT</th><th>STATUS</th><th>DATE</th></tr></thead><tbody>{recentOrders.map((o, i) => <tr key={o.id ?? i}><td><span className="order-id">#{String(pick(o, ["orderNumber", "id"], "—")).slice(0, 12)}</span></td><td className="amount-cell">{money(pick(o, ["totalAmount", "total", "amount", "price"], 0))}</td><td><span className={`status-chip ${statusClass(pick(o, ["status", "paymentStatus"]))}`}>{pick(o, ["status", "paymentStatus"], "Pending")}</span></td><td className="date-cell">{formatDate(pick(o, ["createdAt", "orderDate", "createdDate"]))}</td></tr>)}</tbody></table></div> : <EmptyState icon="⇄" title="No orders to show yet" text={token ? "Orders from your connected service will appear here." : "Sign in to view order activity from your workspace."} action={!token ? () => { setAuthMode("login"); setShowAuth(true); } : null} actionText="Sign in" />}
              </div>
              <div className="panel inventory-panel"><div className="panel-heading"><div><h3>Inventory health</h3><p>Catalog availability at a glance</p></div><span className="mini-icon">▦</span></div><div className="inventory-big"><strong>{productStats.count}</strong><span>products in catalog</span></div><div className="inventory-track"><span style={{ width: `${productStats.count ? Math.max(4, 100 - (productStats.lowStock / productStats.count) * 100) : 0}%` }} /></div><div className="inventory-legend"><span><i className="legend-dot good" /> Healthy stock</span><strong>{Math.max(0, productStats.count - productStats.lowStock)}</strong></div><div className="inventory-legend"><span><i className="legend-dot warn" /> Low stock (&lt;10)</span><strong>{productStats.lowStock}</strong></div><button className="inventory-link" onClick={() => setPage("Products")}>Manage inventory <span>→</span></button></div>
            </div>
            <div className="bottom-note"><span>✦</span><p><strong>Designed for clarity.</strong> Nexora brings your product, order and payment workflows together in one workspace.</p><span className="note-version">NEXORA CLOUD · 1.0</span></div>
          </section>}

          {page === "Products" && <section className="page-content"><PageHeader eyebrow="CATALOG MANAGEMENT" title="Products" subtitle="Manage your catalog, pricing and inventory from one place." action={<button className="button button-primary" onClick={() => { setSelectedProduct(null); setProductForm({ name: "", description: "", price: "", stock: "" }); setShowProductForm(true); }}>＋ Add product</button>} /><div className="catalog-summary"><div><span className="summary-label">TOTAL PRODUCTS</span><strong>{productStats.count}</strong></div><div><span className="summary-label">CATALOG VALUE</span><strong>{money(productStats.totalValue)}</strong></div><div><span className="summary-label">LOW STOCK</span><strong className={productStats.lowStock ? "text-warning" : ""}>{productStats.lowStock}</strong></div><div className="summary-action"><button className="button button-secondary" onClick={() => refresh()}>↻ Sync catalog</button></div></div><div className="panel catalog-panel"><div className="catalog-toolbar"><div><h3>Product catalog</h3><p>{filteredProducts.length} results</p></div><div className="search-box"><span>⌕</span><input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search products…" aria-label="Search products" /><kbd>⌘ K</kbd></div></div>{filteredProducts.length ? <div className="table-scroll"><table className="catalog-table"><thead><tr><th>PRODUCT</th><th>SKU / ID</th><th>PRICE</th><th>STOCK</th><th>STATUS</th><th className="right-align">ACTIONS</th></tr></thead><tbody>{filteredProducts.map((p, i) => { const stock = Number(pick(p, ["stockQuantity", "stock", "quantity"], 0)); return <tr key={p.id ?? i}><td><div className="product-cell"><div className={`product-thumb thumb-${i % 5}`}>{String(pick(p, ["name", "title"], "P")).slice(0, 1).toUpperCase()}</div><div><strong>{pick(p, ["name", "title"], "Untitled product")}</strong><span>{pick(p, ["description", "category"], "Catalog item")}</span></div></div></td><td className="muted-mono">{pick(p, ["sku", "id"], "—")}</td><td className="amount-cell">{money(p.price)}</td><td>{stock}</td><td><span className={`status-chip ${stock < 10 ? "status-warn" : "status-good"}`}>{stock < 10 ? "Low stock" : "In stock"}</span></td><td className="right-align"><button className="table-action" onClick={() => editProduct(p)} title="Edit product">✎</button><button className="table-action danger-action" onClick={() => deleteProduct(p)} title="Delete product">×</button><button className="table-action" onClick={() => createOrder(p)} title="Place order">↗</button></td></tr>; })}</tbody></table></div> : <EmptyState icon="▦" title={search ? "No matching products" : "Your catalog is ready for its first product"} text={search ? "Try a different search term." : apiState === "offline" ? "Start the product service and API gateway, then refresh." : "Add a product to start building your live catalog."} action={search ? () => setSearch("") : () => setShowProductForm(true)} actionText={search ? "Clear search" : "Add first product"} />}</div></section>}

          {page === "Orders" && <section className="page-content"><PageHeader eyebrow="ORDER OPERATIONS" title="Orders" subtitle="Track order activity and monitor fulfilment status." action={<button className="button button-secondary" onClick={() => refresh()}>↻ Refresh</button>} /><div className="metrics-grid compact-metrics"><Metric label="Total orders" value={orders.length} icon="⇄" tone="purple" foot="Loaded from order service" /><Metric label="Order value" value={money(orderRevenue)} icon="↗" tone="green" foot="Combined order totals" /><Metric label="Awaiting action" value={orders.filter(o => !/complete|deliver|cancel/i.test(String(o.status))).length} icon="◦" tone="orange" foot="Not completed or cancelled" /><Metric label="Session" value={token ? "Active" : "Guest"} icon="♡" tone="blue" foot={token ? "Authenticated" : "Sign in to access orders"} /></div><div className="panel catalog-panel"><div className="panel-heading padded-heading"><div><h3>All orders</h3><p>Most recent records returned by the API</p></div></div>{orders.length ? <div className="table-scroll"><table><thead><tr><th>ORDER ID</th><th>CUSTOMER</th><th>AMOUNT</th><th>STATUS</th><th>CREATED</th><th>ACTIONS</th></tr></thead><tbody>{orders.map((o,i)=><tr key={o.id ?? i}><td className="order-id">#{pick(o,["orderNumber","id"],"—")}</td><td>{pick(o,["customerName","username","userId","customerId"],"—")}</td><td className="amount-cell">{money(pick(o,["totalAmount","total","amount"],0))}</td><td><span className={`status-chip ${statusClass(o.status)}`}>{pick(o,["status"],"Pending")}</span></td><td className="date-cell">{formatDate(pick(o,["createdAt","orderDate","createdDate"]))}</td><td>{! /paid|cancel/i.test(String(o.status || "")) && <button className="button button-secondary button-small" onClick={() => markOrderPaid(o)}>Mark as Paid</button>}</td></tr>)}</tbody></table></div> : <EmptyState icon="⇄" title="No order records available" text={token ? "The order service returned no records, or this endpoint requires a different permission." : "Sign in to access your order operations."} action={!token ? () => { setAuthMode("login"); setShowAuth(true); } : null} actionText="Sign in" />}</div></section>}

          {page === "Payments" && <section className="page-content"><PageHeader eyebrow="FINANCIAL OPERATIONS" title="Payments" subtitle="Review payment records returned by your payment service." action={<button className="button button-secondary" onClick={() => refresh()}>↻ Refresh</button>} /><div className="metrics-grid compact-metrics"><Metric label="Payment records" value={payments.length} icon="◈" tone="purple" foot="Loaded from payment service" /><Metric label="Successful" value={payments.filter(p => /paid|success|complete/i.test(String(p.status))).length} icon="✓" tone="green" foot="Based on returned status" /><Metric label="Pending / other" value={payments.filter(p => !/paid|success|complete/i.test(String(p.status))).length} icon="◦" tone="orange" foot="May need review" /><Metric label="Payment API" value="Connected" icon="▦" tone="blue" foot="Loaded from payment service" /></div><div className="panel catalog-panel"><div className="panel-heading padded-heading"><div><h3>Payment ledger</h3><p>Payment transactions from the connected service</p></div></div>{payments.length ? <div className="table-scroll"><table><thead><tr><th>PAYMENT ID</th><th>ORDER ID</th><th>AMOUNT</th><th>METHOD</th><th>STATUS</th></tr></thead><tbody>{payments.map((p,i)=><tr key={p.id ?? i}><td className="order-id">#{pick(p,["id","paymentId"],"—")}</td><td>{pick(p,["orderId"],"—")}</td><td className="amount-cell">{money(pick(p,["amount","totalAmount"],0))}</td><td>{pick(p,["method","paymentMethod"],"—")}</td><td><span className={`status-chip ${statusClass(p.status)}`}>{pick(p,["status"],"Pending")}</span></td></tr>)}</tbody></table></div> : <EmptyState icon="◈" title="No payment records yet" text="Payment transactions will appear here when the service returns them." />}</div></section>}

          {page === "Customers" && <section className="page-content"><PageHeader eyebrow="CUSTOMER EXPERIENCE" title="Customers" subtitle="Customer details are protected behind your authenticated user service." /><div className="panel customer-panel"><div className="customer-illustration">♡</div><h2>Customer workspace</h2><p>The current backend exposes a secure <code>/api/users/me</code> profile endpoint. A customer directory needs a dedicated paginated users endpoint before it can be displayed safely.</p>{token ? <button className="button button-primary" onClick={async () => { try { const me = await request("/api/users/me"); setUser(me?.user || me); localStorage.setItem("eop_user", JSON.stringify(me?.user || me)); setNotice({ type: "success", text: "Profile refreshed from the user service." }); } catch(e) { setNotice({ type: "error", text: e.message }); } }}>Load my profile ↗</button> : <button className="button button-primary" onClick={() => { setAuthMode("login"); setShowAuth(true); }}>Sign in to view profile ↗</button>}</div></section>}

          {page === "Notifications" && <section className="page-content"><PageHeader eyebrow="ACTIVITY FEED" title="Notifications" subtitle="Updates from your connected notification service." action={<button className="button button-secondary" onClick={() => refresh()}>↻ Refresh</button>} /><div className="panel notification-panel">{notifications.length ? notifications.map((n,i)=><div className="notification-row" key={n.id ?? i}><div className="notification-icon">◉</div><div className="notification-body"><strong>{pick(n,["title","subject","type"],"Workspace update")}</strong><p>{pick(n,["message","content","description"],"Notification received from the service.")}</p><span>{formatDate(pick(n,["createdAt","timestamp","createdDate"]))}</span></div><span className={`status-chip ${statusClass(n.status)}`}>{pick(n,["status"],"New")}</span></div>) : <EmptyState icon="◉" title="You're all caught up" text="Notifications from your connected service will appear here." />}</div></section>}

          {page === "Settings" && <section className="page-content"><PageHeader eyebrow="WORKSPACE PREFERENCES" title="Settings" subtitle="Connection details and account preferences." /><div className="panel settings-card theme-settings-card"><div className="settings-icon">☼</div><h3>Appearance</h3><p>Customize the dashboard appearance. Your preference is saved in this browser.</p><div className="settings-line"><span>Current theme</span><strong>{darkMode ? "Dark" : "Light"}</strong></div><button className="button button-secondary" onClick={() => setDarkMode(v => !v)}>{darkMode ? "Switch to light mode" : "Switch to dark mode"}</button></div><div className="settings-grid"><div className="panel settings-card"><div className="settings-icon">▦</div><h3>API connection</h3><p>Frontend requests are routed through the same origin. In local development, Vite proxies <code>/api</code> to the Spring Cloud Gateway on port 8080.</p><div className="settings-line"><span>Gateway URL</span><strong>{API_BASE || "Same origin / Vite proxy"}</strong></div><div className="settings-line"><span>Connection</span><span className={`status-chip ${apiState === "online" ? "status-good" : "status-warn"}`}>{apiState}</span></div><button className="button button-secondary" onClick={() => refresh()}>Test connection ↗</button></div><div className="panel settings-card"><div className="settings-icon">♡</div><h3>Account & security</h3><p>Authentication uses the existing JWT login and registration endpoints. Your session token is stored in this browser.</p><div className="settings-line"><span>Session</span><strong>{token ? "Signed in" : "Guest"}</strong></div><div className="settings-line"><span>Identity</span><strong>{displayName}</strong></div>{token ? <button className="button button-secondary" onClick={signOut}>Sign out</button> : <button className="button button-primary" onClick={() => { setAuthMode("login"); setShowAuth(true); }}>Sign in / Create account</button>}</div><div className="panel settings-card"><div className="settings-icon">◈</div><h3>Service coverage</h3><p>Connected service routes currently configured in the gateway.</p>{["Authentication & users", "Product inventory", "Orders", "Payments", "Notifications"].map((s,i)=><div className="service-row" key={s}><span className="service-check">✓</span><span>{s}</span><span className="service-route">{["8081","8082","8083","8084","8085"][i]}</span></div>)}</div></div></section>}
        </div>
        <footer className="app-footer"><span>Â© 2026 Nexora Commerce Cloud</span><span><i className={`footer-status ${apiState === "online" ? "is-online" : ""}`} /> {apiState === "online" ? "Services connected" : "Workspace UI ready"} <span className="footer-divider">·</span> Built for modern commerce</span></footer>
      </main>

      {showAuth && <div className="modal-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) setShowAuth(false); }}><div className="modal-card auth-modal"><button className="modal-close" onClick={() => setShowAuth(false)}>×</button><div className="modal-brand"><div className="brand-mark"><span>◈</span></div><span>NEXORA</span></div><div className="modal-kicker">YOUR COMMERCE WORKSPACE</div><h2>{authMode === "login" ? "Welcome back." : "Create your account."}</h2><p className="modal-description">{authMode === "login" ? "Sign in to access your orders, payments and workspace." : "Register to get started with your commerce workspace."}</p><form onSubmit={submitAuth} className="modal-form"><label>Username<input required autoComplete="username" value={authForm.username} onChange={(e) => setAuthForm({ ...authForm, username: e.target.value })} placeholder="Enter your username" /></label>{authMode === "register" && <label>Email<input required type="email" autoComplete="email" value={authForm.email} onChange={(e) => setAuthForm({ ...authForm, email: e.target.value })} placeholder="you@example.com" /></label>}<label>Password<input required minLength={authMode === "register" ? 8 : 1} type="password" autoComplete={authMode === "login" ? "current-password" : "new-password"} value={authForm.password} onChange={(e) => setAuthForm({ ...authForm, password: e.target.value })} placeholder="Enter your password" /></label><button className="button button-primary modal-submit" disabled={saving}>{saving ? "Please wait…" : authMode === "login" ? "Sign in securely ↗" : "Create account ↗"}</button></form><div className="auth-switch">{authMode === "login" ? "New to Nexora?" : "Already have an account?"} <button onClick={() => setAuthMode(authMode === "login" ? "register" : "login")}>{authMode === "login" ? "Create account" : "Sign in"}</button></div><div className="modal-security">⌑ Your credentials are sent only to your configured API gateway.</div></div></div>}

      {pendingDelete && <div className="modal-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) setPendingDelete(null); }}><div className="modal-card" role="dialog" aria-modal="true" aria-labelledby="delete-product-title"><button className="modal-close" onClick={() => setPendingDelete(null)} aria-label="Close">×</button><div className="modal-kicker">CATALOG MANAGEMENT</div><h2 id="delete-product-title">Delete product?</h2><p className="modal-description">Delete "{pick(pendingDelete, ["name", "title"], "this product")}"? This action cannot be undone.</p><div className="form-two-col"><button type="button" className="button button-secondary" onClick={() => setPendingDelete(null)}>Cancel</button><button type="button" className="button button-primary" disabled={saving} onClick={confirmDeleteProduct}>Delete product</button></div></div></div>}
      {showProductForm && <div className="modal-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) setShowProductForm(false); }}><div className="modal-card product-modal"><button className="modal-close" onClick={() => setShowProductForm(false)}>×</button><div className="modal-kicker">CATALOG MANAGEMENT</div><h2>{selectedProduct ? "Edit product." : "Add a product."}</h2><p className="modal-description">Product changes are saved through the live inventory API.</p><form className="modal-form" onSubmit={saveProduct}><label>Product name<input required maxLength="120" value={productForm.name} onChange={(e) => setProductForm({ ...productForm, name: e.target.value })} placeholder="e.g. Wireless Headphones" /></label><label>Description<textarea rows="3" value={productForm.description} onChange={(e) => setProductForm({ ...productForm, description: e.target.value })} placeholder="Describe the product…" /></label><div className="form-two-col"><label>Price (INR)<input required type="number" min="0" step="0.01" value={productForm.price} onChange={(e) => setProductForm({ ...productForm, price: e.target.value })} placeholder="0.00" /></label><label>Stock quantity<input required type="number" min="0" step="1" value={productForm.stock} onChange={(e) => setProductForm({ ...productForm, stock: e.target.value })} placeholder="0" /></label></div><button className="button button-primary modal-submit" disabled={saving}>{saving ? "Saving…" : selectedProduct ? "Save changes ↗" : "Create product ↗"}</button></form></div></div>}

      {notice && <div className={`toast toast-${notice.type}`} role="status"><span className="toast-symbol">{notice.type === "success" ? "✓" : notice.type === "error" ? "!" : "i"}</span><span>{notice.text}</span><button onClick={() => setNotice(null)}>×</button></div>}
    </div>
  );
}

function Metric({ label, value, change, icon, tone, foot }) {
  return <div className="metric-card"><div className="metric-top"><span className="metric-label">{label}</span><span className={`metric-icon tone-${tone}`}>{icon}</span></div><div className="metric-value">{value}</div><div className="metric-meta"><span className={`metric-change tone-text-${tone}`}>{change}</span></div><div className="metric-foot">{foot}</div></div>;
}
function PageHeader({ eyebrow, title, subtitle, action }) {
  return <div className="page-header-row"><div><div className="eyebrow"><span className="eyebrow-line" /> {eyebrow}</div><h1>{title}<span className="title-period">.</span></h1><p className="page-subtitle">{subtitle}</p></div>{action && <div className="page-header-action">{action}</div>}</div>;
}
function EmptyState({ icon, title, text, action, actionText }) {
  return <div className="empty-state"><div className="empty-icon">{icon}</div><h4>{title}</h4><p>{text}</p>{action && <button className="button button-secondary" onClick={action}>{actionText || "Continue"} →</button>}</div>;
}
function formatDate(value) {
  if (!value) return "—";
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
}
export default App;






