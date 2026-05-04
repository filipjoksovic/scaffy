import type { HTMLAttributes, ReactNode } from 'react'

type BadgeProps = HTMLAttributes<HTMLSpanElement> & {
  children: ReactNode
}

export function Badge({ children, className, ...props }: BadgeProps) {
  const classes = ['badge', className].filter(Boolean).join(' ')

  return (
    <span className={classes} {...props}>
      {children}
    </span>
  )
}
