import { Redis } from 'ioredis'

export class JobQueue {
  private readonly redis: Redis
  private closed = false

  constructor(
    redisUrl: string,
    private readonly queueName: string,
  ) {
    this.redis = new Redis(redisUrl, { maxRetriesPerRequest: null })
  }

  async close(): Promise<void> {
    this.closed = true
    this.redis.disconnect()
  }

  async nextJobId(): Promise<string | null> {
    if (this.closed) return null
    const result = await this.redis.blpop(this.queueName, 0).catch((error) => {
      if (this.closed) return null
      throw error
    })
    if (result === null) return null
    if (!result) throw new Error('Redis returned no job id')
    return result[1]
  }
}
