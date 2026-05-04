import type { HTMLAttributes, ReactNode } from 'react'

export type TimelineTone = 'thinking' | 'grep' | 'read' | 'edit' | 'done'

type TimelinePillProps = HTMLAttributes<HTMLSpanElement> & {
  children: ReactNode
  tone: TimelineTone
}

export function TimelinePill({ children, className, tone, ...props }: TimelinePillProps) {
  const classes = ['timeline-pill', `timeline-pill--${tone}`, className].filter(Boolean).join(' ')

  return (
    <span className={classes} {...props}>
      {children}
    </span>
  )
}
