import type { ReactNode } from 'react'

type StateTone = 'loading' | 'empty' | 'error' | 'success'

type StateRowProps = {
  detail: string
  icon?: ReactNode
  label: string
  tone: StateTone
}

export function StateRow({ detail, icon, label, tone }: StateRowProps) {
  return (
    <div className={`state-row state-row--${tone}`}>
      <span aria-hidden="true">{icon}</span>
      <strong>{label}</strong>
      <p>{detail}</p>
    </div>
  )
}
