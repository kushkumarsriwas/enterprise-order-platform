import { useState } from "react";
import { Ban, RotateCcw, UserRound } from "lucide-react";
import { api } from "../api.js";
import { AccountBadge, AccountStatusDialog, RecentLogins, SuspensionNotice } from "../components/account.jsx";
import { KeyValues, PageHead } from "../components/common.jsx";
import { PagedPanel, orderColumns, paymentColumns } from "../components/tables.jsx";
import { Button, Empty, ErrorState, Loader, Panel, Stat, StatusBadge } from "../components/ui.jsx";
import { formatDate, formatDay, formatMoney, formatNumber, timeAgo } from "../format.js";
import { useLoader } from "../hooks.jsx";
import { AccountList, joinedColumns } from "./StoresPages.jsx";

export function CustomerListPage(props) {
  return (
    <AccountList
      {...props}
      role="CUSTOMER"
      title="Customers"
      sub="Everyone who shops on Nexora. Open a customer to see their orders, payments, conversations and sign-ins, or to suspend the account."
      searchPlaceholder="Name, username, e-mail or phone"
      columns={[
        {
          key: "customer",
          header: "Customer",
          render: (u) => (
            <div>
              <div className="cell-title">{u.fullName || u.username}</div>
              <div className="cell-sub">{u.username}</div>
            </div>
          ),
        },
        { key: "email", header: "E-mail", render: (u) => u.email },
        { key: "phone", header: "Phone", render: (u) => <span className="nowrap">{u.phone || "-"}</span> },
        {
          key: "city",
          header: "Delivers to",
          render: (u) => [u.address?.city, u.address?.region].filter((v, i, all) => v && all.indexOf(v) === i).join(", ") || "-",
        },
        ...joinedColumns,
      ]}
    />
  );
}

export function CustomerPage({ params, navigate }) {
  const id = params.id;
  const [dialog, setDialog] = useState(false);
  const detail = useLoader(() => api(`/api/admin/users/${id}`), [id]);
  const purchases = useLoader(() => api(`/api/admin/customers/${id}/purchases`).catch(() => null), [id]);

  if (detail.loading && !detail.data) return <Loader text="Loading the customer" />;
  if (detail.error && !detail.data) return <ErrorState message={detail.error} onRetry={detail.reload} />;
  const u = detail.data.user;
  if (u.role !== "CUSTOMER") {
    return (
      <Empty icon={UserRound} title="This account is not a customer" action={<a href={`#/stores/${u.id}`}>Open the store's page</a>}>
        {u.username} sells on Nexora.
      </Empty>
    );
  }
  const p = purchases.data;
  const address = u.address;

  return (
    <>
      <PageHead
        title={u.fullName || u.username}
        badges={<AccountBadge user={u} />}
        back={{ to: "/customers", label: "Customers" }}
        sub={`${u.username}, customer since ${formatDay(u.createdAt)}. Last signed in ${u.lastLoginAt ? timeAgo(u.lastLoginAt) : "never"}.`}
        actions={
          u.active ? (
            <Button variant="danger" icon={Ban} onClick={() => setDialog(true)}>
              Suspend customer
            </Button>
          ) : (
            <Button variant="primary" icon={RotateCcw} onClick={() => setDialog(true)}>
              Reactivate customer
            </Button>
          )
        }
      />
      {!u.active && (
        <div style={{ marginBottom: 16 }}>
          <SuspensionNotice user={u} />
        </div>
      )}
      <div className="cc-stack">
        <div className="stats">
          <Stat label="Orders" value={p ? formatNumber(p.orders) : "-"} hint="confirmed, shipped or delivered" />
          <Stat label="Spent" value={p ? formatMoney(p.spent) : "-"} hint="on those orders, VAT included" />
          <Stat label="Cancelled" value={p ? formatNumber(p.cancelledOrders) : "-"} hint="orders" upIsGood={false} />
          <Stat label="First order" value={p?.firstOrderAt ? formatDay(p.firstOrderAt) : "-"} />
          <Stat label="Last order" value={p?.lastOrderAt ? formatDay(p.lastOrderAt) : "-"} hint={p?.lastOrderAt ? timeAgo(p.lastOrderAt) : undefined} />
        </div>
        <div className="grid-main">
          <div className="cc-stack">
            <PagedPanel
              title="Orders"
              path="/api/admin/orders"
              query={{ customerId: id }}
              columns={orderColumns({ customer: false })}
              onRowClick={(o) => navigate(`/orders/${o.id}`)}
              empty="No orders yet"
            />
            <PagedPanel
              title="Payments"
              path="/api/admin/payments"
              query={{ customerId: id }}
              columns={paymentColumns({ customer: false })}
              empty="No payments yet"
            />
            <PagedPanel
              title="Conversations with stores"
              path="/api/admin/conversations"
              query={{ customerId: id }}
              size={8}
              onRowClick={(c) => navigate(`/messages?open=${c.id}`)}
              empty="No conversations"
              columns={[
                { key: "s", header: "Store", render: (c) => <span className="strong">{c.sellerName}</span> },
                {
                  key: "m",
                  header: "Last message",
                  render: (c) => (
                    <span className="ellipsis" style={{ display: "block", maxWidth: 360 }}>
                      {c.lastMessagePreview}
                    </span>
                  ),
                },
                { key: "r", header: "", render: (c) => (c.reported ? <StatusBadge status="REPORTED" /> : null) },
                { key: "t", header: "When", render: (c) => <span className="nowrap">{timeAgo(c.lastMessageAt)}</span> },
              ]}
            />
            <RecentLogins detail={detail.data} />
          </div>
          <div className="cc-stack">
            <Panel title="Contact">
              <KeyValues
                items={[
                  { label: "Username", value: u.username },
                  { label: "E-mail", value: <a href={`mailto:${u.email}`}>{u.email}</a> },
                  { label: "Phone", value: u.phone || "-" },
                  { label: "Account created", value: formatDate(u.createdAt) },
                  u.statusChangedAt && { label: "Status changed", value: formatDate(u.statusChangedAt) },
                ]}
              />
            </Panel>
            <Panel title="Default delivery address">
              {address ? (
                <KeyValues
                  items={[
                    { label: "Recipient", value: address.recipient },
                    { label: "Phone", value: address.phone || "-" },
                    { label: "Address", value: [address.line1, address.line2].filter(Boolean).join(", ") },
                    { label: "City", value: [address.city, address.region].filter((v, i, all) => v && all.indexOf(v) === i).join(", ") },
                    { label: "Country", value: address.country || "United Arab Emirates" },
                  ]}
                />
              ) : (
                <p className="small muted">No default address saved. Each order keeps the address entered at checkout.</p>
              )}
            </Panel>
          </div>
        </div>
      </div>
      {dialog && (
        <AccountStatusDialog
          user={u}
          open
          onClose={() => setDialog(false)}
          onDone={() => {
            setDialog(false);
            detail.reload();
          }}
        />
      )}
    </>
  );
}
