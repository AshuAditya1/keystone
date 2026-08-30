/**
 * Raise a job, or edit one.
 *
 * One component for both, because the two forms are the same fields with one
 * difference — on create a dispatcher may hand the job straight to an engineer,
 * which is meaningless when editing an existing one.
 *
 * The site list comes from the sites endpoint rather than being built from a
 * customer picker, so it is already scoped: a portal user raising a job sees only
 * their own sites, and there is no way to point a job at somebody else's building.
 * The customer is derived from the site on the server, never sent by the client.
 */

import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { fieldErrors } from "../api";
import { useAuth } from "../auth";
import {
  EnumSelect,
  ErrorBanner,
  Field,
  Loading,
  PageHeader,
} from "../components/ui";
import { siteApi, userApi, workOrderApi } from "../endpoints";
import { priorityLabel } from "../format";
import { useApi, useAction } from "../hooks";
import { PRIORITIES, type Priority } from "../types";

/** Mirrors `SlaPolicy`, shown so the person choosing a priority knows what it commits to. */
const SLA_WINDOWS: Record<Priority, string> = {
  URGENT: "4 hours",
  HIGH: "8 hours",
  MEDIUM: "24 hours",
  LOW: "72 hours",
};

export function WorkOrderFormPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { hasRole } = useAuth();

  const editing = id !== undefined;
  const workOrderId = Number(id);
  const canAssignOnCreate = hasRole("MANAGER", "DISPATCHER");

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState<Priority>("MEDIUM");
  const [siteId, setSiteId] = useState("");
  const [assigneeId, setAssigneeId] = useState("");
  const [invalid, setInvalid] = useState<Record<string, string>>({});

  const action = useAction();

  const sites = useApi(
    () => siteApi.list({ size: 200, sort: "name,asc" }).then((paged) => paged.content),
    []
  );
  const technicians = useApi(
    () => (canAssignOnCreate && !editing ? userApi.technicians() : Promise.resolve([])),
    [canAssignOnCreate, editing]
  );
  const existing = useApi(
    () => (editing ? workOrderApi.detail(workOrderId) : Promise.resolve(null)),
    [editing, workOrderId]
  );

  // Prefill once the existing job arrives. Keyed on the loaded object so a reload
  // re-seeds the form, but typing is not clobbered on every render.
  const loaded = existing.data;
  useEffect(() => {
    if (loaded) {
      setTitle(loaded.title);
      setDescription(loaded.description ?? "");
      setPriority(loaded.priority);
      setSiteId(String(loaded.siteId));
    }
  }, [loaded]);

  const submit = () =>
    action.run(async () => {
      setInvalid({});
      try {
        const saved = editing
          ? await workOrderApi.update(workOrderId, {
              title: title.trim(),
              description: description.trim() || null,
              priority,
              siteId: Number(siteId),
            })
          : await workOrderApi.create({
              title: title.trim(),
              description: description.trim() || null,
              priority,
              siteId: Number(siteId),
              assigneeId: assigneeId ? Number(assigneeId) : null,
            });
        navigate(`/work-orders/${saved.id}`, { replace: true });
      } catch (err: unknown) {
        // Bean Validation returns a field-keyed map; put each message on its input
        // and let useAction surface the summary.
        setInvalid(fieldErrors(err));
        throw err;
      }
    });

  if (editing && existing.loading && !loaded) {
    return <Loading label="Loading job…" />;
  }
  if (editing && existing.error && !loaded) {
    return <ErrorBanner message={existing.error} />;
  }

  const ready = title.trim().length > 0 && siteId !== "";

  return (
    <>
      <PageHeader
        title={editing ? `Edit ${loaded?.code ?? "job"}` : "Raise a work order"}
        subtitle={
          editing
            ? "The job number, history and logged work are not editable — only the details."
            : "The deadline is set automatically from the priority you choose."
        }
        actions={
          <Link className="btn-link" to={editing ? `/work-orders/${workOrderId}` : "/work-orders"}>
            Cancel
          </Link>
        }
      />

      {action.error ? <ErrorBanner message={action.error} onDismiss={action.clearError} /> : null}
      {sites.error ? <ErrorBanner message={sites.error} /> : null}

      <section className="card form-card">
        <form
          onSubmit={(event) => {
            event.preventDefault();
            void submit();
          }}
        >
          <Field
            label="What is wrong?"
            htmlFor="wo-title"
            error={invalid.title}
            hint="One line, as it will appear in the queue."
          >
            <input
              id="wo-title"
              type="text"
              maxLength={200}
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              placeholder="e.g. Chiller unit 3 losing pressure overnight"
              required
            />
          </Field>

          <Field
            label="Details"
            htmlFor="wo-description"
            error={invalid.description}
            hint="Anything the engineer should know before travelling."
          >
            <textarea
              id="wo-description"
              rows={4}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="Access, symptoms, what has already been tried…"
            />
          </Field>

          <div className="form-row">
            <Field
              label="Priority"
              htmlFor="wo-priority"
              error={invalid.priority}
              hint={`Response window: ${SLA_WINDOWS[priority]}`}
            >
              <EnumSelect
                id="wo-priority"
                value={priority}
                options={PRIORITIES}
                label={priorityLabel}
                onChange={(value) => {
                  if (value !== "") {
                    setPriority(value);
                  }
                }}
              />
            </Field>

            <Field
              label="Site"
              htmlFor="wo-site"
              error={invalid.siteId}
              hint={
                editing
                  ? "A job can be moved between sites of the same customer only."
                  : "The customer is taken from the site."
              }
            >
              <select
                id="wo-site"
                value={siteId}
                onChange={(event) => setSiteId(event.target.value)}
                required
              >
                <option value="">Choose a site…</option>
                {(sites.data ?? []).map((site) => (
                  <option key={site.id} value={String(site.id)}>
                    {site.customerName} — {site.name}
                  </option>
                ))}
              </select>
            </Field>
          </div>

          {!editing && canAssignOnCreate ? (
            <Field
              label="Assign now (optional)"
              htmlFor="wo-assignee"
              error={invalid.assigneeId}
              hint="Leave blank to send it to the unassigned queue."
            >
              <select
                id="wo-assignee"
                value={assigneeId}
                onChange={(event) => setAssigneeId(event.target.value)}
              >
                <option value="">Leave unassigned</option>
                {(technicians.data ?? []).map((tech) => (
                  <option key={tech.id} value={String(tech.id)}>
                    {tech.fullName}
                  </option>
                ))}
              </select>
            </Field>
          ) : null}

          <div className="button-row">
            <button type="submit" className="btn-primary inline" disabled={action.busy || !ready}>
              {action.busy ? "Saving…" : editing ? "Save changes" : "Raise job"}
            </button>
            <Link
              className="btn-ghost"
              to={editing ? `/work-orders/${workOrderId}` : "/work-orders"}
            >
              Discard
            </Link>
          </div>
        </form>
      </section>
    </>
  );
}
