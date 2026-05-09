type StepIndicatorProps = {
  current: number
  onJump: (step: number) => void
  steps: string[]
}

export function StepIndicator({ current, onJump, steps }: StepIndicatorProps) {
  return (
    <ol aria-label="Wizard progress" className="step-indicator">
      {steps.map((label, index) => {
        const stepNumber = index + 1
        const state =
          stepNumber < current ? 'done' : stepNumber === current ? 'current' : 'pending'
        const reachable = stepNumber <= current

        return (
          <li
            className={`step-indicator__item step-indicator__item--${state}`}
            key={label}
          >
            <button
              aria-current={stepNumber === current ? 'step' : undefined}
              className="step-indicator__button"
              disabled={!reachable}
              onClick={() => onJump(stepNumber)}
              type="button"
            >
              <span className="step-indicator__num">{stepNumber}</span>
              <span className="step-indicator__label">{label}</span>
            </button>
          </li>
        )
      })}
    </ol>
  )
}
