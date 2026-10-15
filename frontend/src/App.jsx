import { Suspense, lazy, useEffect, useState } from "react";
import { LoaderCircle } from "lucide-react";
import { qs, setSlowStartHandler } from "./api.js";
import { useAuth } from "./auth.jsx";
import { Loader } from "./components/ui.jsx";
import { useToast } from "./hooks.jsx";
import { StoreLayout } from "./layouts/StoreLayout.jsx";
import { AccountArea } from "./pages/account/AccountArea.jsx";
import { AuthPage } from "./pages/auth/AuthPage.jsx";
import { NotFound, WrongAccount } from "./pages/NotFound.jsx";
import { CartPage } from "./pages/shop/CartPage.jsx";
import { CheckoutCompletePage, CheckoutPage } from "./pages/shop/CheckoutPage.jsx";
import { HomePage } from "./pages/shop/HomePage.jsx";
import { ProductPage } from "./pages/shop/ProductPage.jsx";
import { SearchPage } from "./pages/shop/SearchPage.jsx";
import { StorePage, StoresPage } from "./pages/shop/StorePages.jsx";
import { match, useRoute } from "./router.js";

// Seller Central (and the chart library it uses) is downloaded only when a seller opens it,
// so shoppers get a much smaller storefront bundle.
const SellerCentral = lazy(() => import("./pages/seller/SellerCentral.jsx"));

const AUTH_PATHS = ["/signin", "/register", "/seller/signin", "/seller/register"];

/** Current location as a path with its query, used for "?next=" after signing in. */
const here = (route) => `${route.path}${qs(route.query)}`;

export function Redirect({ to, navigate }) {
  useEffect(() => navigate(to, { replace: true }), [to, navigate]);
  return null;
}

function WakeBanner() {
  return (
    <div className="wake-banner" role="status">
      <LoaderCircle className="spin" />
      Waking Nexora up. The free servers sleep when nobody visits; this takes up to two minutes.
    </div>
  );
}

function storefrontPage(route, navigate, auth) {
  const { path } = route;
  let params;
  if (path === "/") return <HomePage />;
  if (path === "/search") return <SearchPage route={route} navigate={navigate} />;
  if ((params = match("/product/:id", path))) return <ProductPage key={params.id} id={params.id} navigate={navigate} />;
  if ((params = match("/store/:id", path))) return <StorePage key={params.id} id={params.id} route={route} navigate={navigate} />;
  if (path === "/stores") return <StoresPage route={route} navigate={navigate} />;
  if (path === "/cart") return <CartPage navigate={navigate} />;
  if (path === "/checkout" || path === "/checkout/complete" || path === "/account" || path.startsWith("/account/")) {
    if (!auth.user) return <Redirect to={`/signin${qs({ next: here(route) })}`} navigate={navigate} />;
    if (!auth.isCustomer) return <WrongAccount expected="customer" navigate={navigate} />;
    if (path === "/checkout") return <CheckoutPage route={route} navigate={navigate} />;
    if (path === "/checkout/complete") return <CheckoutCompletePage route={route} navigate={navigate} />;
    return <AccountArea route={route} navigate={navigate} />;
  }
  return <NotFound />;
}

export default function App() {
  const auth = useAuth();
  const { user, isSeller, isCustomer, sessionExpired, clearExpired } = auth;
  const [route, navigate] = useRoute();
  const toast = useToast();
  const [waking, setWaking] = useState(false);
  const { path } = route;

  useEffect(() => {
    setSlowStartHandler(setWaking);
  }, []);
  useEffect(() => {
    if (sessionExpired) {
      toast("Your session has ended. Please sign in again.", "bad");
      clearExpired();
    }
  }, [sessionExpired, clearExpired, toast]);

  let content;
  if (AUTH_PATHS.includes(path)) {
    const seller = path.startsWith("/seller");
    const signedInHere = seller ? isSeller : isCustomer;
    content = signedInHere ? (
      <Redirect to={route.query.next || (seller ? "/seller" : "/")} navigate={navigate} />
    ) : (
      <AuthPage key={path} seller={seller} mode={path.endsWith("register") ? "register" : "signin"} route={route} />
    );
  } else if (path === "/seller" || path.startsWith("/seller/")) {
    if (!user) content = <Redirect to={`/seller/signin${qs({ next: here(route) })}`} navigate={navigate} />;
    else if (!isSeller)
      content = (
        <StoreLayout route={route} navigate={navigate}>
          <WrongAccount expected="seller" navigate={navigate} />
        </StoreLayout>
      );
    else
      content = (
        <Suspense fallback={<Loader text="Opening Seller Central" />}>
          <SellerCentral route={route} navigate={navigate} />
        </Suspense>
      );
  } else {
    content = (
      <StoreLayout route={route} navigate={navigate}>
        {storefrontPage(route, navigate, auth)}
      </StoreLayout>
    );
  }

  return (
    <>
      {content}
      {waking && <WakeBanner />}
    </>
  );
}
