import { Suspense, lazy, useCallback, useEffect, useState } from "react";
import {
  BarChart3, Bell, Boxes, ClipboardList, FileSpreadsheet, LayoutDashboard, MessageSquare, Package, Star, Store, Truck, Users, Wallet,
} from "lucide-react";
import { api, setSlowStartHandler } from "./api.js";
import { useAuth } from "./auth.jsx";
import { useCart } from "./cart.js";
import { useInterval, useTheme, useToast } from "./hooks.jsx";
import { match, useRoute } from "./router.js";
import StoreLayout from "./layouts/StoreLayout.jsx";
import ConsoleLayout from "./layouts/ConsoleLayout.jsx";
import AuthPage from "./pages/AuthPage.jsx";
import ShopPage from "./pages/shop/ShopPage.jsx";
import ProductPage from "./pages/shop/ProductPage.jsx";
import StorePage from "./pages/shop/StorePage.jsx";
import CartPage from "./pages/shop/CartPage.jsx";
import { OrderDetailPage, OrdersPage } from "./pages/shop/OrdersPage.jsx";
import AccountPage from "./pages/shop/AccountPage.jsx";
import MessagesPage from "./pages/common/MessagesPage.jsx";
import NotificationsPage from "./pages/common/NotificationsPage.jsx";
import { Spinner } from "./components/ui.jsx";

// The back-office consoles (and the chart library they use) are loaded only when a merchant or admin signs in,
// so shoppers download a much smaller storefront bundle.
const MerchantDashboard = lazy(() => import("./pages/merchant/DashboardPage.jsx"));
const MerchantProducts = lazy(() => import("./pages/merchant/ProductsPage.jsx"));
const MerchantInventory = lazy(() => import("./pages/merchant/InventoryPage.jsx"));
const MerchantOrders = lazy(() => import("./pages/merchant/OrdersPage.jsx").then((m) => ({ default: m.MerchantOrders })));
const MerchantOrderDetail = lazy(() => import("./pages/merchant/OrdersPage.jsx").then((m) => ({ default: m.MerchantOrderDetail })));
const MerchantFinance = lazy(() => import("./pages/merchant/FinancePage.jsx"));
const MerchantReports = lazy(() => import("./pages/merchant/ReportsPage.jsx"));
const MerchantReviews = lazy(() => import("./pages/merchant/ReviewsPage.jsx"));
const MerchantStoreProfile = lazy(() => import("./pages/merchant/StoreProfilePage.jsx"));
const AdminDashboard = lazy(() => import("./pages/admin/DashboardPage.jsx"));
const AdminUsers = lazy(() => import("./pages/admin/UsersPage.jsx"));
const AdminOrders = lazy(() => import("./pages/admin/OrdersPage.jsx"));

const NO_COUNTS = { notifications: 0, messages: 0 };

/** Unread notifications and messages for the badges, refreshed every 20 seconds while signed in. */
function useCounts(user) {
  const [counts, setCounts] = useState({ notifications: 0, messages: 0 });
  const refresh = useCallback(() => {
    if (!user || user.role === "ADMIN") {
      if (user) api("/api/notifications/unread-count").then((n) => setCounts({ notifications: n.unread, messages: 0 })).catch(() => {});
      return;
    }
    Promise.all([api("/api/notifications/unread-count"), api("/api/conversations/unread-count")])
      .then(([n, m]) => setCounts({ notifications: n.unread, messages: m.unread }))
      .catch(() => {});
  }, [user]);
  useEffect(() => {
    if (user) refresh();
  }, [user, refresh]);
  useInterval(refresh, 20000, Boolean(user));
  return [user ? counts : NO_COUNTS, refresh];
}

const home = (role) => (role === "MERCHANT" ? "/merchant" : role === "ADMIN" ? "/admin" : "/shop");

function Redirect({ to, navigate }) {
  useEffect(() => navigate(to, { replace: true }), [to, navigate]);
  return null;
}

export default function App() {
  const { user, logout } = useAuth();
  const [route, navigate] = useRoute();
  const [dark, setDark] = useTheme();
  const [waking, setWaking] = useState(false);
  const cart = useCart();
  const toast = useToast();
  const [counts, refreshCounts] = useCounts(user);
  const role = user?.role;
  const path = route.path;

  useEffect(() => setSlowStartHandler(setWaking), []);
  useEffect(() => {
    if (path === "/" || path === "") navigate(home(role), { replace: true });
  }, [path, role, navigate]);

  const signOut = () => {
    logout();
    cart.clear();
    navigate("/shop");
  };
  const ctx = { user, route, navigate, toast, cart, counts, refreshCounts };
  const theme = { dark, setDark, waking, onLogout: signOut };

  // ------------------------------------------------------------------ sign in / sign up
  if (path === "/login" || path === "/register") {
    if (user) return <Redirect to={route.query.next || home(role)} navigate={navigate} />;
    return (
      <>
        <AuthPage mode={path === "/register" ? "register" : "login"} route={route} navigate={navigate} />
        {toast.node}
      </>
    );
  }

  // ------------------------------------------------------------------ merchant console
  if (role === "MERCHANT" && (path.startsWith("/merchant") || path === "/notifications")) {
    const sections = [
      { label: null, items: [["/merchant", "Dashboard", LayoutDashboard]] },
      { label: "Catalogue", items: [["/merchant/products", "Products", Package], ["/merchant/inventory", "Inventory", Boxes]] },
      {
        label: "Sales",
        items: [
          ["/merchant/orders", "Orders", Truck],
          ["/merchant/messages", "Messages", MessageSquare, counts.messages],
          ["/merchant/reviews", "Reviews", Star],
        ],
      },
      { label: "Business", items: [["/merchant/finance", "Finance", Wallet], ["/merchant/reports", "Reports", FileSpreadsheet]] },
      { label: "Store", items: [["/merchant/store", "Store profile", Store], ["/notifications", "Notifications", Bell, counts.notifications]] },
    ];
    let page;
    let params;
    if (path === "/merchant") page = <MerchantDashboard {...ctx} />;
    else if (path === "/merchant/products") page = <MerchantProducts {...ctx} />;
    else if (path === "/merchant/inventory") page = <MerchantInventory {...ctx} />;
    else if (path === "/merchant/orders") page = <MerchantOrders {...ctx} />;
    else if ((params = match("/merchant/orders/:id", path))) page = <MerchantOrderDetail id={params.id} {...ctx} />;
    else if (path === "/merchant/messages") page = <MessagesPage basePath="/merchant/messages" {...ctx} />;
    else if ((params = match("/merchant/messages/:id", path))) page = <MessagesPage basePath="/merchant/messages" conversationId={params.id} {...ctx} />;
    else if (path === "/merchant/reviews") page = <MerchantReviews {...ctx} />;
    else if (path === "/merchant/finance") page = <MerchantFinance {...ctx} />;
    else if (path === "/merchant/reports") page = <MerchantReports {...ctx} />;
    else if (path === "/merchant/store") page = <MerchantStoreProfile {...ctx} />;
    else if (path === "/notifications") page = <NotificationsPage {...ctx} />;
    else page = <MerchantDashboard {...ctx} />;
    return (
      <>
        <ConsoleLayout user={user} sections={sections} route={route} subtitle="Merchant back office" {...theme}>
          <Suspense fallback={<Spinner />}>{page}</Suspense>
        </ConsoleLayout>
        {toast.node}
      </>
    );
  }

  // ------------------------------------------------------------------ admin console
  if (role === "ADMIN" && (path.startsWith("/admin") || path === "/notifications")) {
    const sections = [
      { label: null, items: [["/admin", "Platform overview", BarChart3]] },
      { label: "Manage", items: [["/admin/users", "Users and stores", Users], ["/admin/orders", "All orders", ClipboardList]] },
      { label: "Marketplace", items: [["/shop", "Open marketplace", Store], ["/notifications", "Notifications", Bell, counts.notifications]] },
    ];
    let page;
    if (path === "/admin/users") page = <AdminUsers {...ctx} />;
    else if (path === "/admin/orders") page = <AdminOrders {...ctx} />;
    else if (path === "/notifications") page = <NotificationsPage {...ctx} />;
    else page = <AdminDashboard {...ctx} />;
    return (
      <>
        <ConsoleLayout user={user} sections={sections} route={route} subtitle="Platform administration" {...theme}>
          <Suspense fallback={<Spinner />}>{page}</Suspense>
        </ConsoleLayout>
        {toast.node}
      </>
    );
  }

  // ------------------------------------------------------------------ storefront (customers, visitors, staff preview)
  const customerOnly = ["/cart", "/orders", "/messages", "/notifications", "/account"];
  const needsCustomer = customerOnly.some((p) => path === p || path.startsWith(`${p}/`)) && path !== "/cart";
  if (needsCustomer && !user) return <Redirect to={`/login?next=${encodeURIComponent(path)}`} navigate={navigate} />;
  if (user && role !== "CUSTOMER" && (needsCustomer || path === "/cart")) return <Redirect to={home(role)} navigate={navigate} />;

  let page;
  let params;
  if ((params = match("/product/:id", path))) page = <ProductPage id={params.id} {...ctx} />;
  else if ((params = match("/store/:id", path))) page = <StorePage id={params.id} {...ctx} />;
  else if (path === "/cart") page = <CartPage {...ctx} />;
  else if (path === "/orders") page = <OrdersPage {...ctx} />;
  else if ((params = match("/orders/:id", path))) page = <OrderDetailPage id={params.id} {...ctx} />;
  else if (path === "/messages") page = <MessagesPage basePath="/messages" {...ctx} />;
  else if ((params = match("/messages/:id", path))) page = <MessagesPage basePath="/messages" conversationId={params.id} {...ctx} />;
  else if (path === "/notifications") page = <NotificationsPage {...ctx} />;
  else if (path === "/account") page = <AccountPage {...ctx} />;
  else page = <ShopPage {...ctx} />;

  return (
    <>
      <StoreLayout user={user} cart={cart} counts={counts} route={route} navigate={navigate} {...theme}>
        {page}
      </StoreLayout>
      {toast.node}
    </>
  );
}
