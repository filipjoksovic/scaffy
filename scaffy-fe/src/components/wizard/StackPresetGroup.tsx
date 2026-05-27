import type { StackCatalogOption } from '../../api/init'
import { CheckIcon } from './CheckIcon'
import { ChipRow } from './ChipRow'
import { StackIcon } from './StackIcon'

type StackPresetGroupProps = {
  options: StackCatalogOption[]
  selectedId: string
  selectedVersionId: string
  selectedRuntimeId: string
  onSelect: (id: string) => void
  onVersionSelect: (id: string) => void
  onRuntimeSelect: (id: string) => void
  group: 'frontend' | 'backend'
}

export function StackPresetGroup({
  options,
  selectedId,
  selectedVersionId,
  selectedRuntimeId,
  onSelect,
  onVersionSelect,
  onRuntimeSelect,
  group,
}: StackPresetGroupProps) {
  const selected = findById(options, selectedId)
  const selectedVersion = findById(selected?.versions ?? [], selectedVersionId)

  return (
    <div className="stack-group">
      <div className="stack-cards" role="radiogroup" aria-label={`${group} framework`}>
        {options.map((option) => {
          const isSelected = selectedId === option.id
          const defaultVersion =
            findById(option.versions, option.defaultVersionId) ?? option.versions[0]
          const defaultRuntime =
            findById(defaultVersion?.runtimes ?? [], defaultVersion?.defaultRuntimeId ?? '') ??
            defaultVersion?.runtimes[0]

          return (
            <button
              aria-checked={isSelected}
              className={`stack-card${isSelected ? ' stack-card--selected' : ''}`}
              key={option.id}
              onClick={() => onSelect(option.id)}
              role="radio"
              type="button"
            >
              <span className="stack-card__icon" aria-hidden="true">
                <StackIcon id={option.id} />
              </span>
              <span className="stack-card__body">
                <span className="stack-card__name">{option.name}</span>
                <span className="stack-card__meta">
                  {[defaultVersion?.label, defaultRuntime?.label].filter(Boolean).join(' · ')}
                </span>
              </span>
              <span className="stack-card__mark" aria-hidden="true">
                {isSelected ? <CheckIcon /> : null}
              </span>
            </button>
          )
        })}
      </div>

      {selected && selectedVersion && (
        <div className="stack-detail">
          <p className="stack-detail__copy">{selected.description}</p>
          <ChipRow
            label="Version"
            options={selected.versions.map((v) => ({ id: v.id, label: v.label }))}
            selectedId={selectedVersionId}
            onSelect={onVersionSelect}
            ariaLabel={`${group} version`}
          />
          <ChipRow
            label="Runtime"
            options={selectedVersion.runtimes.map((r) => ({
              id: r.id,
              label: r.label,
              lts: r.lts,
            }))}
            selectedId={selectedRuntimeId}
            onSelect={onRuntimeSelect}
            ariaLabel={`${group} runtime`}
          />
        </div>
      )}
    </div>
  )
}

function findById<T extends { id: string }>(items: T[], id: string): T | undefined {
  return items.find((item) => item.id === id)
}
