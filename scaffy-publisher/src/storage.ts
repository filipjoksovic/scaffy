import { GetObjectCommand, S3Client } from '@aws-sdk/client-s3'
import type { PublisherConfig } from './types.js'

export class ArtifactStorage {
  private readonly s3: S3Client

  constructor(private readonly config: PublisherConfig) {
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

  async downloadZip(objectKey: string): Promise<Buffer> {
    const response = await this.s3.send(
      new GetObjectCommand({
        Bucket: this.config.s3Bucket,
        Key: objectKey,
      }),
    )
    if (!response.Body) {
      throw new Error('Generated artifact is empty.')
    }
    return Buffer.from(await response.Body.transformToByteArray())
  }
}
