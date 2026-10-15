import { useEffect, useRef, useState } from "react";
import {
  Boxes,
  Contrast,
  CreditCard,
  ExternalLink,
  Gauge,
  LogOut,
  Mail,
  Megaphone,
  Menu as MenuIcon,
  MessageSquareWarning,
  Package,
  Receipt,
  Search,
  ShieldCheck,
  Star,
  Store,
  Sun,
  Tags,
  UserRound,
  Users,
} from "lucide-react";
import { api, qs } from "../api.js";
import { useSession } from "../auth.jsx";
import { formatMoney, formatNumber } from "../format.js";
import { useClickOutside, useDebounced, useInterval, useLoader, useTheme } from "../hooks.jsx";
import { ConsoleLogo } from "./brand.jsx";
import { MARKETPLACE_URL, storeName } from "./common.jsx";
import { Avatar, IconButton, Menu } from "./ui.jsx";

/** Counts shown next to the navigation links, refreshed every minute. */
function useNavCounts() {
  const counts = useLoader(async () => {
    const [messaging, orders, accounts] = await Promise.all([
      api("/api/admin/conversations/stats").catch(() => null),
      api("/api/admin/orders/counts").catch(() => null),
      api("/api/admin/users/stats").catch(() => null),
    ]);
    return {
      reported: messaging?.reportedConversations || 0,
      toShip: orders?.CONFIRMED || 0,
      failedSignIns: accounts?.failedSignInsLast24h || 0,
    };
  }, []);
  useInterval(() => counts.reload(), 60000);
  return counts.data || { reported: 0, toShip: 0, failedSignIns: 0 };
}

const NAV = [
  { group: null, items: [{ to: "/", label: "Overview", icon: Gauge, exact: true }] },
  {
    group: "Marketplace",
    items: [
      { to: "/stores", label: "Stores", icon: Store },
      { to: "/customers", label: "Customers", icon: Users },
      { to: "/catalogue", label: "Catalogue", icon: Tags },
    ],
  },
  {
    group: "Commerce",
    items: [
      { to: "/orders", label: "Orders", icon: Package, count: "toShip" },
      { to: "/payments", label: "Payments", icon: CreditCard },
      { to: "/inventory", label: "Inventory", icon: Boxes },
    ],
  },
  {
    group: "Trust and safety",
    items: [
      { to: "/reviews", label: "Reviews", icon: Star },
      { to: "/messages", label: "Messages", icon: MessageSquareWarning, count: "reported", hot: true },
      { to: "/security", label: "Security log", icon: ShieldCheck, count: "failedSignIns", hot: true },
    ],
  },
  {
    group: "Communication",
    items: [
      { to: "/announcements", label: "Announcements", icon: Megaphone },
      { to: "/outbox", label: "Notifications and e-mail", icon: Mail },
    ],
  },
];

const isActive = (item, path) => (item.exact ? path === item.to : path === item.to || path.startsWith(`${item.to}/`));

// ---------------------------------------------------------------- find anything

const EMPTY_RESULTS = { orders: [], accounts: [], payments: [], products: [] };

/** One search box for the whole marketplace: orders, stores, customers, payments and products, across services. */
function FindAnything({ navigate }) {
  const [text, setText] = useState("");
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const [state, setState] = useState({ query: "", results: EMPTY_RESULTS, loading: false });
  const query = useDebounced(text.trim(), 250);
  const box = useRef(null);
  const input = useRef(null);
  useClickOutside(box, () => setOpen(false), open);

  useEffect(() => {
    const onKey = (event) => {
      const typing = /^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement?.tagName || "");
      if ((event.key === "k" && (event.metaKey || event.ctrlKey)) || (event.key === "/" && !typing)) {
        event.preventDefault();
        input.current?.focus();
        setOpen(true);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  useEffect(() => {
    if (query.length < 2) return undefined;
    let cancelled = false;
    const load = async () => {
      setState((s) => ({ ...s, loading: true }));
      const [orders, accounts, payments, products] = await Promise.all([
        api(`/api/admin/orders${qs({ q: query, size: 5 })}`).catch(() => null),
        api(`/api/admin/users${qs({ search: query, size: 6 })}`).catch(() => null),
        api(`/api/admin/payments${qs({ q: query, size: 4 })}`).catch(() => null),
        api(`/api/admin/catalog/products${qs({ search: query, size: 5 })}`).catch(() => null),
      ]);
      if (cancelled) return;
      setActive(0);
      setState({
        query,
        loading: false,
        results: {
          orders: orders?.content || [],
          accounts: accounts?.content || [],
          payments: payments?.content || [],
          products: products?.content || [],
        },
      });
    };
    load();
    return () => {
      cancelled = true;
    };
  }, [query]);

  const showing = query.length >= 2 && state.query === query;
  const r = showing ? state.results : EMPTY_RESULTS;
  const items = [
    ...r.accounts.map((u) => ({
      key: `u${u.id}`,
      group: "Stores and customers",
      icon: u.role === "SELLER" ? Store : UserRound,
      title: u.role === "SELLER" ? storeName(u) : u.fullName || u.username,
      sub: `${u.role === "SELLER" ? "Store" : "Customer"} - ${u.username} - ${u.email}${u.active ? "" : " - suspended"}`,
      to: u.role === "SELLER" ? `/stores/${u.id}` : `/customers/${u.id}`,
    })),
    ...r.orders.map((o) => ({
      key: `o${o.id}`,
      group: "Orders",
      icon: Package,
      title: o.orderNumber,
      sub: `${o.sellerName} - ${o.recipientName || o.customerUsername} - ${formatMoney(o.totalAmount)}`,
      to: `/orders/${o.id}`,
    })),
    ...r.payments.map((p) => ({
      key: `p${p.id}`,
      group: "Payments",
      icon: Receipt,
      title: p.transactionReference,
      sub: `${p.orderNumber} - ${formatMoney(p.amount)} - ${p.status.toLowerCase()}`,
      to: `/payments${qs({ q: p.transactionReference, open: p.id })}`,
    })),
    ...r.products.map((p) => ({
      key: `c${p.id}`,
      group: "Products",
      icon: Tags,
      title: p.name,
      sub: `${p.sku} - ${p.sellerName} - ${formatNumber(p.availableQuantity)} available`,
      to: `/catalogue${qs({ search: p.sku, product: p.id })}`,
    })),
  ];
  const groups = [...new Set(items.map((i) => i.group))];

  const go = (item) => {
    if (!item) return;
    navigate(item.to);
    setOpen(false);
    setText("");
    input.current?.blur();
  };

  return (
    <div className="cc-find" ref={box}>
      <div className="cc-find-box">
        <Search />
        <input
          ref={input}
          value={text}
          placeholder="Find an order, store, customer, payment or product"
          aria-label="Find anything on the marketplace"
          aria-expanded={open && query.length >= 2}
          aria-controls="cc-find-results"
          role="combobox"
          aria-autocomplete="list"
          onFocus={() => setOpen(true)}
          onChange={(event) => {
            setText(event.target.value);
            setOpen(true);
          }}
          onKeyDown={(event) => {
            if (event.key === "ArrowDown") {
              event.preventDefault();
              setActive((i) => Math.min(items.length - 1, i + 1));
            } else if (event.key === "ArrowUp") {
              event.preventDefault();
              setActive((i) => Math.max(0, i - 1));
            } else if (event.key === "Enter") {
              go(items[active]);
            } else if (event.key === "Escape") {
              setOpen(false);
              input.current?.blur();
            }
          }}
        />
        <kbd>Ctrl K</kbd>
      </div>
      {open && query.length >= 2 && (
        <div className="cc-find-results" id="cc-find-results" role="listbox">
          {!showing || (state.loading && !items.length) ? (
            <div className="cc-find-empty">Searching the marketplace...</div>
          ) : items.length === 0 ? (
            <div className="cc-find-empty">Nothing matches "{query}". Try an order number, a store name, an e-mail address or a SKU.</div>
          ) : (
            groups.map((group) => (
              <div className="cc-find-group" key={group}>
                <h3>{group}</h3>
                {items
                  .filter((item) => item.group === group)
                  .map((item) => {
                    const index = items.indexOf(item);
                    return (
                      <button
                        key={item.key}
                        type="button"
                        role="option"
                        aria-selected={index === active}
                        className={`cc-find-item ${index === active ? "active" : ""}`}
                        onMouseEnter={() => setActive(index)}
                        onClick={() => go(item)}
                      >
                        <item.icon />
                        <span className="grow">
                          <b>{item.title}</b>
                          <small>{item.sub}</small>
                        </span>
                      </button>
                    );
                  })}
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- shell

export function Shell({ route, navigate, children }) {
  const { admin, session, signOut } = useSession();
  const { dark, toggle } = useTheme();
  const counts = useNavCounts();
  const [navOpen, setNavOpen] = useState(false);
  const endsAt = session?.expiresAt ? new Date(session.expiresAt).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" }) : null;

  return (
    <>
      <header className="cc-bar">
        <IconButton icon={MenuIcon} label="Menu" className="cc-menu-btn" onClick={() => setNavOpen((v) => !v)} aria-expanded={navOpen} />
        <ConsoleLogo />
        <FindAnything navigate={navigate} />
        <div className="cc-bar-actions">
          <a className="cc-market-link" href={MARKETPLACE_URL} target="_blank" rel="noreferrer">
            <ExternalLink /> <span>Marketplace</span>
          </a>
          <button
            type="button"
            className="icon-btn"
            aria-label={dark ? "Switch to light theme" : "Switch to dark theme"}
            title={dark ? "Switch to light theme" : "Switch to dark theme"}
            onClick={toggle}
          >
            {dark ? <Sun key="light" className="theme-icon" /> : <Contrast key="dark" className="theme-icon" />}
          </button>
          <Menu
            trigger={({ open, toggle }) => (
              <button type="button" className="cc-admin-btn" aria-label="Administrator menu" aria-expanded={open} onClick={toggle}>
                <Avatar name={admin?.fullName || admin?.username} />
                <span className="who">
                  <small>Administrator</small>
                  <b>{admin?.username}</b>
                </span>
              </button>
            )}
          >
            <div className="session-note">
              Signed in as {admin?.email}
              {endsAt && (
                <>
                  <br />
                  This session ends at {endsAt}
                </>
              )}
            </div>
            <button type="button" className="menu-item" onClick={() => signOut()}>
              <LogOut /> Sign out
            </button>
          </Menu>
        </div>
      </header>
      <div className={`cc-layout ${navOpen ? "nav-open" : ""}`}>
        <nav className="cc-nav" aria-label="Control Center" onClick={(event) => event.target.closest("a") && setNavOpen(false)}>
          {NAV.map((section, index) => (
            <div className="cc-nav-group" key={section.group || index}>
              {section.group && <h2>{section.group}</h2>}
              {section.items.map((item) => {
                const count = item.count ? counts[item.count] : 0;
                return (
                  <a
                    key={item.to}
                    href={`#${item.to}`}
                    className={isActive(item, route.path) ? "active" : ""}
                    aria-current={isActive(item, route.path) ? "page" : undefined}
                  >
                    <item.icon /> {item.label}
                    {count > 0 && <span className={`cc-count ${item.hot ? "hot" : ""}`}>{formatNumber(count)}</span>}
                  </a>
                );
              })}
            </div>
          ))}
          <div className="cc-nav-foot">Suspensions, cancellations and review removals always ask for a reason, so every decision has a record of why.</div>
        </nav>
        <div className="cc-scrim" onClick={() => setNavOpen(false)} />
        <main className="cc-main" id="main">
          <div key={route.path} className="page-enter">
            {children}
          </div>
        </main>
      </div>
    </>
  );
}
