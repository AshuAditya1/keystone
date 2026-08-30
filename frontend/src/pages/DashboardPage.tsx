/**
 * The dispatcher and manager dashboard.
 *
 * Every tile is a link into a filtered list rather than a dead number, because the
 * only useful response to "3 breached" is to look at which three. The figures come
 * from one endpoint that counts in the database, not from paging the list and
 * counting in the browser.
 *
 * The status bars are drawn with plain divs rather than a charting library: four
 * bars and a percentage do not justify shipping a chart bundle, and this way the
 * page has no runtime dependency beyond React.
 */

import { Link } from "react-router-dom";
import { ErrorBanner, Loading, PageHeader, Stat } from "../components/ui";
import { WorkOrderTable } from "../components/WorkOrderTable";
import { dashboardApi, workOrderApi } from "../endpoints";
import {
  formatDateTime,
  formatHours,
  priorityLabel,
  slug,
  statusLabel,
} from "../format";
import { useApi } from "../hooks";
import { PRIORITIES, WORK_ORDER_STATUSES } from "../types";

export function DashboardPage() {
  const summary = useApi(() => dashboardApi.summary(), []);

  // The one list worth putting on the front page: what is at risk of being late.
  const urgent = useApi(
    () =>
      workOrderApi.list({
        openOnly: true,
        sort: "slaDueAt,asc",
        size: 8,
      }),
    []
  );

  if (summary.loading && !summary.data) {
    return <Loading label="Building the dashboard…" />;
  }
  if (summary.error && !summary.data) {
    return <ErrorBanner message={summary.error} />;
  }
  if (!summary.data) {
    return null;
  }

  const data = summary.data;
  const statusMax = Math.max(1, ...WORK_ORDER_STATUSES.map((s) => data.byStatus[s] ?? 0));
  const priorityMax = Math.max(1, ...PRIORITIES.map((p) => data.byPriority[p] ?? 0));

  return (
    <>
      <PageHeader
        title="Operations"
        subtitle="Where the work stands right now. Every figure links to the jobs behind it."
      />

      <section className="stat-row">
        <Link to="/work-orders?openOnly=true" className="stat-link">
          <Stat label="Open jobs" value={data.open} hint={`${data.total} in total`} />
        </Link>
        <Link to="/work-orders?unassigned=true&openOnly=true" className="stat-link">
          <Stat
            label="Waiting for an engineer"
            value={data.unassigned}
            tone={data.unassigned > 0 ? "warn" : "default"}
            hint="Unassigned and still open"
          />
        </Link>
        <Link to="/work-orders?slaStatus=BREACHED" className="stat-link">
          <Stat
            label="SLA breached"
            value={data.slaBreached}
            tone={data.slaBreached > 0 ? "danger" : "good"}
            hint="Past the deadline"
          />
        </Link>
        <Link to="/work-orders?slaStatus=AT_RISK&openOnly=true" className="stat-link">
          <Stat
            label="SLA at risk"
            value={data.slaAtRisk}
            tone={data.slaAtRisk > 0 ? "warn" : "good"}
            hint="Three quarters through the window"
          />
        </Link>
        <Stat
          label="Completed this week"
          value={data.completedLast7Days}
          tone="good"
          hint="Last 7 days"
        />
        <Stat
          label="Average time to complete"
          value={formatHours(data.avgCompletionHours)}
          hint={data.avgCompletionHours === null ? "Nothing completed yet" : "Raised to completed"}
        />
      </section>

      <div className="two-col">
        <section className="card">
          <h2>By status</h2>
          <ul className="bar-list">
            {WORK_ORDER_STATUSES.map((status) => {
              const count = data.byStatus[status] ?? 0;
              return (
                <li key={status}>
                  <Link className="bar-label" to={`/work-orders?statuses=${status}`}>
                    {statusLabel(status)}
                  </Link>
                  <span className="bar-track">
                    <span
                      className={`bar-fill status-fill-${slug(status)}`}
                      style={{ width: `${(count / statusMax) * 100}%` }}
                    />
                  </span>
                  <span className="bar-value">{count}</span>
                </li>
              );
            })}
          </ul>
        </section>

        <section className="card">
          <h2>By priority</h2>
          <ul className="bar-list">
            {PRIORITIES.map((priority) => {
              const count = data.byPriority[priority] ?? 0;
              return (
                <li key={priority}>
                  <Link className="bar-label" to={`/work-orders?priority=${priority}`}>
                    {priorityLabel(priority)}
                  </Link>
                  <span className="bar-track">
                    <span
                      className={`bar-fill priority-fill-${slug(priority)}`}
                      style={{ width: `${(count / priorityMax) * 100}%` }}
                    />
                  </span>
                  <span className="bar-value">{count}</span>
                </li>
              );
            })}
          </ul>
        </section>
      </div>

      <section className="card">
        <div className="card-head">
          <h2>Closest to their deadline</h2>
          <Link className="btn-link" to="/work-orders?openOnly=true&sort=slaDueAt%2Casc">
            See all open jobs
          </Link>
        </div>
        {urgent.error ? <ErrorBanner message={urgent.error} /> : null}
        {urgent.loading && !urgent.data ? (
          <Loading />
        ) : (
          <WorkOrderTable
            items={urgent.data?.content ?? []}
            columns={{ cost: false, labour: false }}
            emptyTitle="Nothing open."
            emptyHint="Every job on the books is finished or cancelled."
          />
        )}
      </section>

      <div className="two-col">
        <section className="card">
          <h2>Engineer load</h2>
          {data.technicianLoad.length === 0 ? (
            <p className="muted">No technicians on the books yet.</p>
          ) : (
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Engineer</th>
                    <th className="numeric">Active</th>
                    <th className="numeric">Completed</th>
                  </tr>
                </thead>
                <tbody>
                  {data.technicianLoad.map((row) => (
                    <tr key={row.technicianId}>
                      <td>
                        <Link
                          className="code-link"
                          to={`/work-orders?assigneeId=${row.technicianId}&openOnly=true`}
                        >
                          {row.technicianName}
                        </Link>
                      </td>
                      <td className="numeric">{row.activeCount}</td>
                      <td className="numeric">{row.completedCount}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <section className="card">
          <h2>Recent activity</h2>
          {data.recentActivity.length === 0 ? (
            <p className="muted">Nothing has moved yet.</p>
          ) : (
            <ol className="activity-list">
              {data.recentActivity.map((entry) => (
                <li key={entry.id}>
                  <div>
                    {entry.workOrderId ? (
                      <Link className="code-link" to={`/work-orders/${entry.workOrderId}`}>
                        {entry.workOrderCode}
                      </Link>
                    ) : null}{" "}
                    {entry.fromStatus ? (
                      <>
                        {statusLabel(entry.fromStatus)} <span aria-hidden="true">&rarr;</span>{" "}
                        <strong>{statusLabel(entry.toStatus)}</strong>
                      </>
                    ) : (
                      <>
                        raised as <strong>{statusLabel(entry.toStatus)}</strong>
                      </>
                    )}
                  </div>
                  <div className="muted">
                    {entry.changedByName} · {formatDateTime(entry.createdAt)}
                  </div>
                  {entry.note ? <div className="activity-note">{entry.note}</div> : null}
                </li>
              ))}
            </ol>
          )}
        </section>
      </div>
    </>
  );
}
