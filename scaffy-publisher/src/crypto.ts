import { createDecipheriv, createHash } from 'node:crypto'

export function decryptProviderToken(encryptedToken: string, secret: string): string {
  const parts = encryptedToken.split('.')
  if (parts.length !== 2) {
    throw new Error('Stored GitHub token has an invalid encrypted format.')
  }

  const iv = Buffer.from(parts[0], 'base64url')
  const payload = Buffer.from(parts[1], 'base64url')
  const encrypted = payload.subarray(0, payload.length - 16)
  const authTag = payload.subarray(payload.length - 16)
  const key = createHash('sha256').update(secret, 'utf8').digest()
  const decipher = createDecipheriv('aes-256-gcm', key, iv)
  decipher.setAuthTag(authTag)
  return Buffer.concat([decipher.update(encrypted), decipher.final()]).toString('utf8')
}
