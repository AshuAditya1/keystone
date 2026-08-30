/**
 * The application frame: brand, role-aware navigation, notification bell, and the
 * signed-in user.
 *
 * The nav list is filtered by role, which is a *usability* decision, not a
 * security one. Hiding "Team" from a technician stops them walking into a 403;
 * it does not stop them reaching `/users`, and it is not what protects the data.
 * Every endpoint behind every link re-checks the caller's role and scope
 * server-side, so a technician who types the URL gets an empty screen with a
 * "You do not have permission" banner rather than someone else's records.
 */

import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth";
import { roleLabel } from "../format";
import type { Role } from "../types";
import { NotificationBell } from "./NotificationBell";

interface NavItem {
  to: string;
  label: string;
  /** Roles that have any use for this screen. Empty means everyone. */
  roles?: Role[];
}

const NAV: NavItem[] = [
  { to: "/", label: "Dashboard", roles: ["MANAGER", "DISPATCHER"] },
  { to: "/my-work", label: "My work", roles: ["TECHNICIAN"] },
  { to: "/work-orders", label: "Work orders" },
  { to: "/board", label: "Board", roles: ["MANAGER", "DISPATCHER", "TECHNICIAN"] },
  { to: "/customers", label: "Customers", roles: ["MANAGER", "DISPATCHER"] },
  { to: "/sites", label: "Sites", roles: ["MANAGER", "DISPATCHER", "CUSTOMER"] },
  { to: "/parts", label: "Inventory", roles: ["MANAGER", "DISPATCHER", "TECHNICIAN"] },
  { to: "/team", label: "Team", roles: ["MANAGER"] },
];

export function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  // `user` is guaranteed by the RequireAuth gate above this component, but the
  // type is nullable, so the guard stays rather than a non-null assertion.
  if (!user) {
    return null;
  }

  const visible = NAV.filter((item) => !item.roles || item.roles.includes(user.role));

  const signOut = () => {
    logout();
    navigate("/login", { replace: true });
  };

  return (
    <div className="shell">
      <header className="topbar">
        <div className="topbar-left">
          <h1 className="brand">
            KEY<span>STONE</span>
          </h1>
          <nav className="main-nav" aria-label="Main">
            {visible.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                // `end` keeps the dashboard link from matching every child route.
                end={item.to === "/"}
                className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        </div>

        <div className="user-chip">
          <NotificationBell />
          <span className="role-badge">{roleLabel(user.role)}</span>
          <span className="user-name">{user.fullName}</span>
          <button type="button" className="btn-link" onClick={signOut}>
            Sign out
          </button>
        </div>
      </header>

      <main className="content">
        <Outlet />
      </main>

      <footer className="app-footer">
        <span className="muted">
          Meridian Facilities Management · Project KEYSTONE · signed in as {user.email}
        </span>
      </footer>
    </div>
  );
}
