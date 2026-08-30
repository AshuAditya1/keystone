/**
 * The Kanban board.
 *
 * Read-only on purpose. Drag-and-drop looks impressive in a demo and then lies:
 * the lifecycle has rules — a job cannot go from NEW to COMPLETED, a pause needs
 * a written reason, completing needs logged work — and a drag gesture has nowhere
 * to put the reason and no way to explain a refusal. Moving a job happens on its
 * detail page, where the server's own list of permitted next steps is shown as
 * buttons and a note can be attached.
 *
 * Each column header shows the true total, which may be larger than the number of
 * cards: the server caps the cards per column so one runaway status cannot make
 * the page enormous.
 */

import { Link } from "react-router-dom";
import { useState } from "react";
import { useAuth } from "../auth";
import {
  EnumSelect,
  ErrorBanner,
  Field,
  Loading,
  PageHeader,
  PriorityPill,
  SlaPill,
} from "../components/ui";
import { customerApi, userApi, workOrderApi } from "../endpoints";
import { formatRelative, isOverdue, priorityLabel, slug, statusLabel } from "../format";
import { useApi, useDebounced } from "../hooks";
import { PRIORITIES, type Priority } from "../types";

export function BoardPage() {
  const { hasRole } = useAuth();
  const canDispatch = hasRole("MANAGER", "DISPATCHER");

  const [priority, setPriority] = useState<Priority | "">("");
  const [assigneeId, setAssigneeId] = useState("");
  const [customerId, setCustomerId] = useState("");
  const [unassigned, setUnassigned] = useState(false);
  const [searchInput, setSearchInput] = useState("");
  const search = useDebounced(searchInput);

  const board = useApi(
    () =>
      workOrderApi.board({
        priority: priority || null,
        assigneeId: assigneeId ? Number(assigneeId) : null,
        customerId: customerId ? Number(customerId) : null,
        unassigned: unassigned ? true : null,
        search: search || null,
      }),
    [priority, assigneeId, customerId, unassigned, search]
  );

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

  const columns = board.data?.columns ?? [];
  const totalShown = columns.reduce((sum, column) => sum + column.count, 0);

  return (
    <>
      <PageHeader
        title="Board"
        subtitle="One column per lifecycle state, most urgent first. Open a job to move it."
      />

      <section className="card filters">
        <div className="filter-row">
          <Field label="Search" htmlFor="board-search">
            <input
              id="board-search"
              type="search"
              placeholder="Job number or title"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
            />
          </Field>

          <Field label="Priority" htmlFor="board-priority">
            <EnumSelect
              id="board-priority"
              value={priority}
              options={PRIORITIES}
              label={priorityLabel}
              anyLabel="Any priority"
              onChange={setPriority}
            />
          </Field>

          {canDispatch ? (
            <Field label="Engineer" htmlFor="board-assignee">
              <select
                id="board-assignee"
                value={assigneeId}
                onChange={(event) => setAssigneeId(event.target.value)}
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
            <Field label="Customer" htmlFor="board-customer">
              <select
                id="board-customer"
                value={customerId}
                onChange={(event) => setCustomerId(event.target.value)}
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
        </div>

        {canDispatch ? (
          <div className="chip-row">
            <button
              type="button"
              className={unassigned ? "chip on" : "chip"}
              onClick={() => setUnassigned(!unassigned)}
              aria-pressed={unassigned}
            >
              Unassigned only
            </button>
            <span className="muted">
              {totalShown} {totalShown === 1 ? "job" : "jobs"} on the board
            </span>
          </div>
        ) : null}
      </section>

      {board.error ? <ErrorBanner message={board.error} /> : null}

      {board.loading && !board.data ? (
        <Loading label="Loading the board…" />
      ) : (
        <div className="board">
          {columns.map((column) => (
            <section key={column.status} className={`board-column status-col-${slug(column.status)}`}>
              <header className="board-column-head">
                <span className="board-column-title">{statusLabel(column.status)}</span>
                <span className="board-count">{column.count}</span>
              </header>

              <div className="board-cards">
                {column.items.length === 0 ? (
                  <p className="board-empty muted">Empty</p>
                ) : (
                  column.items.map((wo) => (
                    <Link key={wo.id} className="board-card" to={`/work-orders/${wo.id}`}>
                      <div className="board-card-top">
                        <span className="code-link">{wo.code}</span>
                        <PriorityPill priority={wo.priority} />
                      </div>
                      <p className="board-card-title">{wo.title}</p>
                      <p className="board-card-meta muted">
                        {wo.customerName} · {wo.siteName}
                      </p>
                      <div className="board-card-foot">
                        <span className={wo.assigneeName ? "" : "muted"}>
                          {wo.assigneeName ?? "Unassigned"}
                        </span>
                        <SlaPill sla={wo.slaStatus} dueAt={wo.slaDueAt} />
                      </div>
                      {wo.slaDueAt ? (
                        <p
                          className={
                            isOverdue(wo.slaDueAt) && wo.completedAt === null
                              ? "board-card-due overdue"
                              : "board-card-due muted"
                          }
                        >
                          Due {formatRelative(wo.slaDueAt)}
                        </p>
                      ) : null}
                    </Link>
                  ))
                )}

                {column.count > column.items.length ? (
                  <Link className="board-more" to={`/work-orders?statuses=${column.status}`}>
                    +{column.count - column.items.length} more →
                  </Link>
                ) : null}
              </div>
            </section>
          ))}
        </div>
      )}
    </>
  );
}
