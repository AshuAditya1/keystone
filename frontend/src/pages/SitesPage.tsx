/**
 * Sites — the buildings work actually happens at.
 *
 * A site belongs to exactly one customer and that link is what scopes the whole
 * application: a portal user's visibility is defined by which sites their
 * organisation owns, so this list is already filtered for them by the server.
 *
 * Managers and dispatchers can create and edit; only a manager can delete, and
 * only while nothing references the site.
 */

import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
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
import { customerApi, siteApi } from "../endpoints";
import { fallback } from "../format";
import { useApi, useAction, useDebounced } from "../hooks";
import type { SiteView } from "../types";

interface FormState {
  id: number | null;
  customerId: string;
  name: string;
  address: string;
}

export function SitesPage() {
  const { hasRole } = useAuth();
  const canEdit = hasRole("MANAGER", "DISPATCHER");
  const canDelete = hasRole("MANAGER");

  const [params, setParams] = useSearchParams();
  const customerFilter = params.get("customerId") ?? "";

  const [searchInput, setSearchInput] = useState("");
  const search = useDebounced(searchInput);
  const [page, setPage] = useState(0);
  const [form, setForm] = useState<FormState | null>(null);
  const [invalid, setInvalid] = useState<Record<string, string>>({});
  const [confirming, setConfirming] = useState<SiteView | null>(null);

  const action = useAction();

  const listing = useApi(
    () =>
      siteApi.list({
        customerId: customerFilter ? Number(customerFilter) : null,
        search: search || null,
        page,
        size: 20,
        sort: "name,asc",
      }),
    [customerFilter, search, page]
  );

  // Needed both for the filter dropdown and for the create form's owner picker.
  const customers = useApi(
    () =>
      canEdit
        ? customerApi.list({ size: 100, sort: "name,asc" }).then((paged) => paged.content)
        : Promise.resolve([]),
    [canEdit]
  );

  const save = () =>
    action.run(async () => {
      if (!form) {
        return;
      }
      setInvalid({});
      const body = {
        customerId: Number(form.customerId),
        name: form.name.trim(),
        address: form.address.trim() || null,
      };
      try {
        if (form.id === null) {
          await siteApi.create(body);
        } else {
          await siteApi.update(form.id, body);
        }
        setForm(null);
        listing.reload();
      } catch (err: unknown) {
        setInvalid(fieldErrors(err));
        throw err;
      }
    });

  const remove = (site: SiteView) =>
    action.run(async () => {
      await siteApi.remove(site.id);
      setConfirming(null);
      listing.reload();
    });

  const setCustomerFilter = (value: string) => {
    const next = new URLSearchParams(params);
    if (value) {
      next.set("customerId", value);
    } else {
      next.delete("customerId");
    }
    setParams(next, { replace: true });
    setPage(0);
  };

  return (
    <>
      <PageHeader
        title="Sites"
        subtitle="Every location under contract, and who owns it."
        actions={
          canEdit ? (
            <button
              type="button"
              className="btn-primary inline"
              onClick={() => {
                setInvalid({});
                action.clearError();
                setForm({
                  id: null,
                  customerId: customerFilter,
                  name: "",
                  address: "",
                });
              }}
            >
              Add site
            </button>
          ) : null
        }
      />

      {action.error ? <ErrorBanner message={action.error} onDismiss={action.clearError} /> : null}
      {listing.error ? <ErrorBanner message={listing.error} /> : null}

      <section className="card">
        <div className="filter-row">
          <Field label="Search" htmlFor="site-search" hint="Name or address">
            <input
              id="site-search"
              type="search"
              value={searchInput}
              onChange={(event) => {
                setSearchInput(event.target.value);
                setPage(0);
              }}
              placeholder="e.g. Warehouse"
            />
          </Field>

          {canEdit ? (
            <Field label="Customer" htmlFor="site-customer-filter">
              <select
                id="site-customer-filter"
                value={customerFilter}
                onChange={(event) => setCustomerFilter(event.target.value)}
              >
                <option value="">All customers</option>
                {(customers.data ?? []).map((customer) => (
                  <option key={customer.id} value={String(customer.id)}>
                    {customer.name}
                  </option>
                ))}
              </select>
            </Field>
          ) : null}
        </div>

        {listing.loading && !listing.data ? (
          <Loading />
        ) : listing.data ? (
          <>
            {listing.data.content.length === 0 ? (
              <p className="muted">No sites match that search.</p>
            ) : (
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Site</th>
                      <th>Customer</th>
                      <th>Address</th>
                      <th>Jobs</th>
                      {canEdit ? <th /> : null}
                    </tr>
                  </thead>
                  <tbody>
                    {listing.data.content.map((site) => (
                      <tr key={site.id}>
                        <td>
                          <strong>{site.name}</strong>
                        </td>
                        <td>{site.customerName}</td>
                        <td className={site.address ? "" : "muted"}>{fallback(site.address)}</td>
                        <td>
                          <Link className="code-link" to={`/work-orders?siteId=${site.id}`}>
                            View jobs
                          </Link>
                        </td>
                        {canEdit ? (
                          <td className="row-actions">
                            <button
                              type="button"
                              className="btn-link"
                              onClick={() => {
                                setInvalid({});
                                action.clearError();
                                setForm({
                                  id: site.id,
                                  customerId: String(site.customerId),
                                  name: site.name,
                                  address: site.address ?? "",
                                });
                              }}
                            >
                              Edit
                            </button>
                            {canDelete ? (
                              <button
                                type="button"
                                className="btn-link danger"
                                onClick={() => {
                                  action.clearError();
                                  setConfirming(site);
                                }}
                              >
                                Delete
                              </button>
                            ) : null}
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
          title={form.id === null ? "Add site" : `Edit ${form.name}`}
          onClose={() => setForm(null)}
          footer={
            <>
              <button type="button" className="btn-ghost" onClick={() => setForm(null)}>
                Cancel
              </button>
              <button
                type="button"
                className="btn-primary inline"
                disabled={action.busy || form.name.trim() === "" || form.customerId === ""}
                onClick={() => void save()}
              >
                {action.busy ? "Saving…" : "Save"}
              </button>
            </>
          }
        >
          <Field
            label="Customer"
            htmlFor="site-owner"
            error={invalid.customerId}
            hint="Who the contract is with. This is what decides who can see jobs here."
          >
            <select
              id="site-owner"
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
          <Field label="Site name" htmlFor="site-name" error={invalid.name}>
            <input
              id="site-name"
              type="text"
              value={form.name}
              onChange={(event) => setForm({ ...form, name: event.target.value })}
              placeholder="e.g. Docklands Warehouse"
              required
            />
          </Field>
          <Field label="Address" htmlFor="site-address" error={invalid.address}>
            <textarea
              id="site-address"
              rows={2}
              value={form.address}
              onChange={(event) => setForm({ ...form, address: event.target.value })}
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
            A site with work orders against it cannot be removed — those jobs are the record of
            what was done there. The server will refuse and say so.
          </p>
          {action.error ? <ErrorBanner message={action.error} /> : null}
        </Modal>
      ) : null}
    </>
  );
}
