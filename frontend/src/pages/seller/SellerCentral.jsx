import "../../styles/seller.css";
import { api } from "../../api.js";
import { Messenger, NotificationFeed } from "../../components/inbox.jsx";
import { useLoader } from "../../hooks.jsx";
import { match } from "../../router.js";
import { InventoryAnalyticsPage, SalesAnalyticsPage } from "./AnalyticsPages.jsx";
import { PageHead } from "./common.jsx";
import { SellerCustomersPage } from "./CustomersPage.jsx";
import { DashboardPage } from "./DashboardPage.jsx";
import { BudgetsPage, ExpensesPage, PaymentsPage, ProfitLossPage } from "./FinancePages.jsx";
import { InventoryPage } from "./InventoryPage.jsx";
import { SellerOrderPage, SellerOrdersPage } from "./OrdersPages.jsx";
import { ProductEditorPage } from "./ProductEditorPage.jsx";
import { ProductsPage } from "./ProductsPage.jsx";
import { GoodsReceiptsPage, PurchaseOrderEditor, PurchaseOrderPage, PurchaseOrdersPage, ReplenishmentPage } from "./PurchasingPages.jsx";
import { ReportsPage } from "./ReportsPage.jsx";
import { SellerReviewsPage } from "./ReviewsPage.jsx";
import { SellerLayout, useSeller } from "./SellerLayout.jsx";
import { SettingsPage } from "./SettingsPage.jsx";
import { StockCountPage, StockCountsPage } from "./StockCountsPages.jsx";
import { SuppliersPage } from "./SuppliersPage.jsx";
import { WarehousesPage } from "./WarehousesPage.jsx";

function SellerMessages({ route }) {
  return (
    <>
      <PageHead title="Messages" sub="Customers write to you from your products, your store page and their orders. Keep every sale on Nexora." />
      <Messenger initialId={route.query.c} emptyText="When a customer asks about a product or an order, the conversation appears here." />
    </>
  );
}

function SellerNotifications() {
  const { reloadCounts } = useSeller();
  return (
    <>
      <PageHead title="Notifications" sub="New orders, low stock, budget alerts, reviews and messages." />
      <NotificationFeed onRead={reloadCounts} />
    </>
  );
}

function NotFoundHere({ navigate }) {
  return (
    <>
      <PageHead title="Page not found" sub="This Seller Central page does not exist." />
      <button type="button" className="btn btn-secondary" onClick={() => navigate("/seller")}>
        Go to the dashboard
      </button>
    </>
  );
}

/** Seller Central: the store's inventory, purchasing, sales and finance system (loaded only for sellers). */
export default function SellerCentral({ route, navigate }) {
  const settings = useLoader(() => api("/api/seller/store/settings").catch(() => null), []);
  const { path } = route;
  const props = { route, navigate };
  let page;
  let params;

  if (path === "/seller") page = <DashboardPage {...props} />;
  else if (path === "/seller/products") page = <ProductsPage {...props} />;
  else if (path === "/seller/products/new") page = <ProductEditorPage key="new" {...props} />;
  else if ((params = match("/seller/products/:id", path))) page = <ProductEditorPage key={params.id} id={params.id} {...props} />;
  else if (path === "/seller/inventory") page = <InventoryPage {...props} />;
  else if (path === "/seller/warehouses") page = <WarehousesPage {...props} />;
  else if (path === "/seller/stock-counts") page = <StockCountsPage {...props} />;
  else if ((params = match("/seller/stock-counts/:id", path))) page = <StockCountPage key={params.id} id={params.id} {...props} />;
  else if (path === "/seller/replenishment") page = <ReplenishmentPage {...props} />;
  else if (path === "/seller/purchase-orders") page = <PurchaseOrdersPage {...props} />;
  else if (path === "/seller/purchase-orders/new") page = <PurchaseOrderEditor key="new" {...props} />;
  else if ((params = match("/seller/purchase-orders/:id/edit", path))) page = <PurchaseOrderEditor key={`edit-${params.id}`} id={params.id} {...props} />;
  else if ((params = match("/seller/purchase-orders/:id", path))) page = <PurchaseOrderPage key={params.id} id={params.id} {...props} />;
  else if (path === "/seller/receipts") page = <GoodsReceiptsPage {...props} />;
  else if (path === "/seller/suppliers") page = <SuppliersPage {...props} />;
  else if (path === "/seller/orders") page = <SellerOrdersPage {...props} />;
  else if ((params = match("/seller/orders/:id", path))) page = <SellerOrderPage key={params.id} id={params.id} {...props} />;
  else if (path === "/seller/customers") page = <SellerCustomersPage {...props} />;
  else if (path === "/seller/reviews") page = <SellerReviewsPage {...props} />;
  else if (path === "/seller/messages") page = <SellerMessages {...props} />;
  else if (path === "/seller/notifications") page = <SellerNotifications />;
  else if (path === "/seller/analytics") page = <SalesAnalyticsPage {...props} />;
  else if (path === "/seller/analytics/inventory") page = <InventoryAnalyticsPage {...props} />;
  else if (path === "/seller/reports") page = <ReportsPage {...props} />;
  else if (path === "/seller/finance") page = <PaymentsPage {...props} />;
  else if (path === "/seller/finance/profit-loss") page = <ProfitLossPage {...props} />;
  else if (path === "/seller/finance/expenses") page = <ExpensesPage {...props} />;
  else if (path === "/seller/finance/budgets") page = <BudgetsPage {...props} />;
  else if (path === "/seller/settings") page = <SettingsPage {...props} />;
  else page = <NotFoundHere navigate={navigate} />;

  return (
    <SellerLayout route={route} navigate={navigate} settings={settings}>
      {page}
    </SellerLayout>
  );
}
