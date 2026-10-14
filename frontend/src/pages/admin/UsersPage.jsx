import { useState } from "react";
import { api, qs } from "../../api.js";
import { formatDate } from "../../format.js";
import { Alert, Badge, Pagination, Spinner } from "../../components/ui.jsx";
import { useLoader } from "../../hooks.jsx";

export default function UsersPage({ currentUser, toast }) {
  const [page, setPage] = useState(0);
  const users = useLoader(() => api(`/api/users${qs({ page, size: 20 })}`), [page]);

  const toggleRole = async (user) => {
    const role = user.role === "ADMIN" ? "CUSTOMER" : "ADMIN";
    if (!window.confirm(`Change ${user.username} to ${role}?`)) return;
    try {
      await api(`/api/users/${user.id}/role`, { method: "PUT", body: { role } });
      toast.show(`${user.username} is now ${role}`);
      users.reload();
    } catch (err) {
      toast.show(err.message, "error");
    }
  };

  return (
    <div className="page">
      <div className="page-head"><h1>Users</h1></div>
      <Alert>{users.error}</Alert>
      {users.loading && !users.data ? <Spinner /> : (
        <div className="card">
          <table className="table">
            <thead><tr><th>User</th><th>Email</th><th>Role</th><th>Joined</th><th>Last login</th><th className="right" /></tr></thead>
            <tbody>
              {users.data?.content.map((u) => (
                <tr key={u.id}>
                  <td><strong>{u.username}</strong><div className="muted small">{u.fullName}</div></td>
                  <td>{u.email}</td>
                  <td><Badge value={u.role} /></td>
                  <td className="nowrap">{formatDate(u.createdAt)}</td>
                  <td className="nowrap">{formatDate(u.lastLoginAt)}</td>
                  <td className="right">
                    {u.id !== currentUser.id && (
                      <button className="btn btn-sm btn-ghost" onClick={() => toggleRole(u)}>
                        Make {u.role === "ADMIN" ? "customer" : "admin"}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pagination page={users.data} onChange={setPage} />
    </div>
  );
}
