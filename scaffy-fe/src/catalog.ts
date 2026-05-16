export type StackOption = {
  id: string
  name: string
  description: string
  available: boolean
}

export const FRONTENDS: StackOption[] = [
  {
    id: 'angular',
    name: 'Angular',
    description: 'Angular 18 with routing, CSS, standalone bootstrap.',
    available: true,
  },
  {
    id: 'react',
    name: 'React',
    description: 'React + Vite + TypeScript.',
    available: true,
  },
  {
    id: 'vue',
    name: 'Vue',
    description: 'Vue 3 + Vite + TypeScript.',
    available: true,
  },
]

export const BACKENDS: StackOption[] = [
  {
    id: 'spring-boot',
    name: 'Spring Boot',
    description: 'Spring Boot 4, Java 25, Maven, web starter.',
    available: true,
  },
  {
    id: 'nestjs',
    name: 'NestJS',
    description: 'NestJS + TypeScript REST starter.',
    available: false,
  },
  {
    id: 'dotnet',
    name: '.NET',
    description: '.NET Web API.',
    available: true,
  },
]

export const PIPELINES: StackOption[] = [
  {
    id: 'github-actions',
    name: 'GitHub Actions',
    description: 'Workflow file in .github/workflows/ci.yml.',
    available: true,
  },
  {
    id: 'gitlab-ci',
    name: 'GitLab CI',
    description: '.gitlab-ci.yml at the project root.',
    available: true,
  },
]

export function findOption(options: StackOption[], id: string): StackOption | undefined {
  return options.find((option) => option.id === id)
}
