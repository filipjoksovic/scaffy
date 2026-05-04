import { Badge } from './Badge'

type StatusRow = {
  service: string
  status: string
  target: string
}

type Metric = {
  label: string
  value: string
}

type StatusConsoleProps = {
  label: string
  metrics: Metric[]
  note: string
  noteCode: string
  rows: StatusRow[]
  statusLabel: string
  title: string
}

const sidebarItems = ['Overview', 'Services', 'Deployments', 'Logs']

export function StatusConsole({
  label,
  metrics,
  note,
  noteCode,
  rows,
  statusLabel,
  title,
}: StatusConsoleProps) {
  return (
    <div className="status-console" aria-label="Scaffy operations console">
      <header className="status-console__bar">
        <span>{label}</span>
        <span>production preview</span>
      </header>
      <div className="status-console__grid">
        <aside className="status-console__sidebar">
          {sidebarItems.map((item, index) => (
            <span
              className={[
                'status-console__sidebar-item',
                index === 0 && 'status-console__sidebar-item--active',
              ]
                .filter(Boolean)
                .join(' ')}
              key={item}
            >
              {item}
            </span>
          ))}
        </aside>

        <section className="status-console__main">
          <div className="status-console__title">
            <span>{title}</span>
            <Badge>{statusLabel}</Badge>
          </div>
          <div className="status-console__metrics">
            {metrics.map((metric) => (
              <div className="status-console__metric" key={metric.label}>
                <span className="status-console__metric-value">{metric.value}</span>
                <span className="status-console__metric-label">{metric.label}</span>
              </div>
            ))}
          </div>
          <table className="status-table">
            <thead>
              <tr>
                <th>Service</th>
                <th>Target</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.service}>
                  <td>{row.service}</td>
                  <td>{row.target}</td>
                  <td>
                    <span className="status-dot" aria-hidden="true" />
                    {row.status}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        <aside className="status-console__note">
          <span className="status-console__note-label">Agent check</span>
          <p>{note}</p>
          <code>{noteCode}</code>
        </aside>
      </div>
    </div>
  )
}
