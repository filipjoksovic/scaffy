import { emptyDir, mkdirp, remove } from 'fs-extra/esm'
import { readFile, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { validateSelection } from './catalog.js'
import * as cache from './cache.js'
import { buildCommandPlan, postProcessProject, runCommand, writeFixtureProject } from './commands.js'
import { zipDirectory } from './zip.js'
import type { CommandLogLine, GeneratorConfig, InitGenerationJob } from './types.js'

export type ProgressReporter = (message: string) => Promise<void>
export type LogReporter = (line: CommandLogLine) => Promise<void>

export async function generateZip(
  config: GeneratorConfig,
  job: InitGenerationJob,
  report: ProgressReporter,
  reportLog?: LogReporter,
): Promise<string> {
  const selection = validateSelection(job.request)

  // Check the cache before doing any filesystem work.
  const cacheKey = cache.requestKey(job.request)
  const cached = cache.get(cacheKey)
  if (cached !== null) {
    await report('Serving from cache')
    const cachedZipPath = path.join(tmpdir(), 'scaffy-generator', job.id, `${job.request.projectName}.zip`)
    await mkdirp(path.dirname(cachedZipPath))
    await writeFile(cachedZipPath, cached)
    return cachedZipPath
  }

  const root = path.join(tmpdir(), 'scaffy-generator', job.id)
  const workspace = path.join(root, job.request.projectName)
  const zipPath = path.join(root, `${job.request.projectName}.zip`)

  await report('Preparing clean workspace')
  await emptyDir(root)
  await mkdirp(workspace)

  try {
    if (config.mode === 'fixture') {
      await report('Writing fixture project')
      await writeFixtureProject(workspace, job.request, selection)
    } else {
      const commands = buildCommandPlan(workspace, job.request, selection)
      for (const [index, command] of commands.entries()) {
        await report(`${command.label} (${index + 1}/${commands.length})`)
        await runCommand(command, reportLog)
        await report(`${command.label} complete (${index + 1}/${commands.length})`)
      }
      await report('Applying Scaffy overlays')
      await postProcessProject(workspace, job.request, selection)
      await report('Scaffy overlays applied')
    }

    await remove(path.join(workspace, '.scaffy'))
    await report('Creating ZIP artifact')
    await zipDirectory(workspace, zipPath)
    await report('ZIP artifact created')

    // Store in cache so identical future requests are served instantly.
    const zipBytes = await readFile(zipPath)
    cache.put(cacheKey, zipBytes)

    return zipPath
  } catch (error) {
    throw error instanceof Error ? error : new Error(String(error))
  } finally {
    if (process.env.SCAFFY_GENERATOR_KEEP_WORKSPACE !== 'true') {
      setTimeout(() => {
        remove(root).catch(() => undefined)
      }, 30000)
    }
  }
}
