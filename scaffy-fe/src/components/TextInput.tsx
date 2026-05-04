import type { InputHTMLAttributes } from 'react'

type TextInputProps = InputHTMLAttributes<HTMLInputElement>

export function TextInput({ className, ...props }: TextInputProps) {
  const classes = ['text-input', className].filter(Boolean).join(' ')

  return <input className={classes} {...props} />
}
