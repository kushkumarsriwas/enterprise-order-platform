import { KeyRound, ShieldAlert, ShieldCheck } from "lucide-react";
import { api, qs } from "../api.js";
import { loginColumns, portalName } from "../components/account.jsx";
import { ActiveFilters, DebouncedSearch, PageHead, PeriodPicker, Toolbar, useFilters } from "../components/common.jsx";
import { DataTable, Empty, ErrorState, ExportMenu, Loader, Pagination, Panel, PillToggle, Select, Stat } from "../components/ui.jsx";
import { formatNumber } from "../format.js";
import { useLoader } from "../hooks.jsx";

const RESULTS = [
  { value: "", label: "All" },
  { value: "true", label: "Successful" },
  { value: "false", label: "Failed" },
];

const PORTALS = [
  { value: "", label: "Every sign-in page" },
  { value: "CUSTOMER", label: "Marketplace" },
  { value: "SELLER", label: "Seller Central" },
  { value: "ADMIN", label: "Control Center" },
];

/** Every sign-in attempt on the three sign-in pages, kept for audits and to spot password guessing. */
export function SecurityPage({ route, navigate }) {
  const [f, set, clear] = useFilters(route, navigate, {});
  const page = Number(f.page || 0);
  const query = { success: f.success, portal: f.portal, search: f.q, from: f.from, to: f.to };
  const stats = useLoader(() => api("/api/admin/users/stats"), []);
  const logins = useLoader(() => api(`/api/admin/security/logins${qs({ ...query, page, size: 30 })}`), [JSON.stringify(query), page]);
  const s = stats.data;
  const data = logins.data;

  return (
    <>
      <PageHead
        title="Security log"
        sub="Every sign-in attempt on the marketplace, Seller Central and this console, with the IP address and device. Many failures from one IP address usually mean someone is guessing passwords."
        actions={<ExportMenu path="/api/admin/security/logins/export/{format}" query={qs(query)} />}
      />
      <div className="cc-stack">
        {s && (
          <div className="stats">
            <Stat label="Sign-ins, last 24 h" icon={ShieldCheck} value={formatNumber(s.signInsLast24h)} />
            <Stat label="Failed, last 24 h" icon={KeyRound} value={formatNumber(s.failedSignInsLast24h)} attention={s.failedSignInsLast24h > 0} />
            <Stat
              label="IP addresses failing repeatedly"
              icon={ShieldAlert}
              value={formatNumber(s.failingIpsLast24h)}
              attention={s.failingIpsLast24h > 0}
              hint="last 24 h"
            />
          </div>
        )}
        <Panel flush>
          <Toolbar>
            <DebouncedSearch value={f.q} onChange={(q) => set({ q })} placeholder="Username or IP address" />
            <PillToggle label="Result" options={RESULTS} value={f.success || ""} onChange={(success) => set({ success })} />
            <Select
              className="select-sm"
              value={f.portal || ""}
              options={PORTALS}
              onChange={(e) => set({ portal: e.target.value })}
              aria-label="Sign-in page"
            />
            <PeriodPicker from={f.from} to={f.to} allowEmpty onChange={(r) => set(r)} />
          </Toolbar>
          <ActiveFilters
            onClear={() => clear()}
            chips={[
              { key: "q", label: `"${f.q}"`, value: f.q, onRemove: () => set({ q: "" }) },
              { key: "portal", label: portalName(f.portal), value: f.portal, onRemove: () => set({ portal: "" }) },
              { key: "period", label: `${f.from} to ${f.to}`, value: f.from, onRemove: () => set({ from: "", to: "" }) },
            ]}
          />
          {logins.loading && !data ? (
            <Loader text="Loading the security log" />
          ) : logins.error && !data ? (
            <ErrorState message={logins.error} onRetry={logins.reload} />
          ) : (
            <div style={{ opacity: logins.loading ? 0.6 : 1 }}>
              <DataTable rows={data.content} columns={loginColumns()} empty={<Empty icon={ShieldCheck} title="No sign-in attempt matches" />} />
              <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={30} onPage={(p) => set({ page: p })} />
            </div>
          )}
        </Panel>
      </div>
    </>
  );
}
