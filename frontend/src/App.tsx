/**
 * The route table.
 *
 * Two gates sit in front of the pages. `RequireAuth` bounces anyone without a
 * session to the login screen. `RequireRole` keeps a role out of a screen that
 * would only ever return 403 for them — a courtesy, not a control. The server
 * enforces the same rules on every request; if these gates were deleted the
 * application would still be secure, just ruder.
 *
 * The landing route differs by role, because "home" is not the same place for a
 * dispatcher and a technician: one wants the whole queue, the other wants today's
 * five jobs.
 */

import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import type { ReactElement } from "react";
import { Layout } from "./components/Layout";
import { useAuth } from "./auth";
import type { Role } from "./types";
import { BoardPage } from "./pages/BoardPage";
import { CustomersPage } from "./pages/CustomersPage";
import { DashboardPage } from "./pages/DashboardPage";
import { LoginPage } from "./pages/LoginPage";
import { MyWorkPage } from "./pages/MyWorkPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { PartsPage } from "./pages/PartsPage";
import { SitesPage } from "./pages/SitesPage";
import { TeamPage } from "./pages/TeamPage";
import { WorkOrderDetailPage } from "./pages/WorkOrderDetailPage";
import { WorkOrderFormPage } from "./pages/WorkOrderFormPage";
import { WorkOrdersPage } from "./pages/WorkOrdersPage";

function RequireAuth({ children }: { children: ReactElement }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return <div className="center-screen">Restoring your session…</div>;
  }
  if (!user) {
    // Remember where they were headed so login can send them back there.
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return children;
}

function RequireRole({ roles, children }: { roles: Role[]; children: ReactElement }) {
  const { user } = useAuth();
  if (user && !roles.includes(user.role)) {
    return <Navigate to="/" replace />;
  }
  return children;
}

/** Where each role starts. A technician's queue is not a manager's dashboard. */
function Home() {
  const { user } = useAuth();
  if (!user) {
    return null;
  }
  if (user.role === "TECHNICIAN") {
    return <Navigate to="/my-work" replace />;
  }
  if (user.role === "CUSTOMER") {
    return <Navigate to="/work-orders" replace />;
  }
  return <DashboardPage />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route path="/" element={<Home />} />

        <Route path="/work-orders" element={<WorkOrdersPage />} />
        {/* "new" is declared before ":id" so it is not read as an id. */}
        <Route
          path="/work-orders/new"
          element={
            <RequireRole roles={["MANAGER", "DISPATCHER", "CUSTOMER"]}>
              <WorkOrderFormPage />
            </RequireRole>
          }
        />
        <Route path="/work-orders/:id" element={<WorkOrderDetailPage />} />
        <Route
          path="/work-orders/:id/edit"
          element={
            <RequireRole roles={["MANAGER", "DISPATCHER"]}>
              <WorkOrderFormPage />
            </RequireRole>
          }
        />

        <Route
          path="/board"
          element={
            <RequireRole roles={["MANAGER", "DISPATCHER", "TECHNICIAN"]}>
              <BoardPage />
            </RequireRole>
          }
        />

        <Route
          path="/my-work"
          element={
            <RequireRole roles={["TECHNICIAN", "MANAGER"]}>
              <MyWorkPage />
            </RequireRole>
          }
        />

        <Route
          path="/customers"
          element={
            <RequireRole roles={["MANAGER", "DISPATCHER"]}>
              <CustomersPage />
            </RequireRole>
          }
        />

        <Route
          path="/sites"
          element={
            <RequireRole roles={["MANAGER", "DISPATCHER", "CUSTOMER"]}>
              <SitesPage />
            </RequireRole>
          }
        />

        <Route
          path="/parts"
          element={
            <RequireRole roles={["MANAGER", "DISPATCHER", "TECHNICIAN"]}>
              <PartsPage />
            </RequireRole>
          }
        />

        <Route
          path="/team"
          element={
            <RequireRole roles={["MANAGER"]}>
              <TeamPage />
            </RequireRole>
          }
        />

        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
