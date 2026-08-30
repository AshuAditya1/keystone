/**
 * One typed function per endpoint.
 *
 * Components never call `api.get` directly. Everything goes through here so that
 * the URL, the request body type and the response type are declared in exactly
 * one place — if the backend renames a field, the compiler points at this file
 * rather than at fifteen call sites.
 *
 * Query strings are built with {@link query} rather than handed to axios as a
 * plain object. Axios serialises `{ statuses: ["NEW"] }` as `statuses[]=NEW`,
 * and Spring binds a `@RequestParam List<T>` only from repeated bare keys
 * (`statuses=NEW&statuses=ASSIGNED`), so the default would silently drop every
 * multi-value filter.
 */

import { api } from "./api";
import type {
  AssignRequest,
  BoardQuery,
  BoardView,
  CountResponse,
  CreateUserRequest,
  CreateWorkOrderRequest,
  CustomerRequest,
  CustomerView,
  DashboardSummary,
  LogPartRequest,
  LogTimeRequest,
  LoginResponse,
  NoteRequest,
  NotificationView,
  PageResponse,
  PartRequest,
  PartView,
  Role,
  SiteRequest,
  SiteView,
  TransitionRequest,
  UpdateUserRequest,
  UpdateWorkOrderRequest,
  UserSummary,
  UserView,
  WorkOrderDetail,
  WorkOrderQuery,
  WorkOrderSummary,
} from "./types";

type QueryValue = string | number | boolean | null | undefined | string[];

/**
 * Build a query string, dropping anything absent.
 *
 * Empty strings are dropped as well as null and undefined: a cleared search box
 * should mean "no filter", not "match the empty string". `false` is kept, because
 * `unassigned=false` is a meaningful instruction.
 */
function query(params: Record<string, QueryValue>): URLSearchParams {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === null || value === undefined || value === "") {
      return;
    }
    if (Array.isArray(value)) {
      value.forEach((item) => search.append(key, item));
      return;
    }
    search.append(key, String(value));
  });
  return search;
}

// ------------------------------------------------------------------------ auth

export const authApi = {
  async login(email: string, password: string): Promise<LoginResponse> {
    const { data } = await api.post<LoginResponse>("/auth/login", { email, password });
    return data;
  },

  async me(): Promise<UserView> {
    const { data } = await api.get<UserView>("/auth/me");
    return data;
  },
};

// ----------------------------------------------------------------- work orders

export const workOrderApi = {
  async list(q: WorkOrderQuery): Promise<PageResponse<WorkOrderSummary>> {
    const { data } = await api.get<PageResponse<WorkOrderSummary>>("/work-orders", {
      params: query({
        statuses: q.statuses,
        priority: q.priority,
        slaStatus: q.slaStatus,
        assigneeId: q.assigneeId,
        customerId: q.customerId,
        siteId: q.siteId,
        unassigned: q.unassigned,
        openOnly: q.openOnly,
        search: q.search,
        page: q.page,
        size: q.size,
        sort: q.sort,
      }),
    });
    return data;
  },

  async board(q: BoardQuery): Promise<BoardView> {
    const { data } = await api.get<BoardView>("/work-orders/board", {
      params: query({
        priority: q.priority,
        assigneeId: q.assigneeId,
        customerId: q.customerId,
        unassigned: q.unassigned,
        search: q.search,
      }),
    });
    return data;
  },

  /** The technician's own open jobs, most urgent first. Server-scoped to the caller. */
  async myWork(): Promise<WorkOrderSummary[]> {
    const { data } = await api.get<WorkOrderSummary[]>("/work-orders/my");
    return data;
  },

  async detail(id: number): Promise<WorkOrderDetail> {
    const { data } = await api.get<WorkOrderDetail>(`/work-orders/${id}`);
    return data;
  },

  async create(body: CreateWorkOrderRequest): Promise<WorkOrderDetail> {
    const { data } = await api.post<WorkOrderDetail>("/work-orders", body);
    return data;
  },

  async update(id: number, body: UpdateWorkOrderRequest): Promise<WorkOrderDetail> {
    const { data } = await api.put<WorkOrderDetail>(`/work-orders/${id}`, body);
    return data;
  },

  async assign(id: number, body: AssignRequest): Promise<WorkOrderDetail> {
    const { data } = await api.post<WorkOrderDetail>(`/work-orders/${id}/assign`, body);
    return data;
  },

  async unassign(id: number, body: NoteRequest): Promise<WorkOrderDetail> {
    const { data } = await api.post<WorkOrderDetail>(`/work-orders/${id}/unassign`, body);
    return data;
  },

  async transition(id: number, body: TransitionRequest): Promise<WorkOrderDetail> {
    const { data } = await api.post<WorkOrderDetail>(`/work-orders/${id}/transition`, body);
    return data;
  },

  async logPart(id: number, body: LogPartRequest): Promise<WorkOrderDetail> {
    const { data } = await api.post<WorkOrderDetail>(`/work-orders/${id}/parts`, body);
    return data;
  },

  async removePart(id: number, usageId: number): Promise<WorkOrderDetail> {
    const { data } = await api.delete<WorkOrderDetail>(`/work-orders/${id}/parts/${usageId}`);
    return data;
  },

  async logTime(id: number, body: LogTimeRequest): Promise<WorkOrderDetail> {
    const { data } = await api.post<WorkOrderDetail>(`/work-orders/${id}/time`, body);
    return data;
  },
};

// ------------------------------------------------------------------- dashboard

export const dashboardApi = {
  async summary(): Promise<DashboardSummary> {
    const { data } = await api.get<DashboardSummary>("/dashboard/summary");
    return data;
  },
};

// ------------------------------------------------------------------- customers

export const customerApi = {
  async list(params: {
    search?: string | null;
    page?: number | null;
    size?: number | null;
    sort?: string | null;
  }): Promise<PageResponse<CustomerView>> {
    const { data } = await api.get<PageResponse<CustomerView>>("/customers", {
      params: query({
        search: params.search,
        page: params.page,
        size: params.size,
        sort: params.sort,
      }),
    });
    return data;
  },

  async get(id: number): Promise<CustomerView> {
    const { data } = await api.get<CustomerView>(`/customers/${id}`);
    return data;
  },

  /** Unpaged — this feeds the site picker when raising a job. */
  async sites(id: number): Promise<SiteView[]> {
    const { data } = await api.get<SiteView[]>(`/customers/${id}/sites`);
    return data;
  },

  async create(body: CustomerRequest): Promise<CustomerView> {
    const { data } = await api.post<CustomerView>("/customers", body);
    return data;
  },

  async update(id: number, body: CustomerRequest): Promise<CustomerView> {
    const { data } = await api.put<CustomerView>(`/customers/${id}`, body);
    return data;
  },

  async remove(id: number): Promise<void> {
    await api.delete(`/customers/${id}`);
  },
};

// ----------------------------------------------------------------------- sites

export const siteApi = {
  async list(params: {
    customerId?: number | null;
    search?: string | null;
    page?: number | null;
    size?: number | null;
    sort?: string | null;
  }): Promise<PageResponse<SiteView>> {
    const { data } = await api.get<PageResponse<SiteView>>("/sites", {
      params: query({
        customerId: params.customerId,
        search: params.search,
        page: params.page,
        size: params.size,
        sort: params.sort,
      }),
    });
    return data;
  },

  async get(id: number): Promise<SiteView> {
    const { data } = await api.get<SiteView>(`/sites/${id}`);
    return data;
  },

  async create(body: SiteRequest): Promise<SiteView> {
    const { data } = await api.post<SiteView>("/sites", body);
    return data;
  },

  async update(id: number, body: SiteRequest): Promise<SiteView> {
    const { data } = await api.put<SiteView>(`/sites/${id}`, body);
    return data;
  },

  async remove(id: number): Promise<void> {
    await api.delete(`/sites/${id}`);
  },
};

// ----------------------------------------------------------------------- parts

export const partApi = {
  async list(params: {
    search?: string | null;
    lowStockOnly?: boolean | null;
    page?: number | null;
    size?: number | null;
    sort?: string | null;
  }): Promise<PageResponse<PartView>> {
    const { data } = await api.get<PageResponse<PartView>>("/parts", {
      params: query({
        search: params.search,
        lowStockOnly: params.lowStockOnly,
        page: params.page,
        size: params.size,
        sort: params.sort,
      }),
    });
    return data;
  },

  /** The whole catalogue, for the part picker in the field view. */
  async catalog(): Promise<PartView[]> {
    const { data } = await api.get<PartView[]>("/parts/catalog");
    return data;
  },

  async get(id: number): Promise<PartView> {
    const { data } = await api.get<PartView>(`/parts/${id}`);
    return data;
  },

  async create(body: PartRequest): Promise<PartView> {
    const { data } = await api.post<PartView>("/parts", body);
    return data;
  },

  async update(id: number, body: PartRequest): Promise<PartView> {
    const { data } = await api.put<PartView>(`/parts/${id}`, body);
    return data;
  },

  async remove(id: number): Promise<void> {
    await api.delete(`/parts/${id}`);
  },
};

// ----------------------------------------------------------------------- users

export const userApi = {
  async list(role?: Role | null): Promise<UserSummary[]> {
    const { data } = await api.get<UserSummary[]>("/users", {
      params: query({ role }),
    });
    return data;
  },

  /** Active technicians only — the assignment picker's source of truth. */
  async technicians(): Promise<UserSummary[]> {
    const { data } = await api.get<UserSummary[]>("/users/technicians");
    return data;
  },

  async get(id: number): Promise<UserSummary> {
    const { data } = await api.get<UserSummary>(`/users/${id}`);
    return data;
  },

  async create(body: CreateUserRequest): Promise<UserSummary> {
    const { data } = await api.post<UserSummary>("/users", body);
    return data;
  },

  async update(id: number, body: UpdateUserRequest): Promise<UserSummary> {
    const { data } = await api.put<UserSummary>(`/users/${id}`, body);
    return data;
  },
};

// --------------------------------------------------------------- notifications

export const notificationApi = {
  async recent(): Promise<NotificationView[]> {
    const { data } = await api.get<NotificationView[]>("/notifications");
    return data;
  },

  async unreadCount(): Promise<CountResponse> {
    const { data } = await api.get<CountResponse>("/notifications/unread-count");
    return data;
  },

  async markRead(id: number): Promise<NotificationView> {
    const { data } = await api.post<NotificationView>(`/notifications/${id}/read`);
    return data;
  },

  async markAllRead(): Promise<CountResponse> {
    const { data } = await api.post<CountResponse>("/notifications/read-all");
    return data;
  },
};
