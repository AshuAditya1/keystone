/**
 * The notification bell.
 *
 * Polls the unread count every 60 seconds. Polling rather than a websocket is a
 * deliberate trade: the SLA sweep that generates most of these runs on a schedule
 * anyway, so sub-minute freshness buys nothing, and a single cheap COUNT query is
 * far less to operate — and to deploy on a free tier — than a persistent
 * connection.
 *
 * Opening the panel loads the list lazily, so a user who never clicks the bell
 * never pays for the full fetch.
 */

import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { errorMessage } from "../api";
import { notificationApi } from "../endpoints";
import { formatRelative, notificationLabel, slug } from "../format";
import type { NotificationView } from "../types";

const POLL_INTERVAL_MS = 60_000;

export function NotificationBell() {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [unread, setUnread] = useState(0);
  const [items, setItems] = useState<NotificationView[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const panel = useRef<HTMLDivElement | null>(null);

  const refreshCount = useCallback(async () => {
    try {
      const { count } = await notificationApi.unreadCount();
      setUnread(count);
    } catch {
      // A failed poll is not worth interrupting the user for; the next one may
      // succeed, and the badge simply keeps its previous value.
    }
  }, []);

  useEffect(() => {
    void refreshCount();
    const timer = window.setInterval(() => void refreshCount(), POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [refreshCount]);

  // Close on an outside click, so the panel behaves like every other dropdown.
  useEffect(() => {
    if (!open) {
      return;
    }
    const onDocumentClick = (event: MouseEvent) => {
      if (panel.current && !panel.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", onDocumentClick);
    return () => document.removeEventListener("mousedown", onDocumentClick);
  }, [open]);

  const toggle = async () => {
    const next = !open;
    setOpen(next);
    if (!next) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setItems(await notificationApi.recent());
    } catch (err: unknown) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  const openItem = async (item: NotificationView) => {
    setOpen(false);
    if (!item.read) {
      try {
        await notificationApi.markRead(item.id);
        setUnread((n) => Math.max(0, n - 1));
      } catch {
        // Navigating matters more than the read receipt; the count self-corrects
        // on the next poll.
      }
    }
    if (item.workOrderId) {
      navigate(`/work-orders/${item.workOrderId}`);
    }
  };

  const markAll = async () => {
    try {
      await notificationApi.markAllRead();
      setItems((current) => current.map((item) => ({ ...item, read: true })));
      setUnread(0);
    } catch (err: unknown) {
      setError(errorMessage(err));
    }
  };

  return (
    <div className="bell-wrap" ref={panel}>
      <button
        type="button"
        className="bell"
        onClick={() => void toggle()}
        aria-label={unread > 0 ? `Notifications, ${unread} unread` : "Notifications"}
        aria-expanded={open}
      >
        <span aria-hidden="true">Alerts</span>
        {unread > 0 ? <span className="bell-badge">{unread > 99 ? "99+" : unread}</span> : null}
      </button>

      {open ? (
        <div className="bell-panel" role="menu">
          <div className="bell-head">
            <strong>Notifications</strong>
            {unread > 0 ? (
              <button type="button" className="btn-link" onClick={() => void markAll()}>
                Mark all read
              </button>
            ) : null}
          </div>

          {loading ? <div className="bell-empty muted">Loading…</div> : null}
          {error ? <div className="bell-empty error-text">{error}</div> : null}
          {!loading && !error && items.length === 0 ? (
            <div className="bell-empty muted">Nothing to report.</div>
          ) : null}

          <ul className="bell-list">
            {items.map((item) => (
              <li key={item.id}>
                <button
                  type="button"
                  className={item.read ? "bell-item" : "bell-item unread"}
                  onClick={() => void openItem(item)}
                >
                  <span className={`bell-kind kind-${slug(item.type)}`}>
                    {notificationLabel(item.type)}
                  </span>
                  <span className="bell-title">{item.title}</span>
                  <span className="bell-message">{item.message}</span>
                  <span className="bell-meta muted">
                    {item.workOrderCode ? `${item.workOrderCode} · ` : ""}
                    {formatRelative(item.createdAt)}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </div>
  );
}
