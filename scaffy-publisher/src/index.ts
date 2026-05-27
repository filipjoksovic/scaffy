import { readConfig } from './config.js'
import { decryptProviderToken } from './crypto.js'
import { PublicationStore } from './db.js'
import { GitHubPublisher } from './github.js'
import { JobQueue } from './queue.js'
import { ArtifactStorage } from './storage.js'
import { extractZipFiles } from './zip.js'

const config = readConfig()
const queue = new JobQueue(config.redisUrl, config.queueName)
const store = new PublicationStore(config.databaseUrl)
const storage = new ArtifactStorage(config)
const github = new GitHubPublisher(config)
let shuttingDown = false

console.log(
  `[publisher] started queue=${config.queueName} redis=${config.redisUrl} bucket=${config.s3Bucket}`,
)

process.once('SIGINT', () => {
  void shutdown('SIGINT')
})
process.once('SIGTERM', () => {
  void shutdown('SIGTERM')
})

while (!shuttingDown) {
  console.log(`[publisher] waiting for jobs on ${config.queueName}`)
  const jobId = await queue.nextJobId()
  if (!jobId || shuttingDown) break
  console.log(`[publisher] dequeued job ${jobId}`)
  await handleJob(jobId).catch((error) => {
    console.error(`[publisher] unhandled failure for job ${jobId}`, error)
  })
}

await shutdown('loop-exit')

async function handleJob(jobId: string): Promise<void> {
  const claimed = await store.claim(jobId)
  if (!claimed) {
    console.log(`[publisher] job=${jobId} was already claimed or completed`)
    return
  }

  try {
    const job = await store.getJob(jobId)
    if (!job) {
      throw new Error('Publication job no longer exists.')
    }
    if (!job.artifactObjectKey) {
      throw new Error('Generated artifact is not available.')
    }

    console.log(`[publisher] job=${jobId} publishing repository=${job.repositoryName}`)
    await store.progress(jobId, 'Loading GitHub token')
    const tokenRecord = await store.getGitHubToken(job.userId)
    if (!tokenRecord) {
      throw new Error('Reconnect with GitHub before creating repositories.')
    }

    const scopes = (tokenRecord.scopes ?? '').split(/[,\s]+/)
    if (!scopes.includes('repo') || !scopes.includes('workflow')) {
      throw new Error('Reconnect GitHub with repository and workflow access before creating repositories.')
    }

    const token = decryptProviderToken(
      tokenRecord.encryptedAccessToken,
      config.providerTokenEncryptionSecret,
    )

    await store.progress(jobId, 'Downloading generated artifact')
    const zipBytes = await storage.downloadZip(job.artifactObjectKey)
    const files = extractZipFiles(zipBytes)
    if (files.length === 0) {
      throw new Error('Generated artifact does not contain any files.')
    }
    await store.progress(jobId, `Preparing ${files.length} files for GitHub`)

    const published = await github.publishRepository(
      token,
      job.repositoryName,
      job.repositoryDescription,
      files,
    )
    await store.succeed(jobId, job.userId, published.owner, published.name, published.url)
    console.log(`[publisher] job=${jobId} succeeded url=${published.url}`)
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Publication failed.'
    console.error(`[publisher] job=${jobId} failed: ${message}`)
    await store.fail(jobId, message)
  }
}

async function shutdown(signal: string): Promise<void> {
  if (shuttingDown && signal !== 'loop-exit') return
  shuttingDown = true
  console.log(`[publisher] received ${signal}, shutting down`)
  await queue.close().catch(() => undefined)
  await store.close().catch(() => undefined)
}
