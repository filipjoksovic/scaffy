type ChipOption = { id: string; label: string; lts?: boolean }

type ChipRowProps = {
  label: string
  options: ChipOption[]
  selectedId: string
  onSelect: (id: string) => void
  ariaLabel: string
}

export function ChipRow({ label, options, selectedId, onSelect, ariaLabel }: ChipRowProps) {
  return (
    <div className="chip-row">
      <span className="chip-row__label">{label}</span>
      <div className="chip-row__chips" role="radiogroup" aria-label={ariaLabel}>
        {options.map((option) => {
          const isSelected = option.id === selectedId
          return (
            <button
              aria-checked={isSelected}
              className={`chip${isSelected ? ' chip--selected' : ''}`}
              key={option.id}
              onClick={() => onSelect(option.id)}
              role="radio"
              type="button"
            >
              {option.label}
              {option.lts && (
                <span className="chip__badge" aria-label="Long-term support">
                  LTS
                </span>
              )}
            </button>
          )
        })}
      </div>
    </div>
  )
}
