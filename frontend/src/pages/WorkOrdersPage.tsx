/**
 * The work-order list: the dispatcher's main screen.
 *
 * Filter state lives in the URL rather than in component state. That is worth the
 * small amount of plumbing because it makes a filtered view a link — "every
 * breached urgent job" can be pasted into a chat message, bookmarked, or reached
 * with the back button after opening a job and returning.
 *
 * Nothing here restricts what the caller can see. The server intersects every
 * query with the caller's own scope, so a technician who removes the assignee
 * filter still gets only their own jobs back, and a portal user still gets only
 * their own sites.
 */

import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth";
import { WorkOrderTable } from "../components/WorkOrderTable";
import {
  EnumSelect,
  ErrorBanner,
  Field,
  Loading,
  PageHeader,
  Pagination,
} from "../components/ui";
import { customerApi, userApi, workOrderApi } from "../endpoints";
import { priorityLabel, slaLabel, statusLabel } from "../format";
import { useApi, useDebounced } from "../hooks";
import {
  PRIORITIES,
  SLA_STATUSES,
  WORK_ORDER_STATUSES,
  type Priority,
  type SlaStatus,
  type WorkOrderStatus,
} from "../types";

/** Sort keys the server whitelists. Anything else comes back as a clean 400. */
const SORT_OPTIONS: { value: string; label: string }[] = [
  { value: "createdAt,desc", label: "Newest first" },
  { value: "createdAt,asc", label: "Oldest first" },
  { value: "slaDueAt,asc", label: "Deadline soonest" },
  { value: "priority,desc", label: "Priority highest" },
  { value: "status,asc", label: "Status" },
  { value: "code,asc", label: "Job number" },
  { value: "updatedAt,desc", label: "Recently updated" },
];

type PatchValue = string | string[] | null;

export function WorkOrdersPage() {
  const { user, hasRole } = useAuth();
  const [params, setParams] = useSearchParams();

  const canDispatch = hasRole("MANAGER", "DISPATCHER");
  const canRaise = hasRole("MANAGER", "DISPATCHER", "CUSTOMER");

  // ------------------------------------------------------------ URL as state
  const statuses = params.getAll("statuses") as WorkOrderStatus[];
  const priority = (params.get("priority") ?? "") as Priority | "";
  const slaStatus = (params.get("slaStatus") ?? "") as SlaStatus | "";
  const assigneeId = params.get("assigneeId") ?? "";
  const customerId = params.get("customerId") ?? "";
  const unassigned = params.get("unassigned") === "true";
  const openOnly = params.get("openOnly") === "true";
  const searchTerm = params.get("search") ?? "";
  const sort = params.get("sort") ?? "createdAt,desc";
  const page = Math.max(0, Number(params.get("page") ?? "0") || 0);

  function update(patch: Record<string, PatchValue>) {
    const next = new URLSearchParams(params);
    Object.entries(patch).forEach(([key, value]) => {
      next.delete(key);
      if (Array.isArray(value)) {
        value.forEach((item) => next.append(key, item));
      } else if (value !== null && value !== "") {
        next.set(key, value);
      }
    });
    setParams(next, { replace: true });
  }

  /** Any filter change resets to the first page; page 4 of a new result set is meaningless. */
  function filter(patch: Record<string, PatchValue>) {
    update({ ...patch, page: null });
  }

  // The search box is typed into, so it keeps local state and pushes to the URL
  // only once the user pauses.
  const [searchInput, setSearchInput] = useState(searchTerm);
  const debouncedSearch = useDebounced(searchInput);

  useEffect(() => {
    if (debouncedSearch !== searchTerm) {
      filter({ search: debouncedSearch });
    }
    // Deliberately keyed on the debounced value alone: including `filter` or
    // `searchTerm` would re-fire this on every URL change and fight the user.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedSearch]);

  // ----------------------------------------------------------------- loading
  const statusKey = statuses.join(",");

  const listing = useApi(
    () =>
      workOrderApi.list({
        statuses: statuses.length > 0 ? statuses : undefined,
        priority: priority || null,
        slaStatus: slaStatus || null,
        assigneeId: assigneeId ? Number(assigneeId) : null,
        customerId: customerId ? Number(customerId) : null,
        unassigned: unassigned ? true : null,
        openOnly: openOnly ? true : null,
        search: searchTerm || null,
        page,
        size: 20,
        sort,
      }),
    [
      statusKey,
      priority,
      slaStatus,
      assigneeId,
      customerId,
      unassigned,
      openOnly,
      searchTerm,
      page,
      sort,
    ]
  );

  // The pickers are only fetched for roles that are allowed to read them; asking
  // as a technician would be a guaranteed 403.
  const technicians = useApi(
    () => (canDispatch ? userApi.technicians() : Promise.resolve([])),
    [canDispatch]
  );
  const customers = useApi(
    () =>
      canDispatch
        ? customerApi.list({ size: 100, sort: "name,asc" }).then((paged) => paged.content)
        : Promise.resolve([]),
    [canDispatch]
  );

  const toggleStatus = (status: WorkOrderStatus) => {
    const next = statuses.includes(status)
      ? statuses.filter((s) => s !== status)
      : [...statuses, status];
    filter({ statuses: next });
  };

  const clearAll = () => {
    setSearchInput("");
    setParams(new URLSearchParams(), { replace: true });
  };

  const activeFilters =
    statuses.length +
    (priority ? 1 : 0) +
    (slaStatus ? 1 : 0) +
    (assigneeId ? 1 : 0) +
    (customerId ? 1 : 0) +
    (unassigned ? 1 : 0) +
    (openOnly ? 1 : 0) +
    (searchTerm ? 1 : 0);

  return (
    <>
      <PageHeader
        title="Work orders"
        subtitle={
          user?.role === "TECHNICIAN"
            ? "Jobs assigned to you."
            : user?.role === "CUSTOMER"
              ? "Jobs raised for your sites."
              : "Everything on the books, filterable and shareable by URL."
        }
        actions={
          canRaise ? (
            <Link className="btn-primary inline" to="/work-orders/new">
              Raise job
            </Link>
          ) : null
        }
      />

      <section className="card filters">
        <div className="filter-row">
          <Field label="Search" htmlFor="wo-search" hint="Job number, title or description">
            <input
              id="wo-search"
              type="search"
              placeholder="e.g. WO-2026-0007 or chiller"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
            />
          </Field>

          <Field label="Priority" htmlFor="wo-priority">
            <EnumSelect
              id="wo-priority"
              value={priority}
              options={PRIORITIES}
              label={priorityLabel}
              anyLabel="Any priority"
              onChange={(value) => filter({ priority: value })}
            />
          </Field>

          <Field label="SLA" htmlFor="wo-sla">
            <EnumSelect
              id="wo-sla"
              value={slaStatus}
              options={SLA_STATUSES}
              label={slaLabel}
              anyLabel="Any SLA state"
              onChange={(value) => filter({ slaStatus: value })}
            />
          </Field>

          {canDispatch ? (
            <Field label="Engineer" htmlFor="wo-assignee">
              <select
                id="wo-assignee"
                value={assigneeId}
                onChange={(event) => filter({ assigneeId: event.target.value })}
              >
                <option value="">Any engineer</option>
                {(technicians.data ?? []).map((tech) => (
                  <option key={tech.id} value={String(tech.id)}>
                    {tech.fullName}
                  </option>
                ))}
              </select>
            </Field>
          ) : null}

          {canDispatch ? (
            <Field label="Customer" htmlFor="wo-customer">
              <select
                id="wo-customer"
                value={customerId}
                onChange={(event) => filter({ customerId: event.target.value })}
              >
                <option value="">Any customer</option>
                {(customers.data ?? []).map((customer) => (
                  <option key={customer.id} value={String(customer.id)}>
                    {customer.name}
                  </option>
                ))}
              </select>
            </Field>
          ) : null}

          <Field label="Sort" htmlFor="wo-sort">
            <select
              id="wo-sort"
              value={sort}
              onChange={(event) => filter({ sort: event.target.value })}
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
          {WORK_ORDER_STATUSES.map((status) => (
            <button
              key={status}
              type="button"
              className={statuses.includes(status) ? "chip on" : "chip"}
              onClick={() => toggleStatus(status)}
              aria-pressed={statuses.includes(status)}
            >
              {statusLabel(status)}
            </button>
          ))}

          <span className="chip-divider" aria-hidden="true" />

          <button
            type="button"
            className={openOnly ? "chip on" : "chip"}
            onClick={() => filter({ openOnly: openOnly ? null : "true" })}
            aria-pressed={openOnly}
          >
            Open only
          </button>
          {canDispatch ? (
            <button
              type="button"
              className={unassigned ? "chip on" : "chip"}
              onClick={() => filter({ unassigned: unassigned ? null : "true" })}
              aria-pressed={unassigned}
            >
              Unassigned
            </button>
          ) : null}

          {activeFilters > 0 ? (
            <button type="button" className="btn-link" onClick={clearAll}>
              Clear {activeFilters} {activeFilters === 1 ? "filter" : "filters"}
            </button>
          ) : null}
        </div>
      </section>

      {listing.error ? <ErrorBanner message={listing.error} /> : null}

      {listing.loading && !listing.data ? (
        <Loading label="Loading work orders…" />
      ) : listing.data ? (
        <section className="card">
          <WorkOrderTable
            items={listing.data.content}
            columns={{
              assignee: user?.role !== "TECHNICIAN",
              customer: user?.role !== "CUSTOMER",
            }}
            emptyTitle={
              activeFilters > 0 ? "No jobs match these filters." : "No work orders yet."
            }
            emptyHint={
              activeFilters > 0
                ? "Clear a filter or widen the search."
                : canRaise
                  ? "Raise the first one to get started."
                  : undefined
            }
          />
          <Pagination
            page={listing.data.page}
            totalPages={listing.data.totalPages}
            totalElements={listing.data.totalElements}
            first={listing.data.first}
            last={listing.data.last}
            onPage={(next) => update({ page: String(next) })}
          />
        </section>
      ) : null}
    </>
  );
}
