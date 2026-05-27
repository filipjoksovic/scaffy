import AdmZip from 'adm-zip'
import path from 'node:path'
import type { ExtractedFile } from './types.js'

export function extractZipFiles(zipBytes: Buffer): ExtractedFile[] {
  const zip = new AdmZip(zipBytes)
  return zip
    .getEntries()
    .filter((entry) => !entry.isDirectory)
    .map((entry) => {
      const normalized = entry.entryName.replaceAll('\\', '/')
      if (!normalized || normalized.startsWith('/') || normalized.includes('\0')) {
        throw new Error(`Unsafe artifact path: ${entry.entryName}`)
      }
      const resolved = path.posix.normalize(normalized)
      if (resolved.startsWith('../') || resolved === '..') {
        throw new Error(`Unsafe artifact path: ${entry.entryName}`)
      }
      return {
        path: resolved,
        content: entry.getData(),
      }
    })
}
