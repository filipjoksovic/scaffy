export function formatRelativeTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  const diffMs = date.getTime() - Date.now()
  const diffSec = Math.round(diffMs / 1000)
  const abs = Math.abs(diffSec)

  if (abs < 60) return 'just now'
  const minutes = Math.round(diffSec / 60)
  if (Math.abs(minutes) < 60) return formatChunk(minutes, 'minute')
  const hours = Math.round(minutes / 60)
  if (Math.abs(hours) < 24) return formatChunk(hours, 'hour')
  const days = Math.round(hours / 24)
  if (Math.abs(days) < 30) return formatChunk(days, 'day')
  const months = Math.round(days / 30)
  if (Math.abs(months) < 12) return formatChunk(months, 'month')
  const years = Math.round(days / 365)
  return formatChunk(years, 'year')
}

function formatChunk(value: number, unit: string): string {
  const n = Math.abs(value)
  const plural = n === 1 ? unit : `${unit}s`
  return value < 0 ? `${n} ${plural} ago` : `in ${n} ${plural}`
}
