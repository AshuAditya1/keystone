/**
 * Display formatting.
 *
 * Kept apart from the components because the same job code, deadline and cost
 * appear on the board, the list, the detail page and the dashboard, and they have
 * to read identically in all four. Everything here is defensive about nulls: the
 * API returns `null` for an unassigned engineer, an unset deadline and an
 * unfinished job, and a screen full of "null" is worse than a screen full of
 * dashes.
 */

import type {
  NotificationType,
  Priority,
  Role,
  SlaStatus,
  WorkOrderStatus,
} from "./types";

/** What to show where the API gave us nothing. */
const EMPTY = "—";

// -------------------------------------------------------------------- date/time

const DATE_TIME = new Intl.DateTimeFormat("en-GB", {
  day: "2-digit",
  month: "short",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
});

const DATE_ONLY = new Intl.DateTimeFormat("en-GB", {
  day: "2-digit",
  month: "short",
  year: "numeric",
});

const TIME_ONLY = new Intl.DateTimeFormat("en-GB", {
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
});

function parse(iso: string | null | undefined): Date | null {
  if (!iso) {
    return null;
  }
  const date = new Date(iso);
  // An unparseable string yields an Invalid Date, whose getTime() is NaN.
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatDateTime(iso: string | null | undefined): string {
  const date = parse(iso);
  return date ? DATE_TIME.format(date) : EMPTY;
}

export function formatDate(iso: string | null | undefined): string {
  const date = parse(iso);
  return date ? DATE_ONLY.format(date) : EMPTY;
}

export function formatTime(iso: string | null | undefined): string {
  const date = parse(iso);
  return date ? TIME_ONLY.format(date) : EMPTY;
}

/**
 * "in 3h", "5m ago" — the form a dispatcher scanning a board actually reads.
 *
 * Deliberately coarse. Past the day boundary the exact number of hours stops
 * being useful, so it degrades to days and then to an absolute date.
 */
export function formatRelative(iso: string | null | undefined): string {
  const date = parse(iso);
  if (!date) {
    return EMPTY;
  }
  const deltaMs = date.getTime() - Date.now();
  const future = deltaMs > 0;
  const minutes = Math.floor(Math.abs(deltaMs) / 60_000);

  if (minutes < 1) {
    return "just now";
  }
  const said = (amount: string) => (future ? `in ${amount}` : `${amount} ago`);
  if (minutes < 60) {
    return said(`${minutes}m`);
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    const remainder = minutes % 60;
    return said(remainder === 0 ? `${hours}h` : `${hours}h ${remainder}m`);
  }
  const days = Math.floor(hours / 24);
  if (days <= 14) {
    return said(`${days}d`);
  }
  return formatDate(iso);
}

/** Whether a deadline has already gone by. Drives the red styling on the due cell. */
export function isOverdue(iso: string | null | undefined): boolean {
  const date = parse(iso);
  return date !== null && date.getTime() < Date.now();
}

// ------------------------------------------------------------ durations, money

/** 135 becomes "2h 15m"; 0 becomes "0m" rather than an empty string. */
export function formatMinutes(minutes: number | null | undefined): string {
  if (minutes === null || minutes === undefined) {
    return EMPTY;
  }
  if (minutes < 60) {
    return `${minutes}m`;
  }
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest === 0 ? `${hours}h` : `${hours}h ${rest}m`;
}

const MONEY = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
});

export function formatMoney(amount: number | null | undefined): string {
  if (amount === null || amount === undefined) {
    return EMPTY;
  }
  return MONEY.format(amount);
}

/** One decimal place, for the average-completion figure on the dashboard. */
export function formatHours(hours: number | null | undefined): string {
  if (hours === null || hours === undefined) {
    return EMPTY;
  }
  return `${hours.toFixed(1)}h`;
}

// ------------------------------------------------------------- enum → prose

const STATUS_LABELS: Record<WorkOrderStatus, string> = {
  NEW: "New",
  ASSIGNED: "Assigned",
  IN_PROGRESS: "In progress",
  ON_HOLD: "On hold",
  COMPLETED: "Completed",
  CLOSED: "Closed",
  CANCELLED: "Cancelled",
};

const PRIORITY_LABELS: Record<Priority, string> = {
  LOW: "Low",
  MEDIUM: "Medium",
  HIGH: "High",
  URGENT: "Urgent",
};

const SLA_LABELS: Record<SlaStatus, string> = {
  ON_TRACK: "On track",
  AT_RISK: "At risk",
  BREACHED: "Breached",
};

const ROLE_LABELS: Record<Role, string> = {
  MANAGER: "Manager",
  DISPATCHER: "Dispatcher",
  TECHNICIAN: "Technician",
  CUSTOMER: "Customer",
};

const NOTIFICATION_LABELS: Record<NotificationType, string> = {
  SLA_BREACH: "SLA breached",
  SLA_AT_RISK: "SLA at risk",
  WORK_ORDER_ASSIGNED: "Assigned to you",
  STATUS_CHANGED: "Status changed",
};

export function statusLabel(status: WorkOrderStatus | null | undefined): string {
  return status ? STATUS_LABELS[status] : EMPTY;
}

export function priorityLabel(priority: Priority | null | undefined): string {
  return priority ? PRIORITY_LABELS[priority] : EMPTY;
}

export function slaLabel(sla: SlaStatus | null | undefined): string {
  return sla ? SLA_LABELS[sla] : EMPTY;
}

export function roleLabel(role: Role | null | undefined): string {
  return role ? ROLE_LABELS[role] : EMPTY;
}

export function notificationLabel(type: NotificationType | null | undefined): string {
  return type ? NOTIFICATION_LABELS[type] : EMPTY;
}

/**
 * The verb for a lifecycle button.
 *
 * The server sends the target status; a button reading "In progress" would be
 * ambiguous about whether it describes the current state or the action, so each
 * one is given an imperative label instead.
 */
const TRANSITION_VERBS: Record<WorkOrderStatus, string> = {
  NEW: "Return to new",
  ASSIGNED: "Send back to assigned",
  IN_PROGRESS: "Start work",
  ON_HOLD: "Put on hold",
  COMPLETED: "Complete job",
  CLOSED: "Close",
  CANCELLED: "Cancel job",
};

export function transitionVerb(target: WorkOrderStatus): string {
  return TRANSITION_VERBS[target];
}

/**
 * Transitions the server insists on having a note for.
 *
 * Mirrors the rule in `WorkOrderService`: pausing or cancelling requires an
 * explanation. Duplicated here only so the form can mark the field required
 * before the round trip; the server still refuses a blank note with a 409.
 */
export function noteRequiredFor(target: WorkOrderStatus): boolean {
  return target === "ON_HOLD" || target === "CANCELLED";
}

/** Anything else is a display-only word: text placed inside a coloured pill. */
export function fallback(value: string | null | undefined): string {
  return value === null || value === undefined || value === "" ? EMPTY : value;
}

/** A CSS class suffix, so `.pill-urgent` and friends can be written by hand. */
export function slug(value: string): string {
  return value.toLowerCase().replace(/_/g, "-");
}
