import archiver from 'archiver'
import { createWriteStream } from 'node:fs'

// Dependency and build-output directories that must never end up in the
// published artifact. They are regenerated locally and are conventionally
// gitignored; the matching .gitignore files produced by the underlying
// scaffolders are still included so the published repo stays correct.
const IGNORED_DIRECTORIES = new Set([
  'node_modules',
  'dist',
  'build',
  'out',
  'coverage',
  '.next',
  '.nuxt',
  '.svelte-kit',
  '.angular',
  '.turbo',
  '.cache',
  'bin',
  'obj',
  'target',
  '.gradle',
])

export async function zipDirectory(sourceDir: string, outputPath: string): Promise<void> {
  await new Promise<void>((resolve, reject) => {
    const output = createWriteStream(outputPath)
    const archive = archiver('zip', { zlib: { level: 9 } })

    output.on('close', () => resolve())
    archive.on('error', reject)
    archive.pipe(output)
    archive.directory(sourceDir, false, (entry) => {
      const segments = entry.name.split('/')
      return segments.some((segment) => IGNORED_DIRECTORIES.has(segment)) ? false : entry
    })
    archive.finalize().catch(reject)
  })
}
