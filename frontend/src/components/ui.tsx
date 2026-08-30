/**
 * The shared vocabulary of the interface.
 *
 * Small, presentational, and deliberately in one file: a status pill appears on
 * five screens, and the moment its markup is duplicated the board and the list
 * start disagreeing about what "at risk" looks like. Nothing here fetches, and
 * nothing here decides what a user is allowed to do.
 */

import { useEffect } from "react";
import type { ChangeEvent, ReactNode } from "react";
import { isOverdue, priorityLabel, slaLabel, slug, statusLabel } from "../format";
import type { Priority, SlaStatus, WorkOrderStatus } from "../types";

// ------------------------------------------------------------------------ pills

export function StatusPill({ status }: { status: WorkOrderStatus }) {
  return <span className={`pill status-${slug(status)}`}>{statusLabel(status)}</span>;
}

export function PriorityPill({ priority }: { priority: Priority }) {
  return <span className={`pill priority-${slug(priority)}`}>{priorityLabel(priority)}</span>;
}

/**
 * The SLA pill.
 *
 * A completed job keeps whatever verdict it earned, so this reads the status the
 * server sent rather than recomputing anything from the deadline. The deadline is
 * used only for the tooltip.
 */
export function SlaPill({ sla, dueAt }: { sla: SlaStatus; dueAt?: string | null }) {
  const title = dueAt
    ? `Due ${new Date(dueAt).toLocaleString()}${isOverdue(dueAt) ? " (past)" : ""}`
    : "No deadline set";
  return (
    <span className={`pill sla-${slug(sla)}`} title={title}>
      {slaLabel(sla)}
    </span>
  );
}

// ------------------------------------------------------------------- page states

export function Loading({ label = "Loading…" }: { label?: string }) {
  return (
    <div className="state-block" role="status">
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function ErrorBanner({
  message,
  onDismiss,
}: {
  message: string;
  onDismiss?: () => void;
}) {
  return (
    <div className="error-banner" role="alert">
      <span>{message}</span>
      {onDismiss ? (
        <button type="button" className="banner-close" onClick={onDismiss} aria-label="Dismiss">
          &times;
        </button>
      ) : null}
    </div>
  );
}

export function EmptyState({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="state-block empty">
      <strong>{title}</strong>
      {hint ? <span className="muted">{hint}</span> : null}
    </div>
  );
}

// -------------------------------------------------------------------- structure

export function PageHeader({
  title,
  subtitle,
  actions,
}: {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}) {
  return (
    <header className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle ? <p className="muted">{subtitle}</p> : null}
      </div>
      {actions ? <div className="page-actions">{actions}</div> : null}
    </header>
  );
}

export function Stat({
  label,
  value,
  tone,
  hint,
}: {
  label: string;
  value: ReactNode;
  tone?: "default" | "warn" | "danger" | "good";
  hint?: string;
}) {
  return (
    <div className={`stat tone-${tone ?? "default"}`}>
      <span className="stat-label">{label}</span>
      <span className="stat-value">{value}</span>
      {hint ? <span className="stat-hint">{hint}</span> : null}
    </div>
  );
}

/** A labelled form control, with room for the server's per-field complaint. */
export function Field({
  label,
  htmlFor,
  error,
  hint,
  children,
}: {
  label: string;
  htmlFor?: string;
  error?: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <div className={`field${error ? " field-invalid" : ""}`}>
      <label htmlFor={htmlFor}>{label}</label>
      {children}
      {hint && !error ? <span className="field-hint">{hint}</span> : null}
      {error ? <span className="field-error">{error}</span> : null}
    </div>
  );
}

// ----------------------------------------------------------------------- paging

/**
 * Page controls.
 *
 * `first` and `last` come from the server rather than being derived from
 * `page === 0` — with a filter applied the two can differ, and trusting the
 * server is what keeps the buttons honest on the last page of a shrinking set.
 */
export function Pagination({
  page,
  totalPages,
  totalElements,
  first,
  last,
  onPage,
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  first: boolean;
  last: boolean;
  onPage: (page: number) => void;
}) {
  if (totalElements === 0) {
    return null;
  }
  return (
    <div className="pagination">
      <span className="muted">
        Page {page + 1} of {Math.max(totalPages, 1)} · {totalElements}{" "}
        {totalElements === 1 ? "result" : "results"}
      </span>
      <div className="pagination-buttons">
        <button type="button" className="btn-ghost" disabled={first} onClick={() => onPage(page - 1)}>
          Previous
        </button>
        <button type="button" className="btn-ghost" disabled={last} onClick={() => onPage(page + 1)}>
          Next
        </button>
      </div>
    </div>
  );
}

// ------------------------------------------------------------------------ modal

/**
 * A dialog.
 *
 * Escape closes it and a click on the backdrop closes it, because a modal that
 * can only be dismissed by finding the right button is the single most irritating
 * thing an internal tool can do.
 */
export function Modal({
  title,
  onClose,
  children,
  footer,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
}) {
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onClose();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="modal-backdrop" onClick={onClose} role="presentation">
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="modal-head">
          <h2>{title}</h2>
          <button type="button" className="banner-close" onClick={onClose} aria-label="Close">
            &times;
          </button>
        </div>
        <div className="modal-body">{children}</div>
        {footer ? <div className="modal-foot">{footer}</div> : null}
      </div>
    </div>
  );
}

// ----------------------------------------------------------------------- inputs

/**
 * A `<select>` over an enum, with a labelling function.
 *
 * The empty option carries the empty string, which the query builder in
 * `endpoints.ts` drops rather than sending — so clearing the dropdown clears the
 * filter instead of asking the server to match a literal "".
 */
export function EnumSelect<T extends string>({
  id,
  value,
  options,
  label,
  anyLabel,
  onChange,
  disabled,
}: {
  id?: string;
  value: T | "";
  options: readonly T[];
  label: (option: T) => string;
  anyLabel?: string;
  onChange: (value: T | "") => void;
  disabled?: boolean;
}) {
  return (
    <select
      id={id}
      value={value}
      disabled={disabled}
      onChange={(event: ChangeEvent<HTMLSelectElement>) => onChange(event.target.value as T | "")}
    >
      {anyLabel !== undefined ? <option value="">{anyLabel}</option> : null}
      {options.map((option) => (
        <option key={option} value={option}>
          {label(option)}
        </option>
      ))}
    </select>
  );
}
