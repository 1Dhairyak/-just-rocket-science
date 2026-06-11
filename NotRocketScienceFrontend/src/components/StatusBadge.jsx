/**
 * Compact status badge used in the fleet roster table.
 * Maps backend status strings to coloured pill styles.
 */
export function StatusBadge({ status }) {
  if (!status) return null;
  if (status === "ACTIVE")         return <span className="badge badge-active">Active</span>;
  if (status === "IN_DEVELOPMENT") return <span className="badge badge-dev">In Development</span>;
  return <span className="badge badge-retired">{status}</span>;
}

/**
 * Larger status badge shown at the top of the rocket detail page.
 */
export function RocketStatusBadge({ status }) {
  if (!status) return null;
  if (status === "ACTIVE")         return <span className="rocket-status-badge rsb-active">Active Development</span>;
  if (status === "IN_DEVELOPMENT") return <span className="rocket-status-badge rsb-dev">In Development</span>;
  return <span className="rocket-status-badge rsb-retired">{status}</span>;
}
