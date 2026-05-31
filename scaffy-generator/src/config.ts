import type { GeneratorConfig } from './types.js'

export function readConfig(): GeneratorConfig {
  return {
    databaseUrl: env('DATABASE_URL', 'postgresql://scaffy:scaffy@localhost:5432/scaffy'),
    redisUrl: env('REDIS_URL', 'redis://localhost:6379'),
    queueName: env('SCAFFY_INIT_QUEUE_NAME', 'scaffy:init-jobs'),
    s3Endpoint: optionalEnv('SCAFFY_INIT_STORAGE_ENDPOINT'),
    s3Region: env('SCAFFY_INIT_STORAGE_REGION', 'us-east-1'),
    s3Bucket: env('SCAFFY_INIT_STORAGE_BUCKET', 'scaffy-initializer'),
    s3AccessKey: env('SCAFFY_INIT_STORAGE_ACCESS_KEY', 'scaffy'),
    s3SecretKey: env('SCAFFY_INIT_STORAGE_SECRET_KEY', 'scaffy-secret'),
    s3PathStyle: env('SCAFFY_INIT_STORAGE_PATH_STYLE', 'true') !== 'false',
    jobTimeoutMs: Number(env('SCAFFY_GENERATOR_JOB_TIMEOUT_MS', '300000')),
    heartbeatIntervalMs: Number(env('SCAFFY_GENERATOR_HEARTBEAT_INTERVAL_MS', '10000')),
    retryBackoffBaseMs: Number(env('SCAFFY_GENERATOR_RETRY_BACKOFF_BASE_MS', '15000')),
    mode: env('SCAFFY_GENERATOR_MODE', 'runtime') === 'fixture' ? 'fixture' : 'runtime',
  }
}

function env(name: string, fallback: string): string {
  return process.env[name] || fallback
}

function optionalEnv(name: string): string | undefined {
  const value = process.env[name]
  return value && value.trim() ? value : undefined
}
