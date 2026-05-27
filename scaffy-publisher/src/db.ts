import pg from 'pg'
import { randomUUID } from 'node:crypto'
import type { OAuthToken, PublicationJob } from './types.js'

const { Pool } = pg

export class PublicationStore {
  private readonly pool: pg.Pool

  constructor(databaseUrl: string) {
    this.pool = new Pool({ connectionString: databaseUrl })
  }

  async close(): Promise<void> {
    await this.pool.end()
  }

  async getJob(id: string): Promise<PublicationJob | null> {
    const result = await this.pool.query(
      `
      SELECT
        pj.id,
        pj.user_id,
        pj.initializer_generation_job_id,
        pj.repository_name,
        pj.repository_description,
        pj.status,
        ij.artifact_object_key
      FROM repository_publication_jobs pj
      JOIN initializer_generation_jobs ij ON ij.id = pj.initializer_generation_job_id
      WHERE pj.id = $1
      `,
      [id],
    )
    const row = result.rows[0]
    if (!row) return null
    return {
      id: row.id,
      userId: row.user_id,
      initJobId: row.initializer_generation_job_id,
      repositoryName: row.repository_name,
      repositoryDescription: row.repository_description,
      status: row.status,
      artifactObjectKey: row.artifact_object_key,
    }
  }

  async claim(id: string): Promise<boolean> {
    const result = await this.pool.query(
      `
      UPDATE repository_publication_jobs
      SET status = 'running',
          progress_message = 'Publisher claimed the job',
          started_at = COALESCE(started_at, CURRENT_TIMESTAMP)
      WHERE id = $1 AND status = 'queued'
      `,
      [id],
    )
    return result.rowCount === 1
  }

  async getGitHubToken(userId: string): Promise<OAuthToken | null> {
    const result = await this.pool.query(
      `
      SELECT access_token_encrypted, scopes
      FROM oauth_accounts
      WHERE user_id = $1 AND provider = 'github' AND access_token_encrypted IS NOT NULL
      ORDER BY updated_at DESC
      LIMIT 1
      `,
      [userId],
    )
    const row = result.rows[0]
    if (!row) return null
    return {
      encryptedAccessToken: row.access_token_encrypted,
      scopes: row.scopes,
    }
  }

  async progress(id: string, message: string): Promise<void> {
    await this.pool.query('BEGIN')
    try {
      await this.pool.query(
        `
        UPDATE repository_publication_jobs
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
        INSERT INTO repository_publication_job_logs (job_id, stream, message)
        VALUES ($1, $2, $3)
        `,
        [id, stream, line.slice(0, 4000)],
      )
    }
  }

  async succeed(id: string, userId: string, owner: string, name: string, url: string): Promise<void> {
    await this.pool.query('BEGIN')
    try {
      const connectionId = await this.upsertConnection(userId, owner, name, url)
      await this.pool.query(
        `
        UPDATE repository_publication_jobs
        SET status = 'succeeded',
            progress_message = 'Repository published',
            repository_owner = $2,
            repository_url = $3,
            repository_connection_id = $4,
            completed_at = CURRENT_TIMESTAMP
        WHERE id = $1
        `,
        [id, owner, url, connectionId],
      )
      await this.appendLog(id, 'system', `Repository published: ${owner}/${name}`)
      await this.pool.query('COMMIT')
    } catch (error) {
      await this.pool.query('ROLLBACK')
      throw error
    }
  }

  async fail(id: string, message: string): Promise<void> {
    await this.pool.query('BEGIN')
    try {
      await this.pool.query(
        `
        UPDATE repository_publication_jobs
        SET status = 'failed',
            progress_message = 'Publication failed',
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

  private async upsertConnection(userId: string, owner: string, name: string, url: string): Promise<string> {
    const existing = await this.pool.query(
      `
      SELECT id
      FROM repository_connections
      WHERE user_id = $1 AND provider = 'github' AND repository_owner = $2 AND repository_name = $3
      `,
      [userId, owner, name],
    )
    if (existing.rows[0]) {
      await this.pool.query(
        `
        UPDATE repository_connections
        SET repository_url = $2, updated_at = CURRENT_TIMESTAMP
        WHERE id = $1
        `,
        [existing.rows[0].id, url],
      )
      return existing.rows[0].id
    }

    const inserted = await this.pool.query(
      `
      INSERT INTO repository_connections (
        id,
        user_id,
        provider,
        repository_owner,
        repository_name,
        repository_url
      )
      VALUES ($1, $2, 'github', $3, $4, $5)
      RETURNING id
      `,
      [randomUUID(), userId, owner, name, url],
    )
    return inserted.rows[0].id
  }
}
