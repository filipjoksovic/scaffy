import type { HTMLAttributes, ReactNode } from 'react'

type CardProps = HTMLAttributes<HTMLElement> & {
  as?: 'article' | 'div' | 'form'
  children: ReactNode
  inverted?: boolean
}

export function Card({
  as: Component = 'article',
  children,
  className,
  inverted = false,
  ...props
}: CardProps) {
  const classes = ['card', inverted && 'card--inverted', className].filter(Boolean).join(' ')

  return (
    <Component className={classes} {...props}>
      {children}
    </Component>
  )
}
