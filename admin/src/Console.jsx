import { Shell } from "./components/Shell.jsx";
import { Empty } from "./components/ui.jsx";
import { match } from "./router.js";
import { OverviewPage } from "./pages/OverviewPage.jsx";
import { StoreListPage, StorePage } from "./pages/StoresPages.jsx";
import { CustomerListPage, CustomerPage } from "./pages/CustomersPages.jsx";
import { OrderListPage, OrderPage } from "./pages/OrdersPages.jsx";
import { PaymentsPage } from "./pages/PaymentsPage.jsx";
import { CataloguePage } from "./pages/CataloguePage.jsx";
import { InventoryPage } from "./pages/InventoryPage.jsx";
import { ReviewsPage } from "./pages/ReviewsPage.jsx";
import { MessagesPage } from "./pages/MessagesPage.jsx";
import { AnnouncementsPage } from "./pages/AnnouncementsPage.jsx";
import { SecurityPage } from "./pages/SecurityPage.jsx";
import { OutboxPage } from "./pages/OutboxPage.jsx";
import { Compass } from "lucide-react";

/** Every page of the console, matched on the hash path ("#/stores/12" -> StorePage with id 12). */
const ROUTES = [
  ["/", OverviewPage],
  ["/stores", StoreListPage],
  ["/stores/:id", StorePage],
  ["/customers", CustomerListPage],
  ["/customers/:id", CustomerPage],
  ["/orders", OrderListPage],
  ["/orders/:id", OrderPage],
  ["/payments", PaymentsPage],
  ["/catalogue", CataloguePage],
  ["/inventory", InventoryPage],
  ["/reviews", ReviewsPage],
  ["/messages", MessagesPage],
  ["/announcements", AnnouncementsPage],
  ["/security", SecurityPage],
  ["/outbox", OutboxPage],
];

export default function Console({ route, navigate }) {
  let page = null;
  for (const [pattern, Page] of ROUTES) {
    const params = match(pattern, route.path);
    if (params) {
      page = <Page key={params.id || pattern} route={route} navigate={navigate} params={params} />;
      break;
    }
  }
  return (
    <Shell route={route} navigate={navigate}>
      {page || (
        <Empty icon={Compass} title="This page does not exist" action={<a href="#/">Go to the overview</a>}>
          The link may be old, or the address was typed with a mistake.
        </Empty>
      )}
    </Shell>
  );
}
