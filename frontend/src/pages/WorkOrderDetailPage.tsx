/**
 * The work-order detail page: everything about one job, and every action that can
 * be taken on it.
 *
 * The important design decision is that this page invents nothing. The buttons
 * come from `allowedTransitions`, which the server computed for this caller
 * against this job's current state; the panels come from `canEdit`, `canAssign`
 * and `canLogWork`. There is no table of rules in the frontend to drift out of
 * step with the backend's, and a refusal is displayed verbatim rather than being
 * pre-empted by a guess.
 *
 * Every mutation returns the whole updated job, so the page swaps the new value in
 * rather than re-fetching. That also means the buttons re-derive themselves from
 * the response: complete a job and the "Complete" button is simply gone, because
 * the server stopped offering it.
 */

import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useAuth } from "../auth";
import {
  ErrorBanner,
  Field,
  Loading,
  Modal,
  PriorityPill,
  SlaPill,
  StatusPill,
} from "../components/ui";
import { partApi, userApi, workOrderApi } from "../endpoints";
import {
  fallback,
  formatDateTime,
  formatMinutes,
  formatMoney,
  formatRelative,
  isOverdue,
  noteRequiredFor,
  statusLabel,
  transitionVerb,
} from "../format";
import { useApi, useAction } from "../hooks";
import type { WorkOrderDetail, WorkOrderStatus } from "../types";

export function WorkOrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const workOrderId = Number(id);
  const { hasRole } = useAuth();

  const detail = useApi<WorkOrderDetail>(
    () => workOrderApi.detail(workOrderId),
    [workOrderId]
  );

  const action = useAction();

  // Modal state. Only one of these is ever open at a time.
  const [transitionTo, setTransitionTo] = useState<WorkOrderStatus | null>(null);
  const [note, setNote] = useState("");
  const [assignOpen, setAssignOpen] = useState(false);
  const [assigneeChoice, setAssigneeChoice] = useState("");
  const [unassignOpen, setUnassignOpen] = useState(false);
  const [partOpen, setPartOpen] = useState(false);
  const [partChoice, setPartChoice] = useState("");
  const [quantity, setQuantity] = useState("1");
  const [timeOpen, setTimeOpen] = useState(false);
  const [minutes, setMinutes] = useState("30");

  const job = detail.data;

  // Pickers are fetched only when the panel that needs them is actually usable.
  const technicians = useApi(
    () => (job?.canAssign ? userApi.technicians() : Promise.resolve([])),
    [job?.canAssign]
  );
  const catalog = useApi(
    () => (job?.canLogWork ? partApi.catalog() : Promise.resolve([])),
    [job?.canLogWork]
  );

  if (!Number.isFinite(workOrderId)) {
    return <ErrorBanner message="That is not a valid work-order reference." />;
  }
  if (detail.loading && !job) {
    return <Loading label="Loading job…" />;
  }
  if (detail.error && !job) {
    return <ErrorBanner message={detail.error} />;
  }
  if (!job) {
    return null;
  }

  /** Run a mutation and adopt the returned job as the new truth. */
  const apply = (task: () => Promise<WorkOrderDetail>) =>
    action.run(async () => {
      detail.setData(await task());
      closeAll();
    });

  function closeAll() {
    setTransitionTo(null);
    setAssignOpen(false);
    setUnassignOpen(false);
    setPartOpen(false);
    setTimeOpen(false);
    setNote("");
  }

  const noteNeeded = transitionTo !== null && noteRequiredFor(transitionTo);
  const noteMissing = noteNeeded && note.trim().length === 0;

  return (
    <>
      <header className="page-header detail-header">
        <div>
          <div className="detail-code-row">
            <span className="detail-code">{job.code}</span>
            <StatusPill status={job.status} />
            <PriorityPill priority={job.priority} />
            <SlaPill sla={job.slaStatus} dueAt={job.slaDueAt} />
          </div>
          <h1>{job.title}</h1>
          <p className="muted">
            {job.customerName} · {job.siteName}
            {job.siteAddress ? ` · ${job.siteAddress}` : ""}
          </p>
        </div>
        <div className="page-actions">
          {job.canEdit ? (
            <Link className="btn-ghost" to={`/work-orders/${job.id}/edit`}>
              Edit details
            </Link>
          ) : null}
          <Link className="btn-link" to="/work-orders">
            Back to list
          </Link>
        </div>
      </header>

      {action.error ? <ErrorBanner message={action.error} onDismiss={action.clearError} /> : null}
      {detail.error ? <ErrorBanner message={detail.error} /> : null}

      {/* ------------------------------------------------------ lifecycle */}
      <section className="card">
        <h2>Lifecycle</h2>
        {job.allowedTransitions.length === 0 ? (
          <p className="muted">
            {hasRole("CUSTOMER")
              ? "Your team will move this job along; you will see each step in the history below."
              : `Nothing to do from ${statusLabel(job.status)} in your role.`}
          </p>
        ) : (
          <>
            <p className="muted">
              The only moves permitted from {statusLabel(job.status)} for your role:
            </p>
            <div className="button-row">
              {job.allowedTransitions.map((target) => (
                <button
                  key={target}
                  type="button"
                  className={target === "CANCELLED" ? "btn-danger" : "btn-primary inline"}
                  disabled={action.busy}
                  onClick={() => {
                    setNote("");
                    action.clearError();
                    setTransitionTo(target);
                  }}
                >
                  {transitionVerb(target)}
                </button>
              ))}
            </div>
          </>
        )}
      </section>

      {/* ----------------------------------------------------- assignment */}
      <section className="card">
        <div className="card-head">
          <h2>Assignment</h2>
          {job.canAssign ? (
            <div className="button-row">
              <button
                type="button"
                className="btn-ghost"
                disabled={action.busy}
                onClick={() => {
                  setAssigneeChoice(job.assigneeId ? String(job.assigneeId) : "");
                  setNote("");
                  action.clearError();
                  setAssignOpen(true);
                }}
              >
                {job.assigneeId ? "Reassign" : "Assign engineer"}
              </button>
              {job.assigneeId && job.status === "ASSIGNED" ? (
                <button
                  type="button"
                  className="btn-ghost"
                  disabled={action.busy}
                  onClick={() => {
                    setNote("");
                    action.clearError();
                    setUnassignOpen(true);
                  }}
                >
                  Return to queue
                </button>
              ) : null}
            </div>
          ) : null}
        </div>

        <dl className="detail-grid">
          <div>
            <dt>Engineer</dt>
            <dd className={job.assigneeName ? "" : "muted"}>
              {job.assigneeName ?? "Unassigned"}
            </dd>
          </div>
          <div>
            <dt>Deadline</dt>
            <dd className={isOverdue(job.slaDueAt) && !job.completedAt ? "overdue" : ""}>
              {formatDateTime(job.slaDueAt)}
              {job.slaDueAt ? <span className="muted"> · {formatRelative(job.slaDueAt)}</span> : null}
            </dd>
          </div>
          <div>
            <dt>Raised</dt>
            <dd>{formatDateTime(job.createdAt)}</dd>
          </div>
          <div>
            <dt>Completed</dt>
            <dd>{formatDateTime(job.completedAt)}</dd>
          </div>
          <div>
            <dt>Labour logged</dt>
            <dd>{formatMinutes(job.totalLaborMinutes)}</dd>
          </div>
          <div>
            <dt>Parts cost</dt>
            <dd>{formatMoney(job.totalPartsCost)}</dd>
          </div>
        </dl>

        {job.description ? (
          <>
            <h3>Reported fault</h3>
            <p className="description">{job.description}</p>
          </>
        ) : (
          <p className="muted">No description was given when this job was raised.</p>
        )}
      </section>

      {/* ------------------------------------------------------ work logs */}
      <section className="card">
        <div className="card-head">
          <h2>Parts and labour</h2>
          {job.canLogWork ? (
            <div className="button-row">
              <button
                type="button"
                className="btn-ghost"
                disabled={action.busy}
                onClick={() => {
                  setPartChoice("");
                  setQuantity("1");
                  action.clearError();
                  setPartOpen(true);
                }}
              >
                Log a part
              </button>
              <button
                type="button"
                className="btn-ghost"
                disabled={action.busy}
                onClick={() => {
                  setMinutes("30");
                  setNote("");
                  action.clearError();
                  setTimeOpen(true);
                }}
              >
                Log time
              </button>
            </div>
          ) : null}
        </div>

        {!job.canLogWork && job.totalLaborMinutes === 0 && job.parts.length === 0 ? (
          <p className="muted">
            Nothing logged yet. Parts and time can only be recorded by the assigned engineer, and
            only while the job is in progress or on hold.
          </p>
        ) : null}

        {job.parts.length > 0 ? (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Part</th>
                  <th className="numeric">Qty</th>
                  <th className="numeric">Unit cost</th>
                  <th className="numeric">Line</th>
                  <th>Logged by</th>
                  <th>When</th>
                  {job.canLogWork ? <th /> : null}
                </tr>
              </thead>
              <tbody>
                {job.parts.map((line) => (
                  <tr key={line.id}>
                    <td>
                      <strong>{line.partSku}</strong>
                      <div className="row-title">{line.partName}</div>
                    </td>
                    <td className="numeric">{line.quantity}</td>
                    {/* The cost at the time of use, not today's price — the ledger is history. */}
                    <td className="numeric">{formatMoney(line.unitCostAtUse)}</td>
                    <td className="numeric">{formatMoney(line.lineCost)}</td>
                    <td>{line.loggedByName}</td>
                    <td className="muted">{formatRelative(line.createdAt)}</td>
                    {job.canLogWork ? (
                      <td>
                        <button
                          type="button"
                          className="btn-link danger"
                          disabled={action.busy}
                          onClick={() =>
                            void apply(() => workOrderApi.removePart(job.id, line.id))
                          }
                        >
                          Remove
                        </button>
                      </td>
                    ) : null}
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={3}>Total parts</td>
                  <td className="numeric">
                    <strong>{formatMoney(job.totalPartsCost)}</strong>
                  </td>
                  <td colSpan={job.canLogWork ? 3 : 2} />
                </tr>
              </tfoot>
            </table>
          </div>
        ) : null}

        {job.timeLogs.length > 0 ? (
          <>
            <h3>Time</h3>
            <ul className="log-list">
              {job.timeLogs.map((log) => (
                <li key={log.id}>
                  <span className="log-amount">{formatMinutes(log.minutes)}</span>
                  <span>{log.technicianName}</span>
                  {log.note ? <span className="muted">— {log.note}</span> : null}
                  <span className="muted log-when">{formatRelative(log.createdAt)}</span>
                </li>
              ))}
            </ul>
            <p className="muted">
              Total labour: <strong>{formatMinutes(job.totalLaborMinutes)}</strong>
            </p>
          </>
        ) : null}
      </section>

      {/* -------------------------------------------------------- history */}
      <section className="card">
        <h2>History</h2>
        <p className="muted">
          Append-only. Every move is recorded with who made it and why; nothing here can be edited
          or deleted.
        </p>
        <ol className="timeline">
          {job.history.map((entry) => (
            <li key={entry.id}>
              <div className="timeline-dot" aria-hidden="true" />
              <div className="timeline-body">
                <p className="timeline-head">
                  {entry.fromStatus ? (
                    <>
                      {statusLabel(entry.fromStatus)} <span aria-hidden="true">&rarr;</span>{" "}
                      <strong>{statusLabel(entry.toStatus)}</strong>
                    </>
                  ) : (
                    <>
                      Raised as <strong>{statusLabel(entry.toStatus)}</strong>
                    </>
                  )}
                </p>
                {entry.note ? <p className="timeline-note">{entry.note}</p> : null}
                <p className="muted timeline-meta">
                  {entry.changedByName} · {formatDateTime(entry.createdAt)}
                </p>
              </div>
            </li>
          ))}
        </ol>
      </section>

      {/* --------------------------------------------------------- modals */}
      {transitionTo ? (
        <Modal
          title={transitionVerb(transitionTo)}
          onClose={closeAll}
          footer={
            <>
              <button type="button" className="btn-ghost" onClick={closeAll}>
                Cancel
              </button>
              <button
                type="button"
                className="btn-primary inline"
                disabled={action.busy || noteMissing}
                onClick={() =>
                  void apply(() =>
                    workOrderApi.transition(job.id, {
                      targetStatus: transitionTo,
                      note: note.trim() || null,
                    })
                  )
                }
              >
                {action.busy ? "Working…" : "Confirm"}
              </button>
            </>
          }
        >
          <p>
            Move {job.code} from <strong>{statusLabel(job.status)}</strong> to{" "}
            <strong>{statusLabel(transitionTo)}</strong>.
          </p>
          {transitionTo === "COMPLETED" ? (
            <p className="muted">
              A job cannot be completed with no work recorded against it. If the server refuses,
              log the time or parts spent first.
            </p>
          ) : null}
          <Field
            label={noteNeeded ? "Reason (required)" : "Note (optional)"}
            htmlFor="transition-note"
            hint={
              noteNeeded
                ? "This is kept on the permanent record, so say what actually happened."
                : "Anything worth knowing later."
            }
          >
            <textarea
              id="transition-note"
              rows={3}
              value={note}
              onChange={(event) => setNote(event.target.value)}
              placeholder={
                transitionTo === "ON_HOLD"
                  ? "e.g. Waiting on a replacement compressor, ETA Thursday."
                  : transitionTo === "CANCELLED"
                    ? "e.g. Duplicate of WO-2026-0031."
                    : ""
              }
            />
          </Field>
          {action.error ? <ErrorBanner message={action.error} /> : null}
        </Modal>
      ) : null}

      {assignOpen ? (
        <Modal
          title={job.assigneeId ? "Reassign job" : "Assign engineer"}
          onClose={closeAll}
          footer={
            <>
              <button type="button" className="btn-ghost" onClick={closeAll}>
                Cancel
              </button>
              <button
                type="button"
                className="btn-primary inline"
                disabled={action.busy || !assigneeChoice}
                onClick={() =>
                  void apply(() =>
                    workOrderApi.assign(job.id, {
                      assigneeId: Number(assigneeChoice),
                      note: note.trim() || null,
                    })
                  )
                }
              >
                {action.busy ? "Working…" : "Assign"}
              </button>
            </>
          }
        >
          <Field label="Engineer" htmlFor="assign-to">
            <select
              id="assign-to"
              value={assigneeChoice}
              onChange={(event) => setAssigneeChoice(event.target.value)}
            >
              <option value="">Choose an engineer…</option>
              {(technicians.data ?? []).map((tech) => (
                <option key={tech.id} value={String(tech.id)}>
                  {tech.fullName}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Note (optional)" htmlFor="assign-note">
            <textarea
              id="assign-note"
              rows={2}
              value={note}
              onChange={(event) => setNote(event.target.value)}
              placeholder="e.g. Nearest engineer, has the part on the van."
            />
          </Field>
          <p className="muted">
            Assigning a new job moves it to Assigned. Reassigning an in-flight job leaves its
            status alone — the work has already started.
          </p>
          {action.error ? <ErrorBanner message={action.error} /> : null}
        </Modal>
      ) : null}

      {unassignOpen ? (
        <Modal
          title="Return job to the queue"
          onClose={closeAll}
          footer={
            <>
              <button type="button" className="btn-ghost" onClick={closeAll}>
                Cancel
              </button>
              <button
                type="button"
                className="btn-primary inline"
                disabled={action.busy}
                onClick={() =>
                  void apply(() =>
                    workOrderApi.unassign(job.id, { note: note.trim() || null })
                  )
                }
              >
                {action.busy ? "Working…" : "Unassign"}
              </button>
            </>
          }
        >
          <p>
            {job.code} goes back to <strong>New</strong> and {job.assigneeName} is taken off it.
          </p>
          <Field label="Note (optional)" htmlFor="unassign-note">
            <textarea
              id="unassign-note"
              rows={2}
              value={note}
              onChange={(event) => setNote(event.target.value)}
              placeholder="e.g. Engineer off sick."
            />
          </Field>
          {action.error ? <ErrorBanner message={action.error} /> : null}
        </Modal>
      ) : null}

      {partOpen ? (
        <Modal
          title="Log a part"
          onClose={closeAll}
          footer={
            <>
              <button type="button" className="btn-ghost" onClick={closeAll}>
                Cancel
              </button>
              <button
                type="button"
                className="btn-primary inline"
                disabled={action.busy || !partChoice || Number(quantity) < 1}
                onClick={() =>
                  void apply(() =>
                    workOrderApi.logPart(job.id, {
                      partId: Number(partChoice),
                      quantity: Number(quantity),
                    })
                  )
                }
              >
                {action.busy ? "Working…" : "Log part"}
              </button>
            </>
          }
        >
          <Field label="Part" htmlFor="part-choice">
            <select
              id="part-choice"
              value={partChoice}
              onChange={(event) => setPartChoice(event.target.value)}
            >
              <option value="">Choose a part…</option>
              {(catalog.data ?? []).map((part) => (
                <option key={part.id} value={String(part.id)}>
                  {part.sku} — {part.name} ({part.stockQuantity} in stock)
                </option>
              ))}
            </select>
          </Field>
          <Field
            label="Quantity"
            htmlFor="part-qty"
            hint="Stock is drawn down as soon as this is saved, and cannot go below zero."
          >
            <input
              id="part-qty"
              type="number"
              min={1}
              value={quantity}
              onChange={(event) => setQuantity(event.target.value)}
            />
          </Field>
          {action.error ? <ErrorBanner message={action.error} /> : null}
        </Modal>
      ) : null}

      {timeOpen ? (
        <Modal
          title="Log time"
          onClose={closeAll}
          footer={
            <>
              <button type="button" className="btn-ghost" onClick={closeAll}>
                Cancel
              </button>
              <button
                type="button"
                className="btn-primary inline"
                disabled={action.busy || Number(minutes) < 1}
                onClick={() =>
                  void apply(() =>
                    workOrderApi.logTime(job.id, {
                      minutes: Number(minutes),
                      note: note.trim() || null,
                    })
                  )
                }
              >
                {action.busy ? "Working…" : "Log time"}
              </button>
            </>
          }
        >
          <Field label="Minutes" htmlFor="time-minutes">
            <input
              id="time-minutes"
              type="number"
              min={1}
              step={5}
              value={minutes}
              onChange={(event) => setMinutes(event.target.value)}
            />
          </Field>
          <Field label="What was done (optional)" htmlFor="time-note">
            <textarea
              id="time-note"
              rows={2}
              value={note}
              onChange={(event) => setNote(event.target.value)}
              placeholder="e.g. Drained and refilled the loop, rechecked pressures."
            />
          </Field>
          <p className="muted">
            Recorded against {fallback(job.assigneeName)} and added to the job's labour total.
          </p>
          {action.error ? <ErrorBanner message={action.error} /> : null}
        </Modal>
      ) : null}
    </>
  );
}
