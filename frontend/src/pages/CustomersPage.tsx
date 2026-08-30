/**
 * Customers.
 *
 * Creating and editing is manager-only; a dispatcher can read the list. The
 * buttons below follow that, but the enforcement is the `@PreAuthorize` on the
 * controller — a dispatcher who forges the request still gets a 403.
 *
 * Deleting is offered but expected to fail most of the time, and that is correct
 * behaviour rather than a bug: a customer with sites or jobs is referenced by
 * records that must not lose their history, so the server refuses with a 409 and
 * the message is shown as-is.
 */

import { useState } from "react";
import { Link } from "react-router-dom";
import { fieldErrors } from "../api";
import { useAuth } from "../auth";
import {
  ErrorBanner,
  Field,
  Loading,
  Modal,
  PageHeader,
  Pagination,
} from "../components/ui";
import { customerApi } from "../endpoints";
import { fallback } from "../format";
import { useApi, useAction, useDebounced } from "../hooks";
import type { CustomerView } from "../types";

interface FormState {
  id: number | null;
  name: string;
  contactEmail: string;
  contactPhone: string;
}

const BLANK: FormState = { id: null, name: "", contactEmail: "", contactPhone: "" };

export function CustomersPage() {
  const { hasRole } = useAuth();
  const canManage = hasRole("MANAGER");

  const [searchInput, setSearchInput] = useState("");
  const search = useDebounced(searchInput);
  const [page, setPage] = useState(0);
  const [form, setForm] = useState<FormState | null>(null);
  const [invalid, setInvalid] = useState<Record<string, string>>({});
  const [confirming, setConfirming] = useState<CustomerView | null>(null);

  const action = useAction();
  const listing = useApi(
    () => customerApi.list({ search: search || null, page, size: 20, sort: "name,asc" }),
    [search, page]
  );

  const save = () =>
    action.run(async () => {
      if (!form) {
        return;
      }
      setInvalid({});
      const body = {
        name: form.name.trim(),
        contactEmail: form.contactEmail.trim() || null,
        contactPhone: form.contactPhone.trim() || null,
      };
      try {
        if (form.id === null) {
          await customerApi.create(body);
        } else {
          await customerApi.update(form.id, body);
        }
        setForm(null);
        listing.reload();
      } catch (err: unknown) {
        setInvalid(fieldErrors(err));
        throw err;
      }
    });

  const remove = (customer: CustomerView) =>
    action.run(async () => {
      await customerApi.remove(customer.id);
      setConfirming(null);
      listing.reload();
    });

  return (
    <>
      <PageHeader
        title="Customers"
        subtitle="The organisations Meridian holds contracts with."
        actions={
          canManage ? (
            <button
              type="button"
              className="btn-primary inline"
              onClick={() => {
                setInvalid({});
                action.clearError();
                setForm(BLANK);
              }}
            >
              Add customer
            </button>
          ) : null
        }
      />

      {action.error ? <ErrorBanner message={action.error} onDismiss={action.clearError} /> : null}
      {listing.error ? <ErrorBanner message={listing.error} /> : null}

      <section className="card">
        <div className="filter-row">
          <Field label="Search" htmlFor="cust-search" hint="Name or contact email">
            <input
              id="cust-search"
              type="search"
              value={searchInput}
              onChange={(event) => {
                setSearchInput(event.target.value);
                setPage(0);
              }}
              placeholder="e.g. Acme"
            />
          </Field>
        </div>

        {listing.loading && !listing.data ? (
          <Loading />
        ) : listing.data ? (
          <>
            {listing.data.content.length === 0 ? (
              <p className="muted">No customers match that search.</p>
            ) : (
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Name</th>
                      <th>Email</th>
                      <th>Phone</th>
                      <th>Sites</th>
                      {canManage ? <th /> : null}
                    </tr>
                  </thead>
                  <tbody>
                    {listing.data.content.map((customer) => (
                      <tr key={customer.id}>
                        <td>
                          <strong>{customer.name}</strong>
                        </td>
                        <td className={customer.contactEmail ? "" : "muted"}>
                          {fallback(customer.contactEmail)}
                        </td>
                        <td className={customer.contactPhone ? "" : "muted"}>
                          {fallback(customer.contactPhone)}
                        </td>
                        <td>
                          <Link className="code-link" to={`/sites?customerId=${customer.id}`}>
                            View sites
                          </Link>
                          {" · "}
                          <Link
                            className="code-link"
                            to={`/work-orders?customerId=${customer.id}`}
                          >
                            Jobs
                          </Link>
                        </td>
                        {canManage ? (
                          <td className="row-actions">
                            <button
                              type="button"
                              className="btn-link"
                              onClick={() => {
                                setInvalid({});
                                action.clearError();
                                setForm({
                                  id: customer.id,
                                  name: customer.name,
                                  contactEmail: customer.contactEmail ?? "",
                                  contactPhone: customer.contactPhone ?? "",
                                });
                              }}
                            >
                              Edit
                            </button>
                            <button
                              type="button"
                              className="btn-link danger"
                              onClick={() => {
                                action.clearError();
                                setConfirming(customer);
                              }}
                            >
                              Delete
                            </button>
                          </td>
                        ) : null}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <Pagination
              page={listing.data.page}
              totalPages={listing.data.totalPages}
              totalElements={listing.data.totalElements}
              first={listing.data.first}
              last={listing.data.last}
              onPage={setPage}
            />
          </>
        ) : null}
      </section>

      {form ? (
        <Modal
          title={form.id === null ? "Add customer" : `Edit ${form.name}`}
          onClose={() => setForm(null)}
          footer={
            <>
              <button type="button" className="btn-ghost" onClick={() => setForm(null)}>
                Cancel
              </button>
              <button
                type="button"
                className="btn-primary inline"
                disabled={action.busy || form.name.trim() === ""}
                onClick={() => void save()}
              >
                {action.busy ? "Saving…" : "Save"}
              </button>
            </>
          }
        >
          <Field label="Name" htmlFor="cust-name" error={invalid.name}>
            <input
              id="cust-name"
              type="text"
              value={form.name}
              onChange={(event) => setForm({ ...form, name: event.target.value })}
              required
            />
          </Field>
          <Field
            label="Contact email"
            htmlFor="cust-email"
            error={invalid.contactEmail}
            hint="Optional, but it is where SLA reports would go."
          >
            <input
              id="cust-email"
              type="email"
              value={form.contactEmail}
              onChange={(event) => setForm({ ...form, contactEmail: event.target.value })}
            />
          </Field>
          <Field label="Contact phone" htmlFor="cust-phone" error={invalid.contactPhone}>
            <input
              id="cust-phone"
              type="tel"
              value={form.contactPhone}
              onChange={(event) => setForm({ ...form, contactPhone: event.target.value })}
            />
          </Field>
          {action.error ? <ErrorBanner message={action.error} /> : null}
        </Modal>
      ) : null}

      {confirming ? (
        <Modal
          title={`Delete ${confirming.name}?`}
          onClose={() => setConfirming(null)}
          footer={
            <>
              <button type="button" className="btn-ghost" onClick={() => setConfirming(null)}>
                Keep it
              </button>
              <button
                type="button"
                className="btn-danger"
                disabled={action.busy}
                onClick={() => void remove(confirming)}
              >
                {action.busy ? "Working…" : "Delete"}
              </button>
            </>
          }
        >
          <p>
            This is only possible while the customer has no sites and no work orders. If anything
            references them, the request will be refused so the history stays intact.
          </p>
          {action.error ? <ErrorBanner message={action.error} /> : null}
        </Modal>
      ) : null}
    </>
  );
}
