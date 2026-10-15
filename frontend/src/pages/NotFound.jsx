import { Compass, UserRoundX } from "lucide-react";
import { useAuth } from "../auth.jsx";
import { Button, Empty } from "../components/ui.jsx";
import { useTitle } from "../hooks.jsx";

export function NotFound() {
  useTitle("Page not found");
  return (
    <div className="panel">
      <Empty
        icon={Compass}
        title="This page does not exist"
        action={
          <Button variant="primary" onClick={() => (window.location.hash = "/")}>
            Go to the home page
          </Button>
        }
      >
        The link may be old or mistyped. Search for a product or browse the departments instead.
      </Empty>
    </div>
  );
}

/** Shown when a customer opens Seller Central, or a seller opens the cart, checkout or customer account. */
export function WrongAccount({ expected, navigate }) {
  const { user, logout } = useAuth();
  useTitle(expected === "seller" ? "Seller Central" : "Customer account");
  const switchAccount = () => {
    logout();
    navigate(expected === "seller" ? "/seller/signin" : "/signin", { replace: true });
  };
  return (
    <div className="panel">
      {expected === "seller" ? (
        <Empty
          icon={UserRoundX}
          title="Seller Central is for store accounts"
          action={
            <div className="row row-wrap" style={{ justifyContent: "center" }}>
              <Button variant="primary" onClick={switchAccount}>
                Sign out and use a seller account
              </Button>
              <Button onClick={() => navigate("/")}>Keep shopping</Button>
            </div>
          }
        >
          You are signed in as {user?.username}, a customer account. Sellers sign in to Seller Central with their store account.
        </Empty>
      ) : (
        <Empty
          icon={UserRoundX}
          title="Shopping needs a customer account"
          action={
            <div className="row row-wrap" style={{ justifyContent: "center" }}>
              <Button variant="primary" onClick={() => navigate("/seller")}>
                Open Seller Central
              </Button>
              <Button onClick={switchAccount}>Sign out and shop as a customer</Button>
            </div>
          }
        >
          You are signed in with the store account of {user?.store?.storeName || user?.username}. Orders, the cart and checkout belong to customer accounts.
        </Empty>
      )}
    </div>
  );
}
