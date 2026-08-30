/**
 * Parts inventory.
 *
 * Stock here is the number the field view draws down, so the figures matter: when
 * a technician logs two of a part against a job, this count drops by two inside
 * the same transaction, under a row lock, and can never go below zero. That is why
 * editing stock is manager-only — an adjustment here is a stock take, not a
 * correction of somebody's job sheet.
 */

import { useState } from "react";
import { fieldErrors } from "../api";
import { useAuth } from "../auth";
import {
  ErrorBanner,
  Field,
  Loading,
  Modal,
  PageHeader,
  Pagination,
  Stat,
} from "../components/ui";
import { partApi } from "../endpoints";
import { formatMoney } from "../format";
import { useApi, useAction, useDebounced } from "../hooks";
import { LOW_STOCK_THRESHOLD, type PartView } from "../types";

interface FormState {
  id: number | null;
  sku: string;
  name: string;
  unitCost: string;
  stockQuantity: string;
}

const BLANK: FormState = { id: null, sku: "", name: "", unitCost: "0.00", stockQuantity: "0" };

const SORT_OPTIONS = [
  { value: "sku,asc", label: "SKU" },
  { value: "name,asc", label: "Name" },
  { value: "stockQuantity,asc", label: "Lowest stock" },
  { value: "unitCost,desc", label: "Most expensive" },
];

export function PartsPage() {
  const { hasRole } = useAuth();
  const canManage = hasRole("MANAGER");

  const [searchInput, setSearchInput] = useState("");
  const search = useDebounced(searchInput);
  const [lowOnly, setLowOnly] = useState(false);
  const [sort, setSort] = useState("sku,asc");
  const [page, setPage] = useState(0);
  const [form, setForm] = useState<FormState | null>(null);
  const [invalid, setInvalid] = useState<Record<string, string>>({});
  const [confirming, setConfirming] = useState<PartView | null>(null);

  const action = useAction();

  const listing = useApi(
    () =>
      partApi.list({
        search: search || null,
        lowStockOnly: lowOnly ? true : null,
        page,
        size: 20,
        sort,
      }),
    [search, lowOnly, sort, page]
  );

  // A second, unfiltered read so the "running low" figure does not change meaning
  // when a filter is applied to the table below it.
  const lowStock = useApi(
    () => partApi.list({ lowStockOnly: true, size: 1 }).then((paged) => paged.totalElements),
    []
  );

  const save = () =>
    action.run(async () => {
      if (!form) {
        return;
      }
      setInvalid({});
      const body = {
        sku: form.sku.trim().toUpperCase(),
        name: form.name.trim(),
        unitCost: Number(form.unitCost),
        stockQuantity: Number(form.stockQuantity),
      };
      try {
        if (form.id === null) {
          await partApi.create(body);
        } else {
          await partApi.update(form.id, body);
        }
        setForm(null);
        listing.reload();
        lowStock.reload();
      } catch (err: unknown) {
        setInvalid(fieldErrors(err));
        throw err;
      }
    });

  const remove = (part: PartView) =>
    action.run(async () => {
      await partApi.remove(part.id);
      setConfirming(null);
      listing.reload();
      lowStock.reload();
    });

  return (
    <>
      <PageHeader
        title="Inventory"
        subtitle="What is on the vans and in the store, and what it costs."
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
              Add part
            </button>
          ) : null
        }
      />

      {action.error ? <ErrorBanner message={action.error} onDismiss={action.clearError} /> : null}
      {listing.error ? <ErrorBanner message={listing.error} /> : null}

      <section className="stat-row">
        <Stat label="Catalogue size" value={listing.data?.totalElements ?? "—"} />
        <Stat
          label="Running low"
          value={lowStock.data ?? "—"}
          tone={(lowStock.data ?? 0) > 0 ? "warn" : "good"}
          hint={`At or below ${LOW_STOCK_THRESHOLD} units`}
        />
      </section>

      <section className="card">
        <div className="filter-row">
          <Field label="Search" htmlFor="part-search" hint="SKU or name">
            <input
              id="part-search"
              type="search"
              value={searchInput}
              onChange={(event) => {
                setSearchInput(event.target.value);
                setPage(0);
              }}
              placeholder="e.g. PMP or belt"
            />
          </Field>
          <Field label="Sort" htmlFor="part-sort">
            <select
              id="part-sort"
              value={sort}
              onChange={(event) => {
                setSort(event.target.value);
                setPage(0);
              }}
            >
              {SORT_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </Field>
        </div>

        <div className="chip-row">
          <button
            type="button"
            className={lowOnly ? "chip on" : "chip"}
            onClick={() => {
              setLowOnly(!lowOnly);
              setPage(0);
            }}
            aria-pressed={lowOnly}
          >
            Low stock only
          </button>
        </div>

        {listing.loading && !listing.data ? (
          <Loading />
        ) : listing.data ? (
          <>
            {listing.data.content.length === 0 ? (
              <p className="muted">Nothing in the catalogue matches that.</p>
            ) : (
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>SKU</th>
                      <th>Name</th>
                      <th className="numeric">Unit cost</th>
                      <th className="numeric">In stock</th>
                      {canManage ? <th /> : null}
                    </tr>
                  </thead>
                  <tbody>
                    {listing.data.content.map((part) => (
                      <tr key={part.id}>
                        <td>
                          <strong>{part.sku}</strong>
                        </td>
                        <td>{part.name}</td>
                        <td className="numeric">{formatMoney(part.unitCost)}</td>
                        <td
                          className={
                            part.stockQuantity === 0
                              ? "numeric overdue"
                              : part.stockQuantity <= LOW_STOCK_THRESHOLD
                                ? "numeric low"
                                : "numeric"
                          }
                        >
                          {part.stockQuantity}
                          {part.stockQuantity === 0 ? " — out" : ""}
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
                                  id: part.id,
                                  sku: part.sku,
                                  name: part.name,
                                  unitCost: String(part.unitCost),
                                  stockQuantity: String(part.stockQuantity),
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
                                setConfirming(part);
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
          title={form.id === null ? "Add part" : `Edit ${form.sku}`}
          onClose={() => setForm(null)}
          footer={
            <>
              <button type="button" className="btn-ghost" onClick={() => setForm(null)}>
                Cancel
              </button>
              <button
                type="button"
                className="btn-primary inline"
                disabled={action.busy || form.sku.trim() === "" || form.name.trim() === ""}
                onClick={() => void save()}
              >
                {action.busy ? "Saving…" : "Save"}
              </button>
            </>
          }
        >
          <Field
            label="SKU"
            htmlFor="part-sku"
            error={invalid.sku}
            hint="Unique. Stored uppercase."
          >
            <input
              id="part-sku"
              type="text"
              value={form.sku}
              onChange={(event) => setForm({ ...form, sku: event.target.value })}
              placeholder="e.g. PMP-100"
              required
            />
          </Field>
          <Field label="Name" htmlFor="part-name" error={invalid.name}>
            <input
              id="part-name"
              type="text"
              value={form.name}
              onChange={(event) => setForm({ ...form, name: event.target.value })}
              required
            />
          </Field>
          <div className="form-row">
            <Field
              label="Unit cost"
              htmlFor="part-cost"
              error={invalid.unitCost}
              hint="Jobs record the cost at the time they used it, so changing this does not rewrite history."
            >
              <input
                id="part-cost"
                type="number"
                min={0}
                step="0.01"
                value={form.unitCost}
                onChange={(event) => setForm({ ...form, unitCost: event.target.value })}
              />
            </Field>
            <Field
              label="Stock quantity"
              htmlFor="part-stock"
              error={invalid.stockQuantity}
              hint="A stock take. Jobs adjust this themselves as parts are used."
            >
              <input
                id="part-stock"
                type="number"
                min={0}
                value={form.stockQuantity}
                onChange={(event) => setForm({ ...form, stockQuantity: event.target.value })}
              />
            </Field>
          </div>
          {action.error ? <ErrorBanner message={action.error} /> : null}
        </Modal>
      ) : null}

      {confirming ? (
        <Modal
          title={`Delete ${confirming.sku}?`}
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
            A part that has been used on a job cannot be deleted — the job's cost record points at
            it. Discontinued items are better left in the catalogue at zero stock.
          </p>
          {action.error ? <ErrorBanner message={action.error} /> : null}
        </Modal>
      ) : null}
    </>
  );
}
