import { useCallback, useEffect, useState } from "react";
import {
  Bell, Boxes, ClipboardList, LayoutDashboard, LogOut, Moon, Package, ShoppingCart, Store, Sun, Truck, Users,
} from "lucide-react";
import { api, setSlowStartHandler } from "./api.js";
import { useAuth } from "./auth.jsx";
import { useCart } from "./cart.js";
import { useToast } from "./hooks.jsx";
import LoginPage from "./pages/LoginPage.jsx";
import CatalogPage from "./pages/CatalogPage.jsx";
import CartPage from "./pages/CartPage.jsx";
import { OrderDetailPage, OrdersPage } from "./pages/OrdersPage.jsx";
import NotificationsPage from "./pages/NotificationsPage.jsx";
import DashboardPage from "./pages/admin/DashboardPage.jsx";
import ProductsPage from "./pages/admin/ProductsPage.jsx";
import OrdersAdminPage from "./pages/admin/OrdersAdminPage.jsx";
import UsersPage from "./pages/admin/UsersPage.jsx";

// Hash routing keeps the static hosting simple (no server-side rewrite rules needed).
function useHashRoute() {
  const read = () => window.location.hash.replace(/^#/, "") || "";
  const [path, setPath] = useState(read);
  useEffect(() => {
    const onChange = () => setPath(read());
    window.addEventListener("hashchange", onChange);
    return () => window.removeEventListener("hashchange", onChange);
  }, []);
  const navigate = useCallback((to) => {
    window.location.hash = to;
    window.scrollTo(0, 0);
  }, []);
  return [path, navigate];
}

function useTheme() {
  const [dark, setDark] = useState(() => {
    try {
      return localStorage.getItem("nexora_theme") === "dark";
    } catch {
      return false;
    }
  });
  useEffect(() => {
    document.documentElement.dataset.theme = dark ? "dark" : "light";
    try {
      localStorage.setItem("nexora_theme", dark ? "dark" : "light");
    } catch {
      /* ignore */
    }
  }, [dark]);
  return [dark, setDark];
}

export default function App() {
  const { user, isAdmin, logout } = useAuth();
  const [path, navigate] = useHashRoute();
  const [dark, setDark] = useTheme();
  const [waking, setWaking] = useState(false);
  const [unread, setUnread] = useState(0);
  const cart = useCart();
  const toast = useToast();

  useEffect(() => setSlowStartHandler(setWaking), []);

  const refreshUnread = useCallback(() => {
    if (!user) return;
    api("/api/notifications/unread-count").then((r) => setUnread(r.unread)).catch(() => {});
  }, [user]);

  useEffect(() => {
    refreshUnread();
    const timer = setInterval(refreshUnread, 20000);
    return () => clearInterval(timer);
  }, [refreshUnread, path]);

  useEffect(() => {
    if (user && (path === "" || path === "/")) navigate(isAdmin ? "/admin" : "/catalog");
  }, [user, isAdmin, path, navigate]);

  if (!user) return <LoginPage />;

  const nav = isAdmin
    ? [
        ["/admin", "Dashboard", LayoutDashboard],
        ["/admin/orders", "Orders", Truck],
        ["/admin/products", "Products & stock", Package],
        ["/admin/users", "Users", Users],
        ["/catalog", "Shop view", Store],
        ["/notifications", "Notifications", Bell],
      ]
    : [
        ["/catalog", "Shop", Store],
        ["/cart", `Cart${cart.count ? ` (${cart.count})` : ""}`, ShoppingCart],
        ["/orders", "My orders", ClipboardList],
        ["/notifications", "Notifications", Bell],
      ];

  const props = { navigate, toast, cart, isAdmin };
  let page;
  const orderMatch = path.match(/^\/orders\/(\d+)$/);
  if (orderMatch) page = <OrderDetailPage id={orderMatch[1]} {...props} />;
  else if (path === "/cart" && !isAdmin) page = <CartPage {...props} />;
  else if (path === "/orders") page = <OrdersPage {...props} />;
  else if (path === "/notifications") page = <NotificationsPage onChange={refreshUnread} />;
  else if (path === "/admin" && isAdmin) page = <DashboardPage {...props} />;
  else if (path === "/admin/products" && isAdmin) page = <ProductsPage {...props} />;
  else if (path === "/admin/orders" && isAdmin) page = <OrdersAdminPage {...props} />;
  else if (path === "/admin/users" && isAdmin) page = <UsersPage currentUser={user} {...props} />;
  else page = <CatalogPage {...props} />;

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand"><Boxes size={22} /> Nexora</div>
        <nav>
          {nav.map(([to, label, Icon]) => (
            <a key={to} href={`#${to}`} className={path === to || (to !== "/admin" && path.startsWith(`${to}/`)) ? "active" : ""}>
              <Icon size={17} />
              <span>{label}</span>
              {to === "/notifications" && unread > 0 && <em className="pill">{unread}</em>}
            </a>
          ))}
        </nav>
        <div className="sidebar-foot">
          <div className="me">
            <strong>{user.fullName || user.username}</strong>
            <span>{user.role.toLowerCase()}</span>
          </div>
          <div className="row">
            <button className="icon-btn" onClick={() => setDark(!dark)} aria-label="Toggle theme">
              {dark ? <Sun size={16} /> : <Moon size={16} />}
            </button>
            <button className="icon-btn" onClick={() => { logout(); navigate("/"); }} aria-label="Log out">
              <LogOut size={16} />
            </button>
          </div>
        </div>
      </aside>
      <main className="content">
        {waking && <div className="waking">Waking up the servers... free hosting can take up to a minute after inactivity.</div>}
        {page}
      </main>
      {toast.node}
    </div>
  );
}
