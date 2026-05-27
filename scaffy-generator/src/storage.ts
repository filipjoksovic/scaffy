import { PutObjectCommand, S3Client } from '@aws-sdk/client-s3'
import { readFile } from 'node:fs/promises'
import type { GeneratorConfig } from './types.js'

export class ArtifactStorage {
  private readonly s3: S3Client

  constructor(private readonly config: GeneratorConfig) {
    this.s3 = new S3Client({
      endpoint: config.s3Endpoint,
      forcePathStyle: config.s3PathStyle,
      region: config.s3Region,
      credentials: {
        accessKeyId: config.s3AccessKey,
        secretAccessKey: config.s3SecretKey,
      },
    })
  }

  async uploadZip(objectKey: string, zipPath: string): Promise<void> {
    await this.s3.send(
      new PutObjectCommand({
        Bucket: this.config.s3Bucket,
        Key: objectKey,
        Body: await readFile(zipPath),
        ContentType: 'application/zip',
      }),
    )
  }
}
