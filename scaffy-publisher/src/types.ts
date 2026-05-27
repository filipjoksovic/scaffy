export type PublisherConfig = {
  databaseUrl: string
  redisUrl: string
  queueName: string
  s3Endpoint?: string
  s3Region: string
  s3Bucket: string
  s3AccessKey: string
  s3SecretKey: string
  s3PathStyle: boolean
  providerTokenEncryptionSecret: string
  githubApiUrl: string
}

export type PublicationJob = {
  id: string
  userId: string
  initJobId: string
  repositoryName: string
  repositoryDescription: string | null
  status: string
  artifactObjectKey: string
}

export type OAuthToken = {
  encryptedAccessToken: string
  scopes: string | null
}

export type ExtractedFile = {
  path: string
  content: Buffer
}
