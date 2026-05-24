import type {
  CapabilityFinding,
  CapabilityScore,
  DimensionAnalysis,
} from '../api/analyze'

const ACCEPTED_EXTENSIONS = ['.yml', '.yaml']

export function collectFindings(
  dimension: DimensionAnalysis,
  type: CapabilityFinding['type'],
): CapabilityFinding[] {
  return dimension.capabilityScores.flatMap((capability: CapabilityScore) =>
    capability.findings.filter((finding) => finding.type === type),
  )
}

export function countIssues(dimension: DimensionAnalysis): number {
  return (
    collectFindings(dimension, 'SMELL').length
    + collectFindings(dimension, 'MISSING').length
  )
}

export function findingKey(finding: CapabilityFinding): string {
  return `${finding.ruleId}-${finding.location ?? ''}-${finding.evidence ?? ''}`
}

export function validateFile(file: File | null): string | null {
  if (!file) return null
  const normalizedName = file.name.toLowerCase()
  if (!ACCEPTED_EXTENSIONS.some((extension) => normalizedName.endsWith(extension))) {
    return 'Upload a .yml or .yaml pipeline file.'
  }
  return null
}

export function formatScore(score: number): string {
  return `${Math.round(score * 100)}%`
}

export function formatFileSize(size: number): string {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${Math.round(size / 1024)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

export function formatProvider(provider: string): string {
  if (provider === 'github-actions') return 'GitHub Actions'
  if (provider === 'gitlab-ci') return 'GitLab CI'
  return formatLabel(provider)
}

export function formatDimension(dimension: string): string {
  return formatLabel(dimension)
}

export function formatLabel(value: string): string {
  return value
    .split(/[-_\s]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

export function dimensionSummary(dimension: DimensionAnalysis): string {
  if (dimension.status === 'not_evaluated') {
    return 'Not evaluated'
  }
  const issues = countIssues(dimension)
  return `${issues} issue${issues === 1 ? '' : 's'}`
}

export function statusBadgeClassName(status: string): string | undefined {
  return status === 'not_evaluated' ? 'badge badge--not-evaluated' : undefined
}
