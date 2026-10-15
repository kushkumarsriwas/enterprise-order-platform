import { Suspense, lazy, useEffect } from "react";
import { qs } from "./api.js";
import { useSession } from "./auth.jsx";
import { Loader } from "./components/ui.jsx";
import { useRoute } from "./router.js";
import { SignInPage } from "./pages/SignInPage.jsx";

// The console itself (navigation, pages and charts) loads only after the administrator has signed in.
const Console = lazy(() => import("./Console.jsx"));

export default function App() {
  const { admin } = useSession();
  const [route, navigate] = useRoute();

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

  if (!admin || route.path === "/signin") return <SignInPage route={route} />;
  return (
    <Suspense fallback={<Loader text="Opening the Control Center" />}>
      <Console route={route} navigate={navigate} />
    </Suspense>
  );
}
