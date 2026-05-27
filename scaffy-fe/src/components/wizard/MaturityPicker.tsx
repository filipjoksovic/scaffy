import type { MaturityPreset } from '../../api/init'
import { CheckIcon } from './CheckIcon'
import { Tooltip } from '../Tooltip'

type MaturityPickerProps = {
  presets: MaturityPreset[]
  selectedId: string
  onSelect: (id: string) => void
}

export function MaturityPicker({ presets, selectedId, onSelect }: MaturityPickerProps) {
  return (
    <div className="maturity-section">
      <div className="maturity-section__head">
        <span className="maturity-section__label">Maturity level</span>
        <span className="maturity-section__hint">
          How much delivery discipline Scaffy should generate.
        </span>
      </div>

      <div className="maturity-picker" role="radiogroup" aria-label="Pipeline maturity level">
        {presets.map((preset) => {
          const isSelected = selectedId === preset.id
          const name = preset.label.replace(/^L\d\s+/, '')
          return (
            <button
              aria-checked={isSelected}
              className={`maturity-card${isSelected ? ' maturity-card--selected' : ''}`}
              key={preset.id}
              onClick={() => onSelect(preset.id)}
              role="radio"
              type="button"
            >
              <span className="maturity-card__bars" aria-hidden="true">
                {[1, 2, 3, 4].map((i) => (
                  <span
                    className={`maturity-card__bar${
                      i <= preset.level ? ' maturity-card__bar--on' : ''
                    }`}
                    key={i}
                  />
                ))}
              </span>
              <span className="maturity-card__body">
                <span className="maturity-card__title-row">
                  <span className="maturity-card__name">
                    <span className="maturity-card__level">L{preset.level}</span>
                    {name}
                  </span>
                  {preset.dockerRequired && (
                    <span className="maturity-card__badge" title="Requires Docker">
                      Docker
                    </span>
                  )}
                </span>
                <span className="maturity-card__desc">{preset.description}</span>
              </span>
              <span className="maturity-card__mark" aria-hidden="true">
                {isSelected ? <CheckIcon /> : null}
              </span>
            </button>
          )
        })}
      </div>

      <MaturityLockedCard />
    </div>
  )
}

export function MaturityLockedCard() {
  return (
    <Tooltip
      side="top"
      align="start"
      content={
        <div className="tooltip__body">
          <strong>Why no L5 yet?</strong>
          <p>
            Honest L5 needs choices Scaffy doesn&apos;t ask for: deployment target (Kubernetes,
            Vercel, ECS…), registry, IaC tool (Terraform, Pulumi, Helm), rollout strategy
            (canary, blue-green, rolling), secrets model, and shared reusable workflows.
          </p>
          <p>
            Generating it without those would produce YAML that &quot;looks mature&quot; but
            doesn&apos;t actually run. It will land as a separate advanced flow.
          </p>
        </div>
      }
    >
      <div
        aria-disabled="true"
        className="maturity-locked"
        role="note"
        tabIndex={0}
      >
        <span className="maturity-locked__bars" aria-hidden="true">
          {[1, 2, 3, 4, 5].map((i) => (
            <span
              className={`maturity-card__bar${i <= 5 ? ' maturity-card__bar--on' : ''}`}
              key={i}
            />
          ))}
        </span>
        <div className="maturity-locked__body">
          <div className="maturity-locked__title-row">
            <span className="maturity-card__name">
              <span className="maturity-card__level">L5</span>
              Advanced Pipeline
            </span>
            <span className="maturity-locked__chip">Coming later</span>
          </div>
          <p className="maturity-locked__desc">
            Canary / blue-green rollouts, policy-as-code, IaC, and reusable org workflows —
            hover for why this isn&apos;t available yet.
          </p>
        </div>
      </div>
    </Tooltip>
  )
}
