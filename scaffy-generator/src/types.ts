export type InitJobStatus = 'queued' | 'running' | 'succeeded' | 'failed'

export type InitJobRequest = {
  projectName: string
  frontend: string
  frontendVersion: string
  frontendRuntime: string
  backend: string
  backendVersion: string
  backendRuntime: string
  pipeline: string
  pipelineMaturity?: string
  includeDocker?: boolean
}

export type InitSelection = {
  frontend: SelectedStack
  backend: SelectedStack
  pipeline: SelectedPipeline
  pipelineMaturity: SelectedMaturity
  includeDocker: boolean
}

export type SelectedStack = {
  id: string
  name: string
  versionId: string
  versionLabel: string
  version: string
  runtimeId: string
  runtimeLabel: string
  runtime: string
  runtimeVersion: string
}

export type SelectedPipeline = {
  id: string
  name: string
}

export type SelectedMaturity = {
  id: string
  label: string
  description: string
  level: number
  dockerRequired: boolean
}

export type InitGenerationJob = {
  id: string
  status: InitJobStatus
  projectName: string
  request: InitJobRequest
  selection: InitSelection
  attemptCount: number
  maxAttempts: number
}

export type CommandSpec = {
  executable: string
  args: string[]
  cwd: string
  env?: Record<string, string>
  timeoutMs: number
  label: string
}

export type CommandLogLine = {
  stream: 'stdout' | 'stderr'
  message: string
}

export type GeneratorConfig = {
  databaseUrl: string
  redisUrl: string
  queueName: string
  s3Endpoint?: string
  s3Region: string
  s3Bucket: string
  s3AccessKey: string
  s3SecretKey: string
  s3PathStyle: boolean
  jobTimeoutMs: number
  heartbeatIntervalMs: number
  retryBackoffBaseMs: number
  mode: 'runtime' | 'fixture'
}
