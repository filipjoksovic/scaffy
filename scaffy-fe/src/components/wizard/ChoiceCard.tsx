type ChoiceCardProps = {
  available: boolean
  description: string
  name: string
  onSelect: () => void
  selected: boolean
}

export function ChoiceCard({ available, description, name, onSelect, selected }: ChoiceCardProps) {
  const classes = [
    'choice-card',
    selected && 'choice-card--selected',
    !available && 'choice-card--disabled',
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <button
      aria-checked={selected}
      className={classes}
      disabled={!available}
      onClick={onSelect}
      role="radio"
      type="button"
    >
      <div className="choice-card__head">
        <strong>{name}</strong>
        {!available && <span className="choice-card__badge">Coming in v2</span>}
      </div>
      <p>{description}</p>
    </button>
  )
}
