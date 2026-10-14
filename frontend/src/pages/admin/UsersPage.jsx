import { useState } from "react";
import { Users } from "lucide-react";
import { api, qs } from "../../api.js";
import { formatDate, formatDay } from "../../format.js";
import { useLoader } from "../../hooks.jsx";
import { Alert, Badge, Empty, Modal, PageHeader, Pagination, Spinner, Tabs } from "../../components/ui.jsx";

export default function AdminUsers({ user: me, toast }) {
  const [role, setRole] = useState("");
  const [page, setPage] = useState(0);
  const users = useLoader(() => api(`/api/users${qs({ role, page, size: 20 })}`), [role, page]);
  const [confirm, setConfirm] = useState(null);

  const setStatus = async () => {
    const { user, enabled } = confirm;
    try {
      await api(`/api/users/${user.id}/status`, { method: "PUT", body: { enabled } });
      toast.show(enabled ? `${user.username} reactivated` : `${user.username} suspended`);
      setConfirm(null);
      users.reload();
    } catch (err) {
      toast.show(err.message, "error");
    }
  };

  return (
    <div className="page">
      <PageHeader title="Users and stores" subtitle="Customers, merchants and administrators. Suspending a merchant hides the store and blocks sign-in." />
      <Tabs
        value={role}
        onChange={(r) => { setRole(r); setPage(0); }}
        tabs={[
          { id: "", label: "Everyone" },
          { id: "CUSTOMER", label: "Customers" },
          { id: "MERCHANT", label: "Merchants" },
          { id: "ADMIN", label: "Administrators" },
        ]}
      />
      <Alert>{users.error}</Alert>
      {users.loading && !users.data ? (
        <Spinner />
      ) : users.data?.content.length === 0 ? (
        <Empty icon={Users} title="Nobody here yet" />
      ) : (
        <div className={`card flush ${users.loading ? "refetching" : ""}`}>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>User</th>
                  <th>Role</th>
                  <th>Store</th>
                  <th>Joined</th>
                  <th>Last sign-in</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {users.data?.content.map((u) => (
                  <tr key={u.id} className={u.enabled ? "" : "inactive"}>
                    <td>
                      <strong>{u.fullName || u.username}</strong>
                      <span className="sub">
                        {u.username} - {u.email}
                      </span>
                    </td>
                    <td>
                      <Badge value={u.role} />
                    </td>
                    <td>
                      {u.store ? (
                        <>
                          <a className="link" href={`#/store/${u.id}`}>
                            {u.store.storeName}
                          </a>
                          <span className="sub">
                            {u.store.city}
                            {u.store.tradeLicenseNumber ? ` - licence ${u.store.tradeLicenseNumber}` : ""}
                          </span>
                        </>
                      ) : (
                        <span className="muted">-</span>
                      )}
                    </td>
                    <td className="nowrap">{formatDay(u.createdAt)}</td>
                    <td className="nowrap">{u.lastLoginAt ? formatDate(u.lastLoginAt) : "Never"}</td>
                    <td>
                      <Badge value={u.enabled ? "ACTIVE" : "SUSPENDED"} />
                    </td>
                    <td className="right">
                      {u.id !== me.id && u.role !== "ADMIN" && (
                        <button className={`btn btn-sm ${u.enabled ? "btn-danger" : ""}`} onClick={() => setConfirm({ user: u, enabled: !u.enabled })}>
                          {u.enabled ? "Suspend" : "Reactivate"}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
      <Pagination page={users.data} onChange={setPage} />
      {confirm && (
        <Modal title={`${confirm.enabled ? "Reactivate" : "Suspend"} ${confirm.user.username}?`} onClose={() => setConfirm(null)}>
          <div className="stack">
            <p>
              {confirm.enabled
                ? "The user can sign in again" + (confirm.user.store ? " and the store is visible on the marketplace again." : ".")
                : "The user cannot sign in any more" + (confirm.user.store ? " and the store disappears from the marketplace." : ".")}
            </p>
            <div className="row end">
              <button className="btn btn-ghost" onClick={() => setConfirm(null)}>
                Cancel
              </button>
              <button className={`btn ${confirm.enabled ? "btn-primary" : "btn-danger"}`} onClick={setStatus}>
                {confirm.enabled ? "Reactivate" : "Suspend"}
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}
