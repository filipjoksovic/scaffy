import pg from 'pg'
import type { InitGenerationJob, InitJobRequest, InitSelection } from './types.js'

const { Pool } = pg

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
      SELECT id, status, project_name, request_json, selection_json
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
    }
  }

  async claim(id: string): Promise<boolean> {
    const result = await this.pool.query(
      `
      UPDATE initializer_generation_jobs
      SET status = 'running',
          progress_message = 'Generator claimed the job',
          started_at = COALESCE(started_at, CURRENT_TIMESTAMP)
      WHERE id = $1 AND status = 'queued'
      `,
      [id],
    )
    return result.rowCount === 1
  }

  async progress(id: string, message: string): Promise<void> {
    await this.pool.query('BEGIN')
    try {
      await this.pool.query(
        `
        UPDATE initializer_generation_jobs
        SET progress_message = $2
        WHERE id = $1 AND status = 'running'
        `,
        [id, message],
      )
      await this.appendLog(id, 'system', message)
      await this.pool.query('COMMIT')
    } catch (error) {
      await this.pool.query('ROLLBACK')
      throw error
    }
  }

  async appendLog(id: string, stream: 'system' | 'stdout' | 'stderr', message: string): Promise<void> {
    const lines = message
      .split(/\r?\n/)
      .map((line) => line.trimEnd())
      .filter((line) => line.length > 0)
      .slice(-200)

    for (const line of lines) {
      await this.pool.query(
        `
        INSERT INTO initializer_generation_job_logs (job_id, stream, message)
        VALUES ($1, $2, $3)
        `,
        [id, stream, line.slice(0, 4000)],
      )
    }
  }

  async succeed(id: string, objectKey: string): Promise<void> {
    await this.pool.query(
      `
      UPDATE initializer_generation_jobs
      SET status = 'succeeded',
          progress_message = 'Artifact uploaded',
          artifact_object_key = $2,
          completed_at = CURRENT_TIMESTAMP
      WHERE id = $1
      `,
      [id, objectKey],
    )
  }

  async fail(id: string, message: string): Promise<void> {
    await this.pool.query('BEGIN')
    try {
      await this.pool.query(
        `
        UPDATE initializer_generation_jobs
        SET status = 'failed',
            progress_message = 'Generation failed',
            error_message = $2,
            completed_at = CURRENT_TIMESTAMP
        WHERE id = $1
        `,
        [id, message.slice(0, 4000)],
      )
      await this.appendLog(id, 'stderr', message)
      await this.pool.query('COMMIT')
    } catch (error) {
      await this.pool.query('ROLLBACK')
      throw error
    }
  }
}
