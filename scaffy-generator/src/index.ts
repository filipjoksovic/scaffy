import { isTransientFailure } from './commands.js'
import { readConfig } from './config.js'
import { JobStore } from './db.js'
import { generateZip } from './generator.js'
import { JobQueue } from './queue.js'
import { ArtifactStorage } from './storage.js'
import type { InitGenerationJob } from './types.js'

const config = readConfig()
const queue = new JobQueue(config.redisUrl, config.queueName)
const store = new JobStore(config.databaseUrl)
const storage = new ArtifactStorage(config)

let shuttingDown = false
let cleanupStarted = false

console.log(
  `[generator] started mode=${config.mode} queue=${config.queueName} redis=${config.redisUrl} bucket=${config.s3Bucket}`,
)

process.once('SIGINT', () => void shutdown('SIGINT'))
process.once('SIGTERM', () => void shutdown('SIGTERM'))

while (!shuttingDown) {
  console.log(`[generator] waiting for jobs on ${config.queueName}`)
  const jobId = await queue.nextJobId()
  if (jobId === null) break
  console.log(`[generator] dequeued job ${jobId}`)
  await handleJob(jobId).catch((error) => {
    console.error(`Unhandled generator failure for job ${jobId}`, error)
  })
}

await closeResources()

async function shutdown(signal: string): Promise<void> {
  console.log(`[generator] received ${signal}, shutting down`)
  shuttingDown = true
  await closeResources()
  process.exit(0)
}

async function closeResources(): Promise<void> {
  if (cleanupStarted) return
  cleanupStarted = true
  await queue.close().catch(() => undefined)
  await store.close().catch(() => undefined)
}

async function handleJob(jobId: string): Promise<void> {
  const claimed = await store.claim(jobId)
  if (!claimed) {
    console.log(`[generator] skipped job ${jobId}; it was not queued anymore`)
    return
  }

  const job = await store.getJob(jobId)
  if (!job) {
    console.warn(`Generator claimed missing job ${jobId}`)
    return
  }

  const heartbeat = setInterval(() => {
    store.heartbeat(jobId).catch((error) => {
      console.warn(`[generator] job=${jobId} heartbeat failed`, error)
    })
  }, config.heartbeatIntervalMs)

  try {
    console.log(`[generator] started job ${jobId} project=${job.projectName} attempt=${job.attemptCount}/${job.maxAttempts}`)
    const objectKey = await withTimeout(
      runGeneration(jobId, job),
      config.jobTimeoutMs,
      `Generation timed out after ${config.jobTimeoutMs}ms`,
    )
    await persist(jobId, 'succeed', () => store.succeed(jobId, objectKey))
    console.log(`[generator] job=${jobId} succeeded`)
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    const retryable = isTransientFailure(message)
    console.error(`[generator] job=${jobId} failed (retryable=${retryable}): ${message}`, error)
    await persist(jobId, 'fail', () =>
      store.fail(jobId, message, {
        retryable,
        attemptCount: job.attemptCount,
        maxAttempts: job.maxAttempts,
        backoffBaseMs: config.retryBackoffBaseMs,
      }),
    )
  } finally {
    clearInterval(heartbeat)
  }
}

async function runGeneration(jobId: string, job: InitGenerationJob): Promise<string> {
  const zipPath = await generateZip(
    config,
    job,
    async (message) => {
      console.log(`[generator] job=${jobId} ${message}`)
      await store.progress(jobId, message)
    },
    async (line) => {
      console.log(`[generator] job=${jobId} ${line.stream}: ${line.message}`)
      await store.appendLog(jobId, line.stream, line.message)
    },
  )
  const objectKey = `initializer/${jobId}/${job.projectName}.zip`
  console.log(`[generator] job=${jobId} uploading artifact key=${objectKey}`)
  await store.progress(jobId, 'Uploading ZIP artifact')
  await storage.uploadZip(objectKey, zipPath)
  return objectKey
}

// A transient DB error must not strand the job; the backend reaper reclaims any
// job whose status write never lands, so a few quick retries are the cheap path.
async function persist(jobId: string, label: string, action: () => Promise<void>): Promise<void> {
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    try {
      await action()
      return
    } catch (error) {
      console.error(`[generator] job=${jobId} ${label} write failed (attempt ${attempt}/3)`, error)
      if (attempt < 3) await delay(500 * attempt)
    }
  }
  console.error(`[generator] job=${jobId} ${label} write gave up; leaving recovery to the reaper`)
}

async function withTimeout<T>(promise: Promise<T>, ms: number, message: string): Promise<T> {
  let timer: NodeJS.Timeout | undefined
  const timeout = new Promise<never>((_, reject) => {
    timer = setTimeout(() => reject(new Error(message)), ms)
  })
  try {
    return await Promise.race([promise, timeout])
  } finally {
    if (timer) clearTimeout(timer)
  }
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
