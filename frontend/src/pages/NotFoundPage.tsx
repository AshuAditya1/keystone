import { Link } from "react-router-dom";

export function NotFoundPage() {
  return (
    <div className="card">
      <h2>Page not found</h2>
      <p className="muted">
        That address does not match anything in the application. It may have been a link from an
        older build, or a screen your role does not have.
      </p>
      <Link className="btn-ghost" to="/">
        Back to start
      </Link>
    </div>
  );
}
