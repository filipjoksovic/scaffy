import { readConfig } from './config.js'
import { JobStore } from './db.js'
import { generateZip } from './generator.js'
import { JobQueue } from './queue.js'
import { ArtifactStorage } from './storage.js'

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

  try {
    console.log(`[generator] started job ${jobId} project=${job.projectName}`)
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
    await store.succeed(jobId, objectKey)
    console.log(`[generator] job=${jobId} succeeded`)
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    console.error(`[generator] job=${jobId} failed: ${message}`, error)
    await store.fail(jobId, message)
  }
}
