import type { InitJobRequest, InitSelection } from './types.js'

type RuntimePreset = {
  id: string
  label: string
  runtime: string
  version: string
}

type VersionPreset = {
  id: string
  label: string
  version: string
  defaultRuntimeId: string
  runtimes: RuntimePreset[]
}

type StackOption = {
  id: string
  name: string
  versions: VersionPreset[]
}

type PipelineOption = {
  id: string
  name: string
}

type MaturityPreset = {
  id: string
  label: string
  description: string
  level: number
  dockerRequired: boolean
}

const node = (version: string): RuntimePreset => ({
  id: `node-${version}`,
  label: `Node ${version}`,
  runtime: 'node',
  version,
})

const java = (version: string): RuntimePreset => ({
  id: `java-${version}`,
  label: `Java ${version}`,
  runtime: 'java',
  version,
})

const dotnet = (version: string): RuntimePreset => ({
  id: `dotnet-${version}`,
  label: `.NET ${version}`,
  runtime: 'dotnet',
  version,
})

const version = (
  id: string,
  label: string,
  frameworkVersion: string,
  defaultRuntimeId: string,
  runtimes: RuntimePreset[],
): VersionPreset => ({
  id,
  label,
  version: frameworkVersion,
  defaultRuntimeId,
  runtimes,
})

const frontends: StackOption[] = [
  {
    id: 'react',
    name: 'React',
    versions: [
      version('18', 'React 18', '18', 'node-20', [node('20'), node('22'), node('24')]),
      version('19', 'React 19', '19', 'node-22', [node('20'), node('22'), node('24')]),
    ],
  },
  {
    id: 'vue',
    name: 'Vue',
    versions: [version('3', 'Vue 3.x', '3.x', 'node-22', [node('20'), node('22'), node('24')])],
  },
  {
    id: 'angular',
    name: 'Angular',
    versions: [
      version('18', 'Angular 18', '18', 'node-20', [node('20'), node('22')]),
      version('19', 'Angular 19', '19', 'node-22', [node('20'), node('22')]),
      version('20', 'Angular 20', '20', 'node-22', [node('20'), node('22'), node('24')]),
    ],
  },
]

const backends: StackOption[] = [
  {
    id: 'spring-boot',
    name: 'Spring Boot',
    versions: [
      version('3.5', 'Spring Boot 3.5', '3.5', 'java-21', [java('17'), java('21')]),
      version('4.0', 'Spring Boot 4.0', '4.0', 'java-21', [java('21'), java('25')]),
    ],
  },
  {
    id: 'nestjs',
    name: 'NestJS',
    versions: [
      version('10', 'NestJS 10', '10', 'node-22', [node('20'), node('22')]),
      version('11', 'NestJS 11', '11', 'node-22', [node('22'), node('24')]),
    ],
  },
  {
    id: 'dotnet',
    name: '.NET',
    versions: [
      version('8', '.NET 8', '8', 'dotnet-8', [dotnet('8')]),
      version('9', '.NET 9', '9', 'dotnet-9', [dotnet('9')]),
      version('10', '.NET 10', '10', 'dotnet-10', [dotnet('10')]),
    ],
  },
]

const pipelines: PipelineOption[] = [
  { id: 'github-actions', name: 'GitHub Actions' },
  { id: 'gitlab-ci', name: 'GitLab CI' },
]

const maturityPresets: MaturityPreset[] = [
  {
    id: 'l1',
    label: 'L1 Minimal',
    description: 'Build validation for prototypes.',
    level: 1,
    dockerRequired: false,
  },
  {
    id: 'l2',
    label: 'L2 Basic CI',
    description: 'Build, test, deterministic installs, and artifacts.',
    level: 2,
    dockerRequired: false,
  },
  {
    id: 'l3',
    label: 'L3 Structured Delivery',
    description: 'Adds Docker, compose, cache, image validation, and versioned artifacts.',
    level: 3,
    dockerRequired: true,
  },
  {
    id: 'l4',
    label: 'L4 Governed Automation',
    description: 'Adds security scanning and gated deployment placeholders.',
    level: 4,
    dockerRequired: true,
  },
]

export function validateSelection(request: InitJobRequest): InitSelection {
  const frontend = selectedStack(frontends, request.frontend, request.frontendVersion, request.frontendRuntime)
  const backend = selectedStack(backends, request.backend, request.backendVersion, request.backendRuntime)
  const pipeline = pipelines.find((item) => item.id === request.pipeline)
  if (!pipeline) throw new Error(`Unsupported pipeline preset: ${request.pipeline}`)
  const pipelineMaturity = maturityPresets.find((item) => item.id === (request.pipelineMaturity ?? 'l2'))
  if (!pipelineMaturity) throw new Error(`Unsupported pipeline maturity preset: ${request.pipelineMaturity}`)

  return {
    frontend,
    backend,
    pipeline,
    pipelineMaturity,
    includeDocker: Boolean(request.includeDocker) || pipelineMaturity.dockerRequired,
  }
}

function selectedStack(
  options: StackOption[],
  stackId: string,
  versionId: string,
  runtimeId: string,
): InitSelection['frontend'] {
  const stack = options.find((item) => item.id === stackId)
  if (!stack) throw new Error(`Unsupported stack preset: ${stackId}`)
  const preset = stack.versions.find((item) => item.id === versionId)
  if (!preset) throw new Error(`Unsupported version preset ${versionId} for ${stack.name}`)
  const runtime = preset.runtimes.find((item) => item.id === runtimeId)
  if (!runtime) throw new Error(`Unsupported runtime preset ${runtimeId} for ${preset.label}`)

  return {
    id: stack.id,
    name: stack.name,
    versionId: preset.id,
    versionLabel: preset.label,
    version: preset.version,
    runtimeId: runtime.id,
    runtimeLabel: runtime.label,
    runtime: runtime.runtime,
    runtimeVersion: runtime.version,
  }
}
