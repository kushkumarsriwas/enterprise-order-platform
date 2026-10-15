import { createContext, useContext, useState } from "react";
import {
  BarChart3,
  Bell,
  Boxes,
  ClipboardCheck,
  ClipboardList,
  ExternalLink,
  Factory,
  FileSpreadsheet,
  LayoutDashboard,
  LogOut,
  Menu as MenuIcon,
  MessageSquare,
  Package,
  PackageCheck,
  PiggyBank,
  Receipt,
  Repeat,
  Scale,
  Search,
  Settings,
  ShoppingBag,
  Star,
  Store,
  TrendingUp,
  Megaphone,
  TriangleAlert,
  Users,
  Wallet,
  Warehouse,
} from "lucide-react";
import { api, qs } from "../../api.js";
import { useAuth } from "../../auth.jsx";
import { Logo } from "../../components/brand.jsx";
import { Avatar, Button, IconButton, Menu } from "../../components/ui.jsx";
import { useInterval, useLoader, useToast } from "../../hooks.jsx";
import { ThemeToggle } from "../../layouts/StoreLayout.jsx";

const SellerContext = createContext({ reloadCounts: () => {}, settings: null, setSettings: () => {} });

/** Lets a page refresh the sidebar badges (after shipping an order, answering a review...) and read the store settings. */
export const useSeller = () => useContext(SellerContext);

/** Badge numbers for the sidebar, refreshed every minute: orders to ship, unread messages, reviews to answer... */
function useSellerCounts() {
  const counts = useLoader(async () => {
    const safe = (promise, fallback) => promise.catch(() => fallback);
    const [orders, messages, notifications, reviews, stock] = await Promise.all([
      safe(api("/api/seller/orders/counts"), {}),
      safe(api("/api/conversations/unread-count"), { unread: 0 }),
      safe(api("/api/notifications/unread-count"), { unread: 0 }),
      safe(api(`/api/seller/reviews${qs({ size: 1 })}`), { awaitingReply: 0 }),
      safe(api("/api/seller/inventory/summary"), {}),
    ]);
    return {
      toShip: orders.CONFIRMED || 0,
      messages: messages.unread || 0,
      notifications: notifications.unread || 0,
      reviews: reviews.awaitingReply || 0,
      lowStock: (stock.lowStockProducts || 0) + (stock.outOfStockProducts || 0),
    };
  }, []);
  useInterval(() => counts.reload(), 60000);
  return counts;
}

const NAV = (c) => [
  { title: null, items: [{ path: "/seller", label: "Dashboard", icon: LayoutDashboard, exact: true }] },
  {
    title: "Catalogue and stock",
    items: [
      { path: "/seller/products", label: "Products", icon: Package },
      { path: "/seller/inventory", label: "Inventory", icon: Boxes, count: c.lowStock, title: "Low or out of stock" },
      { path: "/seller/warehouses", label: "Warehouses", icon: Warehouse },
      { path: "/seller/stock-counts", label: "Stock counts", icon: ClipboardCheck },
    ],
  },
  {
    title: "Purchasing",
    items: [
      { path: "/seller/replenishment", label: "Reorder", icon: Repeat },
      { path: "/seller/purchase-orders", label: "Purchase orders", icon: ClipboardList },
      { path: "/seller/receipts", label: "Goods receipts", icon: PackageCheck },
      { path: "/seller/suppliers", label: "Suppliers", icon: Factory },
    ],
  },
  {
    title: "Sales",
    items: [
      { path: "/seller/orders", label: "Orders", icon: ShoppingBag, count: c.toShip, hot: true, title: "Orders to ship" },
      { path: "/seller/customers", label: "Customers", icon: Users },
      { path: "/seller/reviews", label: "Reviews", icon: Star, count: c.reviews, title: "Reviews without a reply" },
      { path: "/seller/messages", label: "Messages", icon: MessageSquare, count: c.messages, hot: true, title: "Unread messages" },
    ],
  },
  {
    title: "Insights",
    items: [
      { path: "/seller/analytics", label: "Sales analytics", icon: TrendingUp, exact: true },
      { path: "/seller/analytics/inventory", label: "Inventory analytics", icon: BarChart3 },
      { path: "/seller/reports", label: "Reports", icon: FileSpreadsheet },
    ],
  },
  {
    title: "Money",
    items: [
      { path: "/seller/finance", label: "Payments", icon: Wallet, exact: true },
      { path: "/seller/finance/profit-loss", label: "Profit and loss", icon: Scale },
      { path: "/seller/finance/expenses", label: "Expenses", icon: Receipt },
      { path: "/seller/finance/budgets", label: "Budgets", icon: PiggyBank },
    ],
  },
  {
    title: "Store",
    items: [
      { path: "/seller/notifications", label: "Notifications", icon: Bell, count: c.notifications },
      { path: "/seller/settings", label: "Settings", icon: Settings },
    ],
  },
];

const isActive = (item, path) => (item.exact ? path === item.path : path === item.path || path.startsWith(`${item.path}/`));

/** Announcements from Nexora for stores (or everyone), e.g. holiday delivery times. Shows the latest live one. */
function AnnouncementBanner() {
  const announcements = useLoader(() => api("/api/announcements").catch(() => []), []);
  const a = announcements.data?.[0];
  if (!a) return null;
  return (
    <div className={`sc-banner ${a.level === "WARNING" ? "sc-banner-warn" : "sc-banner-info"}`} role="note">
      <Megaphone />
      <span>
        <strong>{a.title}</strong> {a.message}
      </span>
    </div>
  );
}

function StoreBanner({ settings, onChange }) {
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  if (!settings) return null;
  if (settings.suspended) {
    return (
      <div className="sc-banner sc-banner-bad" role="alert">
        <TriangleAlert />
        <span>Nexora support has suspended your store. Your products are hidden from the marketplace; contact support to restore it.</span>
      </div>
    );
  }
  if (!settings.vacationMode) return null;
  return (
    <div className="sc-banner sc-banner-warn" role="status">
      <TriangleAlert />
      <span>Vacation mode is on: customers cannot see or buy your products.</span>
      <Button
        size="sm"
        loading={busy}
        onClick={async () => {
          setBusy(true);
          try {
            const updated = await api("/api/seller/store/settings", {
              method: "PUT",
              body: { vacationMode: false, vacationMessage: settings.vacationMessage, lowStockAlerts: settings.lowStockAlerts },
            });
            onChange(updated);
            toast("Vacation mode is off. Your listings are visible again.");
          } catch (error) {
            toast(error.message, "bad");
          } finally {
            setBusy(false);
          }
        }}
      >
        Turn it off
      </Button>
    </div>
  );
}

export function SellerLayout({ route, navigate, children, settings }) {
  const { user, logout } = useAuth();
  const counts = useSellerCounts();
  const [navOpen, setNavOpen] = useState(false);
  const [search, setSearch] = useState("");
  const c = counts.data || {};
  const storeName = user.store?.storeName || user.username;

  const submitSearch = (event) => {
    event.preventDefault();
    const text = search.trim();
    if (!text) return;
    navigate(/^ORD-/i.test(text) ? `/seller/orders${qs({ q: text })}` : `/seller/products${qs({ search: text })}`);
    setSearch("");
  };

  return (
    <div className={`sc-shell ${navOpen ? "nav-open" : ""}`}>
      <aside className="sc-sidebar" aria-label="Seller Central" onClick={(event) => event.target.closest("a") && setNavOpen(false)}>
        <div className="sc-brand">
          <Logo suffix="Seller Central" size={28} href="#/seller" />
          <div className="sc-store">
            <Avatar name={storeName} />
            <div className="grow">
              <b className="ellipsis">{storeName}</b>
              <small>{settings.data?.vacationMode ? "On vacation" : settings.data?.suspended ? "Suspended" : "Open for orders"}</small>
            </div>
          </div>
        </div>
        <nav className="sc-nav">
          {NAV(c).map((group) => (
            <div key={group.title || "main"} className="sc-nav-group">
              {group.title && <h3>{group.title}</h3>}
              {group.items.map((item) => (
                <a
                  key={item.path}
                  href={`#${item.path}`}
                  className={isActive(item, route.path) ? "active" : ""}
                  aria-current={isActive(item, route.path) ? "page" : undefined}
                >
                  <item.icon />
                  {item.label}
                  {item.count > 0 && (
                    <span className={`sc-count ${item.hot ? "hot" : ""}`} title={item.title}>
                      {item.count > 99 ? "99+" : item.count}
                    </span>
                  )}
                </a>
              ))}
            </div>
          ))}
        </nav>
        <div className="sc-side-foot">
          <a href={`#/store/${user.id}`}>
            <Store /> View my store
          </a>
          <a href="#/">
            <ExternalLink /> Nexora marketplace
          </a>
        </div>
      </aside>
      <div className="sc-scrim" onClick={() => setNavOpen(false)} aria-hidden="true" />

      <div className="sc-body">
        <header className="sc-topbar">
          <IconButton className="sc-menu-btn" icon={MenuIcon} label="Open the menu" onClick={() => setNavOpen(true)} />
          <form className="sc-search" role="search" onSubmit={submitSearch}>
            <Search />
            <input
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Find a product, SKU or order number"
              aria-label="Search Seller Central"
            />
          </form>
          <div className="sc-top-actions">
            <ThemeToggle />
            <IconButton icon={MessageSquare} label="Messages" count={c.messages} onClick={() => navigate("/seller/messages")} />
            <IconButton icon={Bell} label="Notifications" count={c.notifications} onClick={() => navigate("/seller/notifications")} />
            <Menu
              trigger={({ toggle, open }) => (
                <button type="button" className="account-btn" aria-expanded={open} onClick={toggle}>
                  <Avatar name={user.fullName || user.username} />
                </button>
              )}
            >
              <div className="menu-head">
                <strong>{user.fullName || user.username}</strong>
                {user.email}
              </div>
              <div className="menu-sep" />
              <a className="menu-item" href="#/seller/settings">
                <Settings /> Store settings
              </a>
              <a className="menu-item" href={`#/store/${user.id}`}>
                <Store /> View my store
              </a>
              <div className="menu-sep" />
              <button
                type="button"
                className="menu-item"
                onClick={() => {
                  logout();
                  navigate("/seller/signin");
                }}
              >
                <LogOut /> Sign out
              </button>
            </Menu>
          </div>
        </header>
        <StoreBanner settings={settings.data} onChange={settings.setData} />
        <AnnouncementBanner />
        <main className="sc-main">
          <SellerContext.Provider value={{ reloadCounts: counts.reload, settings: settings.data, setSettings: settings.setData }}>
            {children}
          </SellerContext.Provider>
        </main>
      </div>
    </div>
  );
}
