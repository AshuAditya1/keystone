/**
 * The work-order table.
 *
 * Shared by the list, the technician's queue and the customer's job history, so
 * that a job looks the same wherever it is seen. Columns can be switched off for
 * views where they are noise — a technician does not need an "Assignee" column
 * showing their own name seven times.
 */

import { Link } from "react-router-dom";
import {
  fallback,
  formatMinutes,
  formatMoney,
  formatRelative,
  isOverdue,
} from "../format";
import type { WorkOrderSummary } from "../types";
import { EmptyState, PriorityPill, SlaPill, StatusPill } from "./ui";

export interface Columns {
  assignee?: boolean;
  customer?: boolean;
  site?: boolean;
  cost?: boolean;
  labour?: boolean;
}

const ALL: Required<Columns> = {
  assignee: true,
  customer: true,
  site: true,
  cost: true,
  labour: true,
};

export function WorkOrderTable({
  items,
  columns,
  emptyTitle = "No work orders match these filters.",
  emptyHint,
}: {
  items: WorkOrderSummary[];
  columns?: Columns;
  emptyTitle?: string;
  emptyHint?: string;
}) {
  const show = { ...ALL, ...columns };

  if (items.length === 0) {
    return <EmptyState title={emptyTitle} hint={emptyHint} />;
  }

  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            <th>Job</th>
            <th>Priority</th>
            <th>Status</th>
            {show.customer ? <th>Customer</th> : null}
            {show.site ? <th>Site</th> : null}
            {show.assignee ? <th>Engineer</th> : null}
            <th>SLA</th>
            <th>Due</th>
            {show.labour ? <th className="numeric">Labour</th> : null}
            {show.cost ? <th className="numeric">Parts</th> : null}
          </tr>
        </thead>
        <tbody>
          {items.map((wo) => (
            <tr key={wo.id}>
              <td>
                <Link className="code-link" to={`/work-orders/${wo.id}`}>
                  {wo.code}
                </Link>
                <div className="row-title">{wo.title}</div>
              </td>
              <td>
                <PriorityPill priority={wo.priority} />
              </td>
              <td>
                <StatusPill status={wo.status} />
              </td>
              {show.customer ? <td>{wo.customerName}</td> : null}
              {show.site ? <td>{wo.siteName}</td> : null}
              {show.assignee ? (
                <td className={wo.assigneeName ? "" : "muted"}>
                  {wo.assigneeName ?? "Unassigned"}
                </td>
              ) : null}
              <td>
                <SlaPill sla={wo.slaStatus} dueAt={wo.slaDueAt} />
              </td>
              <td
                className={
                  // Only a live job's overdue deadline is worth shouting about;
                  // a finished job's verdict is already in the SLA pill.
                  isOverdue(wo.slaDueAt) && wo.completedAt === null ? "overdue" : "muted"
                }
              >
                {wo.slaDueAt ? formatRelative(wo.slaDueAt) : fallback(null)}
              </td>
              {show.labour ? <td className="numeric">{formatMinutes(wo.totalLaborMinutes)}</td> : null}
              {show.cost ? <td className="numeric">{formatMoney(wo.totalPartsCost)}</td> : null}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
