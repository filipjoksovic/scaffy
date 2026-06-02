export function formatDuration(seconds: number): string {
  if (seconds < 60) return `${seconds}s`
  const min = Math.floor(seconds / 60)
  const sec = seconds % 60
  return sec === 0 ? `${min}m` : `${min}m ${sec}s`
}

export function successRateColor(rate: number): string {
  if (rate >= 0.9) return 'var(--color-success)'
  if (rate >= 0.7) return 'var(--color-body)'
  return 'var(--color-error)'
}
