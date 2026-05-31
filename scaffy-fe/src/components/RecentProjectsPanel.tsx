import type { InitHistoryItem } from "../api/init";
import { Card } from "./Card";
import { StateRow } from "./StateRow";

type Props = Readonly<{
  error: string | null;
  items: InitHistoryItem[];
  loading: boolean;
}>;

export function RecentProjectsPanel({ error, items, loading }: Props) {
  let content;

  if (loading) {
    content = (
      <StateRow
        detail="Loading generated projects from /api/init/history."
        label="Loading recent projects"
        tone="loading"
      />
    );
  } else if (error) {
    content = (
      <StateRow detail={error} label="Could not load recent projects" tone="error" />
    );
  } else if (items.length === 0) {
    content = (
      <div className="empty-state empty-state--compact">
        <h4>No generated projects yet</h4>
        <p>Create a project to keep its stack in your recent history.</p>
      </div>
    );
  } else {
    content = (
      <ul className="recent-projects__list">
        {items.map((item) => (
          <li key={item.jobId} className="recent-projects__item">
            <div className="recent-projects__copy">
              <strong>{item.projectName}</strong>
              <span>
                {item.stack.frontend} · {item.stack.backend} ·{" "}
                {item.stack.pipeline}
              </span>
            </div>
            <div className="recent-projects__meta">
              <span className={`recent-projects__status recent-projects__status--${item.status}`}>
                {item.status}
              </span>
              <time dateTime={item.createdAt}>{formatRecentDate(item.createdAt)}</time>
            </div>
          </li>
        ))}
      </ul>
    );
  }

  return (
    <Card as="section" className="recent-projects" aria-label="Recent projects">
      <div className="recent-projects__header">
        <div>
          <h3>Recent projects</h3>
          <p>Generated initializer jobs from this account.</p>
        </div>
        <span className="recent-projects__count">{items.length}</span>
      </div>

      {content}
    </Card>
  );
}

function formatRecentDate(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "-";
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}
