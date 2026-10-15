import { Suspense, lazy, useEffect, useState } from "react";
import { LoaderCircle } from "lucide-react";
import { qs, setSlowStartHandler } from "./api.js";
import { useSession } from "./auth.jsx";
import { Loader } from "./components/ui.jsx";
import { useRoute } from "./router.js";
import { SignInPage } from "./pages/SignInPage.jsx";

// The console itself (navigation, pages and charts) loads only after the administrator has signed in.
const Console = lazy(() => import("./Console.jsx"));

function WakeBanner() {
  return (
    <div className="wake-banner" role="status">
      <LoaderCircle className="spin" />
      Waking Nexora up. The free servers sleep when nobody visits; this takes up to two minutes.
    </div>
  );
}

export default function App() {
  const { admin } = useSession();
  const [route, navigate] = useRoute();
  const [waking, setWaking] = useState(false);

  useEffect(() => {
    setSlowStartHandler(setWaking);
  }, []);

  // signed out (or the session expired): keep the page that was open, to return to it after signing in
  useEffect(() => {
    if (!admin && route.path !== "/signin") {
      const next = `${route.path}${qs(route.query)}`;
      navigate(`/signin${next !== "/" ? qs({ next }) : ""}`, { replace: true });
    }
    if (admin && route.path === "/signin") navigate(route.query.next || "/", { replace: true });
    // route.query is a new object on every navigation; its text form is enough to react to
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [admin, route.path, qs(route.query), navigate]);

  return (
    <>
      {!admin || route.path === "/signin" ? (
        <SignInPage route={route} />
      ) : (
        <Suspense fallback={<Loader text="Opening the Control Center" />}>
          <Console route={route} navigate={navigate} />
        </Suspense>
      )}
      {waking && <WakeBanner />}
    </>
  );
}
