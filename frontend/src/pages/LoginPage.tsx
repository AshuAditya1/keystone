import { useState, type FormEvent } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../auth";
import { errorMessage } from "../api";

// The four seeded demo accounts share this password, and printing it here is
// deliberate: this is a portfolio build whose reviewer needs to sign in as each
// role without being sent credentials separately.
//
// Being honest about what that costs — V2__seed_data.sql is a plain Flyway
// migration, so these accounts exist in every environment the migrations run
// against, including Render. That is acceptable for a demo and would not be for
// a real tenant; the note in README under "Before real use" says so and says to
// delete the seed users and rotate this password.
// keystone:allow-secret documented demo credential, seeded by V2__seed_data.sql
const DEMO_PASSWORD = "ChangeMe123!";

/** The seeded accounts, so a reviewer can switch roles without typing. */
const DEMO_ACCOUNTS = [
  { email: "manager@meridian.dev", label: "Manager" },
  { email: "dispatcher@meridian.dev", label: "Dispatcher" },
  { email: "tech1@meridian.dev", label: "Technician" },
  { email: "alice@acme.dev", label: "Customer" },
];

/**
 * Login.
 *
 * The failure message is whatever the server said, and the server deliberately
 * says the same thing for an unknown email as for a wrong password — narrowing it
 * down would turn this form into a way to discover who has an account.
 */
export function LoginPage() {
  const { login, user, loading } = useAuth();
  const location = useLocation();
  const [email, setEmail] = useState("manager@meridian.dev");
  const [password, setPassword] = useState(DEMO_PASSWORD);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Where the user was heading before the auth gate intercepted them.
  const state = location.state as { from?: string } | null;
  const from = state?.from && state.from !== "/login" ? state.from : "/";

  if (loading) {
    return <div className="center-screen">Restoring your session…</div>;
  }
  if (user) {
    return <Navigate to={from} replace />;
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email.trim(), password);
      // No navigate() call: `user` becomes non-null, this component re-renders,
      // and the guard above redirects. One path in, one path out.
    } catch (err: unknown) {
      setError(errorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="login-wrap">
      <form className="login-card" onSubmit={(event) => void submit(event)}>
        <h1 className="brand">
          KEY<span>STONE</span>
        </h1>
        <p className="subtitle">Meridian Facilities Management · Field Service Platform</p>

        {error ? (
          <div className="error-banner" role="alert">
            {error}
          </div>
        ) : null}

        <div className="field">
          <label htmlFor="email">Email</label>
          <input
            id="email"
            type="email"
            autoComplete="username"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
        </div>

        <div className="field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </div>

        <button className="btn-primary" type="submit" disabled={submitting}>
          {submitting ? "Signing in…" : "Sign in"}
        </button>

        <div className="hint">
          <strong>Demo accounts</strong> — password <code>{DEMO_PASSWORD}</code>. Each role sees a
          different application:
          <div className="demo-row">
            {DEMO_ACCOUNTS.map((account) => (
              <button
                key={account.email}
                type="button"
                className="btn-ghost small"
                onClick={() => {
                  setEmail(account.email);
                  setPassword(DEMO_PASSWORD);
                }}
              >
                {account.label}
              </button>
            ))}
          </div>
        </div>
      </form>
    </div>
  );
}
