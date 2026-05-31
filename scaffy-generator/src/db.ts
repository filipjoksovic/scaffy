import pg from 'pg'
import type { InitGenerationJob, InitJobRequest, InitSelection } from './types.js'

const { Pool } = pg

type Executor = Pick<pg.PoolClient, 'query'>

export type FailOptions = {
  retryable: boolean
  attemptCount: number
  maxAttempts: number
  backoffBaseMs: number
}

export class JobStore {
  private readonly pool: pg.Pool

  constructor(databaseUrl: string) {
    this.pool = new Pool({ connectionString: databaseUrl })
  }

  async close(): Promise<void> {
    await this.pool.end()
  }

  async getJob(id: string): Promise<InitGenerationJob | null> {
    const result = await this.pool.query(
      `
      SELECT id, status, project_name, request_json, selection_json, attempt_count, max_attempts
      FROM initializer_generation_jobs
      WHERE id = $1
      `,
      [id],
    )
    const row = result.rows[0]
    if (!row) return null
    return {
      id: row.id,
      status: row.status,
      projectName: row.project_name,
      request: JSON.parse(row.request_json) as InitJobRequest,
      selection: JSON.parse(row.selection_json) as InitSelection,
      attemptCount: row.attempt_count,
      maxAttempts: row.max_attempts,
    }
  }

  async claim(id: string): Promise<boolean> {
    const result = await this.pool.query(
      `
      UPDATE initializer_generation_jobs
      SET status = 'running',
          progress_message = 'Generator claimed the job',
          attempt_count = attempt_count + 1,
          heartbeat_at = CURRENT_TIMESTAMP,
          next_attempt_at = NULL,
          started_at = COALESCE(started_at, CURRENT_TIMESTAMP)
      WHERE id = $1 AND status = 'queued'
      `,
      [id],
    )
    return result.rowCount === 1
  }

  async heartbeat(id: string): Promise<void> {
    await this.pool.query(
      `
      UPDATE initializer_generation_jobs
      SET heartbeat_at = CURRENT_TIMESTAMP
      WHERE id = $1 AND status = 'running'
      `,
      [id],
    )
  }

  async progress(id: string, message: string): Promise<void> {
    await this.transaction(async (client) => {
      await client.query(
        `
        UPDATE initializer_generation_jobs
        SET progress_message = $2,
            heartbeat_at = CURRENT_TIMESTAMP
        WHERE id = $1 AND status = 'running'
        `,
        [id, message],
      )
      await this.appendLogVia(client, id, 'system', message)
    })
  }

  async appendLog(id: string, stream: 'system' | 'stdout' | 'stderr', message: string): Promise<void> {
    await this.appendLogVia(this.pool, id, stream, message)
  }

  async succeed(id: string, objectKey: string): Promise<void> {
    await this.pool.query(
      `
      UPDATE initializer_generation_jobs
      SET status = 'succeeded',
          progress_message = 'Artifact uploaded',
          artifact_object_key = $2,
          error_message = NULL,
          next_attempt_at = NULL,
          completed_at = CURRENT_TIMESTAMP
      WHERE id = $1
      `,
      [id, objectKey],
    )
  }

  async fail(id: string, message: string, options: FailOptions): Promise<void> {
    const trimmed = message.slice(0, 4000)
    const willRetry = options.retryable && options.attemptCount < options.maxAttempts
    await this.transaction(async (client) => {
      if (willRetry) {
        const backoffMs = options.backoffBaseMs * 2 ** (options.attemptCount - 1)
        const progress = `Retry scheduled (attempt ${options.attemptCount}/${options.maxAttempts})`
        await client.query(
          `
          UPDATE initializer_generation_jobs
          SET status = 'queued',
              progress_message = $2,
              error_message = $3,
              heartbeat_at = NULL,
              next_attempt_at = CURRENT_TIMESTAMP + ($4 || ' milliseconds')::interval
          WHERE id = $1
          `,
          [id, progress, trimmed, String(backoffMs)],
        )
        await this.appendLogVia(client, id, 'system', `${progress} after transient failure: ${trimmed}`)
      } else {
        await client.query(
          `
          UPDATE initializer_generation_jobs
          SET status = 'failed',
              progress_message = 'Generation failed',
              error_message = $2,
              next_attempt_at = NULL,
              completed_at = CURRENT_TIMESTAMP
          WHERE id = $1
          `,
          [id, trimmed],
        )
        await this.appendLogVia(client, id, 'stderr', message)
      }
    })
  }

  private async transaction(work: (client: pg.PoolClient) => Promise<void>): Promise<void> {
    const client = await this.pool.connect()
    try {
      await client.query('BEGIN')
      await work(client)
      await client.query('COMMIT')
    } catch (error) {
      await client.query('ROLLBACK').catch(() => undefined)
      throw error
    } finally {
      client.release()
    }
  }

  private async appendLogVia(
    executor: Executor,
    id: string,
    stream: 'system' | 'stdout' | 'stderr',
    message: string,
  ): Promise<void> {
    const lines = message
      .split(/\r?\n/)
      .map((line) => line.trimEnd())
      .filter((line) => line.length > 0)
      .slice(-200)

    for (const line of lines) {
      await executor.query(
        `
        INSERT INTO initializer_generation_job_logs (job_id, stream, message)
        VALUES ($1, $2, $3)
        `,
        [id, stream, line.slice(0, 4000)],
      )
    }
  }
}
