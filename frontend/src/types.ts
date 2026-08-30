/**
 * The shape of the API, mirrored field-for-field from the backend DTOs.
 *
 * These are hand-written rather than generated, so the one rule that matters is
 * that a name here must match the Java record component exactly. A typo does not
 * fail the build — it silently reads `undefined` and renders a blank cell, which
 * is the worst possible failure mode. `scripts/static-check.py` cross-checks
 * these against the Java records for that reason.
 *
 * Instants arrive as ISO-8601 strings and BigDecimals as JSON numbers, so both
 * are typed as the primitives they actually are on the wire rather than as
 * `Date`/`Decimal` objects that would need parsing at every use site.
 */

// ---------------------------------------------------------------- enumerations

/** Declared as unions rather than TS enums so they compare directly to the wire value. */
export type Role = "DISPATCHER" | "TECHNICIAN" | "MANAGER" | "CUSTOMER";

export type Priority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";

export type WorkOrderStatus =
  | "NEW"
  | "ASSIGNED"
  | "IN_PROGRESS"
  | "ON_HOLD"
  | "COMPLETED"
  | "CLOSED"
  | "CANCELLED";

export type SlaStatus = "ON_TRACK" | "AT_RISK" | "BREACHED";

export type NotificationType =
  | "SLA_BREACH"
  | "SLA_AT_RISK"
  | "WORK_ORDER_ASSIGNED"
  | "STATUS_CHANGED";

/** Declaration order matters: these drive the order of filter dropdowns and board columns. */
export const ROLES: Role[] = ["MANAGER", "DISPATCHER", "TECHNICIAN", "CUSTOMER"];

export const PRIORITIES: Priority[] = ["URGENT", "HIGH", "MEDIUM", "LOW"];

export const WORK_ORDER_STATUSES: WorkOrderStatus[] = [
  "NEW",
  "ASSIGNED",
  "IN_PROGRESS",
  "ON_HOLD",
  "COMPLETED",
  "CLOSED",
  "CANCELLED",
];

export const SLA_STATUSES: SlaStatus[] = ["ON_TRACK", "AT_RISK", "BREACHED"];

/**
 * Statuses a job can sit in and still be considered live work.
 * Kept in step with `WorkOrderStatus.isOpen()` on the server; the server is
 * authoritative, this copy only decides what the UI greys out.
 */
export const OPEN_STATUSES: WorkOrderStatus[] = [
  "NEW",
  "ASSIGNED",
  "IN_PROGRESS",
  "ON_HOLD",
];

// ------------------------------------------------------------------- envelopes

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface CountResponse {
  count: number;
}

/** The single error body every failing endpoint returns. */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: Record<string, string> | null;
}

// ---------------------------------------------------------------------- people

export interface UserView {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  customerId: number | null;
}

/** The admin-screen view: everything in {@link UserView} plus account state. */
export interface UserSummary {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  customerId: number | null;
  customerName: string | null;
  active: boolean;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresInMinutes: number;
  user: UserView;
}

export interface CreateUserRequest {
  email: string;
  password: string;
  fullName: string;
  role: Role;
  customerId?: number | null;
}

/** Every field is optional: this is a patch, and omitted fields are left alone. */
export interface UpdateUserRequest {
  fullName?: string | null;
  role?: Role | null;
  active?: boolean | null;
  customerId?: number | null;
  password?: string | null;
}

// ------------------------------------------------------------ customers, sites

export interface CustomerView {
  id: number;
  name: string;
  contactEmail: string | null;
  contactPhone: string | null;
}

export interface CustomerRequest {
  name: string;
  contactEmail?: string | null;
  contactPhone?: string | null;
}

export interface SiteView {
  id: number;
  customerId: number;
  customerName: string;
  name: string;
  address: string | null;
}

export interface SiteRequest {
  customerId: number;
  name: string;
  address?: string | null;
}

// ------------------------------------------------------------------- inventory

export interface PartView {
  id: number;
  sku: string;
  name: string;
  unitCost: number;
  stockQuantity: number;
}

export interface PartRequest {
  sku: string;
  name: string;
  unitCost: number;
  stockQuantity: number;
}

/** Mirrors `PartService.LOW_STOCK_THRESHOLD`; used only to colour the stock cell. */
export const LOW_STOCK_THRESHOLD = 5;

// ----------------------------------------------------------------- work orders

export interface WorkOrderSummary {
  id: number;
  code: string;
  title: string;
  priority: Priority;
  status: WorkOrderStatus;
  customerId: number;
  customerName: string;
  siteId: number;
  siteName: string;
  assigneeId: number | null;
  assigneeName: string | null;
  slaDueAt: string | null;
  slaStatus: SlaStatus;
  completedAt: string | null;
  totalLaborMinutes: number;
  totalPartsCost: number;
  createdAt: string;
  updatedAt: string;
}

export interface StatusHistoryView {
  id: number;
  fromStatus: WorkOrderStatus | null;
  toStatus: WorkOrderStatus;
  changedById: number | null;
  changedByName: string;
  note: string | null;
  createdAt: string;
  /** Only populated on the dashboard feed, where rows come from many jobs. */
  workOrderId: number | null;
  workOrderCode: string | null;
}

export interface PartUsageView {
  id: number;
  partId: number;
  partSku: string;
  partName: string;
  quantity: number;
  unitCostAtUse: number;
  lineCost: number;
  loggedById: number | null;
  loggedByName: string;
  createdAt: string;
}

export interface TimeLogView {
  id: number;
  technicianId: number;
  technicianName: string;
  minutes: number;
  note: string | null;
  createdAt: string;
}

/**
 * The full job record.
 *
 * `allowedTransitions`, `canEdit`, `canAssign` and `canLogWork` are computed by
 * the server for the calling user. The UI renders buttons from them and never
 * decides for itself what a role may do — the same rules are re-checked on the
 * write request, so these flags are a convenience, not the enforcement point.
 */
export interface WorkOrderDetail {
  id: number;
  code: string;
  title: string;
  description: string | null;
  priority: Priority;
  status: WorkOrderStatus;
  customerId: number;
  customerName: string;
  siteId: number;
  siteName: string;
  siteAddress: string | null;
  assigneeId: number | null;
  assigneeName: string | null;
  slaDueAt: string | null;
  slaStatus: SlaStatus;
  completedAt: string | null;
  totalLaborMinutes: number;
  totalPartsCost: number;
  createdAt: string;
  updatedAt: string;
  history: StatusHistoryView[];
  parts: PartUsageView[];
  timeLogs: TimeLogView[];
  allowedTransitions: WorkOrderStatus[];
  canEdit: boolean;
  canAssign: boolean;
  canLogWork: boolean;
}

export interface BoardColumn {
  status: WorkOrderStatus;
  /** The true total for the column, which may exceed `items.length`. */
  count: number;
  items: WorkOrderSummary[];
}

export interface BoardView {
  columns: BoardColumn[];
}

export interface CreateWorkOrderRequest {
  title: string;
  description?: string | null;
  priority: Priority;
  siteId: number;
  assigneeId?: number | null;
}

export interface UpdateWorkOrderRequest {
  title: string;
  description?: string | null;
  priority: Priority;
  siteId: number;
}

export interface AssignRequest {
  assigneeId: number;
  note?: string | null;
}

export interface NoteRequest {
  note?: string | null;
}

export interface TransitionRequest {
  targetStatus: WorkOrderStatus;
  note?: string | null;
}

export interface LogPartRequest {
  partId: number;
  quantity: number;
}

export interface LogTimeRequest {
  minutes: number;
  note?: string | null;
}

/** Query parameters accepted by the work-order list endpoint. */
export interface WorkOrderQuery {
  statuses?: WorkOrderStatus[];
  priority?: Priority | null;
  slaStatus?: SlaStatus | null;
  assigneeId?: number | null;
  customerId?: number | null;
  siteId?: number | null;
  unassigned?: boolean | null;
  openOnly?: boolean | null;
  search?: string | null;
  page?: number | null;
  size?: number | null;
  sort?: string | null;
}

/** The narrower set the board endpoint reads; it has no paging and no status filter. */
export interface BoardQuery {
  priority?: Priority | null;
  assigneeId?: number | null;
  customerId?: number | null;
  unassigned?: boolean | null;
  search?: string | null;
}

// -------------------------------------------------------------------- reporting

export interface TechnicianLoad {
  technicianId: number;
  technicianName: string;
  activeCount: number;
  completedCount: number;
}

export interface DashboardSummary {
  total: number;
  open: number;
  unassigned: number;
  slaBreached: number;
  slaAtRisk: number;
  completedLast7Days: number;
  avgCompletionHours: number | null;
  byStatus: Partial<Record<WorkOrderStatus, number>>;
  byPriority: Partial<Record<Priority, number>>;
  technicianLoad: TechnicianLoad[];
  recentActivity: StatusHistoryView[];
}

// ---------------------------------------------------------------- notifications

export interface NotificationView {
  id: number;
  type: NotificationType;
  title: string;
  message: string;
  workOrderId: number | null;
  workOrderCode: string | null;
  read: boolean;
  createdAt: string;
}
