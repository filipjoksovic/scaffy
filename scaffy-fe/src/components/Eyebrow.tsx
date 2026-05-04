import type { HTMLAttributes, ReactNode } from 'react'

type EyebrowProps = HTMLAttributes<HTMLParagraphElement> & {
  children: ReactNode
}

export function Eyebrow({ children, className, ...props }: EyebrowProps) {
  const classes = ['eyebrow', className].filter(Boolean).join(' ')

  return (
    <p className={classes} {...props}>
      {children}
    </p>
  )
}
