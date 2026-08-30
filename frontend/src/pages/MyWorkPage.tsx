/**
 * The technician's screen.
 *
 * Grouped by what to do next rather than sorted into one long table: work in
 * progress first, then anything paused waiting on a part, then the queue. The
 * endpoint is scoped to the caller on the server, so there is no "whose jobs are
 * these" question to get wrong here.
 *
 * There are no action buttons on this list. Starting or completing a job needs the
 * server's own view of what is permitted from the job's current state — and
 * completing needs work logged against it first — so those live on the job page
 * where the rules and the note field are.
 */

import { Link } from "react-router-dom";
import { ErrorBanner, Loading, PageHeader, Stat } from "../components/ui";
import { WorkOrderTable } from "../components/WorkOrderTable";
import { workOrderApi } from "../endpoints";
import { formatRelative, isOverdue } from "../format";
import { useApi } from "../hooks";
import type { WorkOrderStatus, WorkOrderSummary } from "../types";

const GROUPS: { status: WorkOrderStatus; title: string; blurb: string }[] = [
  {
    status: "IN_PROGRESS",
    title: "On the tools",
    blurb: "Log parts and time as you go, then complete the job.",
  },
  {
    status: "ON_HOLD",
    title: "Paused",
    blurb: "Waiting on something. Time can still be logged against these.",
  },
  {
    status: "ASSIGNED",
    title: "Next up",
    blurb: "Assigned to you and not started.",
  },
  {
    status: "NEW",
    title: "Just raised",
    blurb: "Raised against you but not yet moved into the queue.",
  },
];

export function MyWorkPage() {
  const mine = useApi(() => workOrderApi.myWork(), []);

  if (mine.loading && !mine.data) {
    return <Loading label="Loading your jobs…" />;
  }
  if (mine.error && !mine.data) {
    return <ErrorBanner message={mine.error} />;
  }

  const jobs: WorkOrderSummary[] = mine.data ?? [];
  const byStatus = (status: WorkOrderStatus) => jobs.filter((job) => job.status === status);
  const breaching = jobs.filter((job) => job.slaStatus !== "ON_TRACK");
  const overdue = jobs.filter((job) => isOverdue(job.slaDueAt));

  // The next deadline is what a technician actually plans their day around.
  const nextDue = jobs
    .filter((job) => job.slaDueAt !== null)
    .sort((a, b) => String(a.slaDueAt).localeCompare(String(b.slaDueAt)))[0];

  return (
    <>
      <PageHeader
        title="My work"
        subtitle="Your open jobs, most urgent first."
        actions={
          <Link className="btn-link" to="/board">
            See the board
          </Link>
        }
      />

      <section className="stat-row">
        <Stat label="Open jobs" value={jobs.length} />
        <Stat
          label="Needing attention"
          value={breaching.length}
          tone={breaching.length > 0 ? "warn" : "good"}
          hint="At risk or breached"
        />
        <Stat
          label="Past deadline"
          value={overdue.length}
          tone={overdue.length > 0 ? "danger" : "good"}
        />
        <Stat
          label="Next deadline"
          value={nextDue ? formatRelative(nextDue.slaDueAt) : "—"}
          hint={nextDue ? `${nextDue.code} · ${nextDue.siteName}` : "Nothing scheduled"}
        />
      </section>

      {jobs.length === 0 ? (
        <section className="card">
          <h2>Nothing assigned</h2>
          <p className="muted">
            You have no open jobs. New work will appear here as soon as a dispatcher assigns it to
            you.
          </p>
        </section>
      ) : (
        GROUPS.map((group) => {
          const rows = byStatus(group.status);
          if (rows.length === 0) {
            return null;
          }
          return (
            <section className="card" key={group.status}>
              <div className="card-head">
                <h2>
                  {group.title} <span className="count-badge">{rows.length}</span>
                </h2>
              </div>
              <p className="muted">{group.blurb}</p>
              <WorkOrderTable
                items={rows}
                columns={{ assignee: false, cost: false }}
                emptyTitle="Nothing here."
              />
            </section>
          );
        })
      )}
    </>
  );
}
