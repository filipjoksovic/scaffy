import type { ReactNode } from 'react'

type WizardStepProps = {
  index: number
  title: string
  hint: string
  children: ReactNode
}

export function WizardStep({ index, title, hint, children }: WizardStepProps) {
  return (
    <section className="init-step">
      <header className="init-step__head">
        <span className="init-step__index">{String(index).padStart(2, '0')}</span>
        <div>
          <h2 className="init-step__title">{title}</h2>
          <p className="init-step__hint">{hint}</p>
        </div>
      </header>
      <div className="init-step__body">{children}</div>
    </section>
  )
}
