export function ErrorMessage({ error, className = "" }) {
  if (!error) return null;
  return <div className={`alert alert-danger ${className}`} role="alert">{error}</div>;
}

export function LoadingState({ label = "Loading…" }) {
  return (
    <div className="page-state" role="status">
      <span className="spinner-border spinner-border-sm" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function EmptyState({ title, children, action }) {
  return (
    <div className="empty-state">
      <span className="empty-mark" aria-hidden="true">✳</span>
      <h2>{title}</h2>
      {children && <p>{children}</p>}
      {action}
    </div>
  );
}

export function errorMessage(error) {
  const response = error?.response?.data;
  if (response?.fieldErrors && Object.keys(response.fieldErrors).length) {
    return Object.values(response.fieldErrors).join(" ");
  }
  return response?.message || error?.message || "Something went wrong. Please try again.";
}
