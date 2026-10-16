import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
// fonts are bundled with the app (no third-party font CDN): Sora for headlines and prices, Figtree for everything else
import "@fontsource-variable/sora";
import "@fontsource-variable/figtree";
import "./styles/tokens.css";
import "./styles/base.css";
import "./styles/ui.css";
import "./styles/store.css";
import "./styles/motion.css";
import App from "./App.jsx";
import { keepServicesAwake } from "./wake.js";
import { scrollSidewaysWithWheel } from "./wheel.js";
import { AuthProvider } from "./auth.jsx";
import { ToastProvider } from "./hooks.jsx";

keepServicesAwake();
scrollSidewaysWithWheel();

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <AuthProvider>
      <ToastProvider>
        <App />
      </ToastProvider>
    </AuthProvider>
  </StrictMode>,
);
