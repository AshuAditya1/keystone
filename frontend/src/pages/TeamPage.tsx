/**
 * Team administration. Managers only — the whole controller is behind
 * `hasRole('MANAGER')`, so a dispatcher who types this URL gets bounced by the
 * route guard and, if they forge the request anyway, by the server.
 *
 * There is no delete button, because there is no delete endpoint. Accounts are
 * deactivated instead: every status change, part and time entry in the system
 * names the person who made it, and those records have to keep pointing at a real
 * account long after someone has left.
 *
 * Two rules will push back from the server rather than from here, and that is
 * deliberate — they depend on data this page does not hold:
 *   - you cannot demote or deactivate your own manager account (no lockouts)
 *   - a technician still holding open jobs cannot be deactivated or re-roled
 *     until that work is handed over
 * Both come back as a plain-language 409 which is shown as-is.
 */

import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { fieldErrors } from "../api";
import { useAuth } from "../auth";
import { ErrorBanner, Field, Loading, Modal, PageHeader, Stat } from "../components/ui";
import { customerApi, userApi } from "../endpoints";
import { roleLabel, slug } from "../format";
import { useAction, useApi } from "../hooks";
import { ROLES, type Role, type UserSummary } from "../types";

interface FormState {
  id: number | null;
  email: string;
  password: string;
  fullName: string;
  role: Role;
  customerId: string;
  active: boolean;
}

const BLANK: FormState = {
  id: null,
  email: "",
  password: "",
  fullName: "",
  role: "TECHNICIAN",
  customerId: "",
  active: true,
};

/** What each role is for, shown next to the picker so the choice is informed. */
const ROLE_BLURB: Record<Role, string> = {
  MANAGER: "Full access, including this screen, customers and the parts catalogue.",
  DISPATCHER: "Raises and assigns work. Cannot manage accounts or the catalogue.",
  TECHNICIAN: "Sees only their own jobs. Logs parts and time, and completes work.",
  CUSTOMER: "Portal access to their own organisation's jobs only. Must name a customer.",
};

export function TeamPage() {
  const { user } = useAuth();

  const [roleFilter, setRoleFilter] = useState<Role | "">("");
  const [search, setSearch] = useState("");
  const [showInactive, setShowInactive] = useState(true);
  const [form, setForm] = useState<FormState | null>(null);
  const [invalid, setInvalid] = useState<Record<string, string>>({});
  const [confirming, setConfirming] = useState<UserSummary | null>(null);

  const action = useAction();

  // The endpoint is unpaged and the staff list is small, so filtering by name
  // happens here rather than as a round trip.
  const listing = useApi(() => userApi.list(roleFilter || null), [roleFilter]);

  const customers = useApi(
    () => customerApi.list({ size: 200, sort: "name,asc" }).then((paged) => paged.content),
    []
  );

  const all = listing.data ?? [];

  const rows = useMemo(() => {
    const needle = search.trim().toLowerCase();
    return all.filter((row) => {
      if (!showInactive && !row.active) {
        return false;
      }
      if (needle === "") {
        return true;
      }
      return (
        row.fullName.toLowerCase().includes(needle) || row.email.toLowerCase().includes(needle)
      );
    });
  }, [all, search, showInactive]);

  const inactiveCount = all.filter((row) => !row.active).length;

  const openCreate = () => {
    setInvalid({});
    action.clearError();
    setForm(BLANK);
  };

  const openEdit = (row: UserSummary) => {
    setInvalid({});
    action.clearError();
    setForm({
      id: row.id,
      email: row.email,
      password: "",
      fullName: row.fullName,
      role: row.role,
      customerId: row.customerId === null ? "" : String(row.customerId),
      active: row.active,
    });
  };

  const save = () =>
    action.run(async () => {
      if (!form) {
        return;
      }
      setInvalid({});
      const customerId = form.role === "CUSTOMER" ? Number(form.customerId) : null;
      try {
        if (form.id === null) {
          await userApi.create({
            email: form.email.trim().toLowerCase(),
            password: form.password,
            fullName: form.fullName.trim(),
            role: form.role,
            customerId,
          });
        } else {
          // A patch: the email is the account's identity and is not editable, and
          // an empty password box means "leave the password alone".
          await userApi.update(form.id, {
            fullName: form.fullName.trim(),
            role: form.role,
            active: form.active,
            customerId,
            password: form.password.trim() === "" ? null : form.password,
          });
        }
        setForm(null);
        listing.reload();
      } catch (err: unknown) {
        setInvalid(fieldErrors(err));
        throw err;
      }
    });

  const setActive = (row: UserSummary, active: boolean) =>
    action.run(async () => {
      await userApi.update(row.id, { active });
      setConfirming(null);
      listing.reload();
    });

  const customerRequired = form !== null && form.role === "CUSTOMER";
  const saveBlocked =
    form === null ||
    action.busy ||
    form.fullName.trim() === "" ||
    (form.id === null && (form.email.trim() === "" || form.password.length < 8)) ||
    (customerRequired && form.customerId === "");

  return (
    <>
      <PageHeader
        title="Team"
        subtitle="Who can sign in, and what they are allowed to do."
        actions={
          <button type="button" className="btn-primary inline" onClick={openCreate}>
            Add person
          </button>
        }
      />

      {action.error ? <ErrorBanner message={action.error} onDismiss={action.clearError} /> : null}
      {listing.error ? <ErrorBanner message={listing.error} /> : null}

      <section className="stat-row">
        <Stat label="Accounts" value={all.length} />
        <Stat
          label="Active"
          value={all.length - inactiveCount}
          tone="good"
          hint="Able to sign in"
        />
        <Stat
          label="Deactivated"
          value={inactiveCount}
          hint="Kept for the audit trail"
          tone={inactiveCount > 0 ? "warn" : "default"}
        />
        <Stat
          label="Engineers"
          value={all.filter((row) => row.role === "TECHNICIAN" && row.active).length}
          hint="Assignable to jobs"
        />
      </section>

      <section className="card">
        <div className="filter-row">
          <Field label="Search" htmlFor="team-search" hint="Name or email">
            <input
              id="team-search"
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="e.g. Priya"
            />
          </Field>
        </div>

        <div className="chip-row">
          <button
            type="button"
            className={roleFilter === "" ? "chip on" : "chip"}
            onClick={() => setRoleFilter("")}
            aria-pressed={roleFilter === ""}
          >
            All roles
          </button>
          {ROLES.map((role) => (
            <button
              key={role}
              type="button"
              className={roleFilter === role ? "chip on" : "chip"}
              onClick={() => setRoleFilter(role)}
              aria-pressed={roleFilter === role}
            >
              {roleLabel(role)}
            </button>
          ))}
          <span className="chip-divider" aria-hidden="true" />
          <button
            type="button"
            className={showInactive ? "chip on" : "chip"}
            onClick={() => setShowInactive(!showInactive)}
            aria-pressed={showInactive}
          >
            Show deactivated
          </button>
        </div>

        {listing.loading && !listing.data ? (
          <Loading label="Loading the team…" />
        ) : (
          <>
            {rows.length === 0 ? (
              <p className="muted">Nobody matches that.</p>
            ) : (
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Name</th>
                      <th>Email</th>
                      <th>Role</th>
                      <th>Organisation</th>
                      <th>Status</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((row) => {
                      const isMe = user?.id === row.id;
                      return (
                        <tr key={row.id} className={row.active ? "" : "row-muted"}>
                          <td>
                            <strong>{row.fullName}</strong>
                            {isMe ? <span className="count-badge">you</span> : null}
                          </td>
                          <td>{row.email}</td>
                          <td>
                            <span className={`pill role-${slug(row.role)}`}>
                              {roleLabel(row.role)}
                            </span>
                          </td>
                          <td className={row.customerName ? "" : "muted"}>
                            {row.customerName ?? "Meridian"}
                          </td>
                          <td>
                            {row.active ? (
                              <span className="pill sla-on-track">Active</span>
                            ) : (
                              <span className="pill status-cancelled">Deactivated</span>
                            )}
                          </td>
                          <td className="row-actions">
                            {row.role === "TECHNICIAN" ? (
                              <Link
                                className="btn-link"
                                to={`/work-orders?assigneeId=${row.id}&openOnly=true`}
                              >
                                Jobs
                              </Link>
                            ) : null}
                            <button
                              type="button"
                              className="btn-link"
                              onClick={() => openEdit(row)}
                            >
                              Edit
                            </button>
                            {row.active ? (
                              <button
                                type="button"
                                className="btn-link danger"
                                disabled={isMe}
                                title={
                                  isMe ? "You cannot deactivate your own account." : undefined
                                }
                                onClick={() => {
                                  action.clearError();
                                  setConfirming(row);
                                }}
                              >
                                Deactivate
                              </button>
                            ) : (
                              <button
                                type="button"
                                className="btn-link"
                                disabled={action.busy}
                                onClick={() => void setActive(row, true)}
                              >
                                Reactivate
                              </button>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
            <p className="muted">
              Showing {rows.length} of {all.length}.
            </p>
          </>
        )}
      </section>

      {form ? (
        <Modal
          title={form.id === null ? "Add person" : `Edit ${form.fullName}`}
          onClose={() => setForm(null)}
          footer={
            <>
              <button type="button" className="btn-ghost" onClick={() => setForm(null)}>
                Cancel
              </button>
              <button
                type="button"
                className="btn-primary inline"
                disabled={saveBlocked}
                onClick={() => void save()}
              >
                {action.busy ? "Saving…" : "Save"}
              </button>
            </>
          }
        >
          <Field label="Full name" htmlFor="team-name" error={invalid.fullName}>
            <input
              id="team-name"
              type="text"
              value={form.fullName}
              onChange={(event) => setForm({ ...form, fullName: event.target.value })}
              required
            />
          </Field>

          <Field
            label="Email"
            htmlFor="team-email"
            error={invalid.email}
            hint={
              form.id === null
                ? "This is how they sign in. It must be unique."
                : "The sign-in address cannot be changed once the account exists."
            }
          >
            <input
              id="team-email"
              type="email"
              value={form.email}
              onChange={(event) => setForm({ ...form, email: event.target.value })}
              disabled={form.id !== null}
              required
            />
          </Field>

          <Field label="Role" htmlFor="team-role" error={invalid.role} hint={ROLE_BLURB[form.role]}>
            <select
              id="team-role"
              value={form.role}
              onChange={(event) => {
                const role = event.target.value as Role;
                setForm({
                  ...form,
                  role,
                  // A non-portal account must not carry a customer link.
                  customerId: role === "CUSTOMER" ? form.customerId : "",
                });
              }}
            >
              {ROLES.map((role) => (
                <option key={role} value={role}>
                  {roleLabel(role)}
                </option>
              ))}
            </select>
          </Field>

          {customerRequired ? (
            <Field
              label="Organisation"
              htmlFor="team-customer"
              error={invalid.customerId}
              hint="This link is what limits everything the portal user can see."
            >
              <select
                id="team-customer"
                value={form.customerId}
                onChange={(event) => setForm({ ...form, customerId: event.target.value })}
                required
              >
                <option value="">Choose a customer…</option>
                {(customers.data ?? []).map((customer) => (
                  <option key={customer.id} value={String(customer.id)}>
                    {customer.name}
                  </option>
                ))}
              </select>
            </Field>
          ) : null}

          <Field
            label={form.id === null ? "Password" : "Reset password"}
            htmlFor="team-password"
            error={invalid.password}
            hint={
              form.id === null
                ? "At least 8 characters. They should change it after first sign-in."
                : "Leave blank to keep the current password."
            }
          >
            <input
              id="team-password"
              type="password"
              value={form.password}
              autoComplete="new-password"
              onChange={(event) => setForm({ ...form, password: event.target.value })}
              required={form.id === null}
            />
          </Field>

          {form.id !== null ? (
            <Field label="Account" htmlFor="team-active">
              <span className="checkbox-row">
                <input
                  id="team-active"
                  type="checkbox"
                  checked={form.active}
                  onChange={(event) => setForm({ ...form, active: event.target.checked })}
                />
                <span>Can sign in</span>
              </span>
            </Field>
          ) : null}

          {action.error ? <ErrorBanner message={action.error} /> : null}
        </Modal>
      ) : null}

      {confirming ? (
        <Modal
          title={`Deactivate ${confirming.fullName}?`}
          onClose={() => setConfirming(null)}
          footer={
            <>
              <button type="button" className="btn-ghost" onClick={() => setConfirming(null)}>
                Cancel
              </button>
              <button
                type="button"
                className="btn-danger"
                disabled={action.busy}
                onClick={() => void setActive(confirming, false)}
              >
                {action.busy ? "Working…" : "Deactivate"}
              </button>
            </>
          }
        >
          <p>
            They will no longer be able to sign in, and they will disappear from the assignment
            picker. Nothing they have already done is removed — their name stays on every job they
            touched.
          </p>
          {confirming.role === "TECHNICIAN" ? (
            <p className="muted">
              If they still hold open jobs the server will refuse this until the work is reassigned.
            </p>
          ) : null}
          {action.error ? <ErrorBanner message={action.error} /> : null}
        </Modal>
      ) : null}
    </>
  );
}
