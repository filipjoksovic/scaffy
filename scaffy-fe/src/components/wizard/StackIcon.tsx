import type { ComponentType, SVGProps } from 'react'
import {
  SiAngular,
  SiDotnet,
  SiGithubactions,
  SiGitlab,
  SiNestjs,
  SiReact,
  SiSpring,
  SiVuedotjs,
} from 'react-icons/si'

type Props = SVGProps<SVGSVGElement> & { id: string }

const ICONS: Record<string, ComponentType<SVGProps<SVGSVGElement>>> = {
  react: SiReact,
  vue: SiVuedotjs,
  angular: SiAngular,
  'spring-boot': SiSpring,
  nestjs: SiNestjs,
  dotnet: SiDotnet,
  'github-actions': SiGithubactions,
  'gitlab-ci': SiGitlab,
}

export function StackIcon({ id, ...props }: Props) {
  const Icon = ICONS[id]
  if (!Icon) {
    return (
      <svg viewBox="0 0 24 24" aria-hidden="true" {...props}>
        <rect x="3" y="3" width="18" height="18" rx="3" fill="none" stroke="currentColor" strokeWidth="1.6" />
      </svg>
    )
  }
  return <Icon aria-hidden="true" {...props} />
}
