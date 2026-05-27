import type { PublisherConfig } from './types.js'

export function readConfig(): PublisherConfig {
  return {
    databaseUrl: env('DATABASE_URL', 'postgresql://scaffy:scaffy@localhost:5432/scaffy'),
    redisUrl: env('REDIS_URL', 'redis://localhost:6379'),
    queueName: env('SCAFFY_REPOSITORY_PUBLICATION_QUEUE_NAME', 'scaffy:repo-publication-jobs'),
    s3Endpoint: optionalEnv('SCAFFY_INIT_STORAGE_ENDPOINT'),
    s3Region: env('SCAFFY_INIT_STORAGE_REGION', 'us-east-1'),
    s3Bucket: env('SCAFFY_INIT_STORAGE_BUCKET', 'scaffy-initializer'),
    s3AccessKey: env('SCAFFY_INIT_STORAGE_ACCESS_KEY', 'scaffy'),
    s3SecretKey: env('SCAFFY_INIT_STORAGE_SECRET_KEY', 'scaffy-secret'),
    s3PathStyle: env('SCAFFY_INIT_STORAGE_PATH_STYLE', 'true') !== 'false',
    providerTokenEncryptionSecret: env(
      'SCAFFY_PROVIDER_TOKEN_ENCRYPTION_SECRET',
      'dev-provider-token-secret-change-me',
    ),
    githubApiUrl: env('GITHUB_API_URL', 'https://api.github.com').replace(/\/$/, ''),
  }
}

function env(name: string, fallback: string): string {
  return process.env[name] || fallback
}

function optionalEnv(name: string): string | undefined {
  const value = process.env[name]
  return value && value.trim() ? value : undefined
}
