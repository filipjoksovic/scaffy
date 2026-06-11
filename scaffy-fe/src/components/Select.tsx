import * as SelectPrimitive from "@radix-ui/react-select";
import { ChevronDown } from "lucide-react";

type SelectItem = {
  label: string;
  value: string;
};

type SelectProps = {
  compact?: boolean;
  hideLabel?: boolean;
  id: string;
  items: SelectItem[];
  label: string;
  onValueChange: (value: string) => void;
  value: string;
};

export function Select({
  compact = false,
  hideLabel = false,
  id,
  items,
  label,
  onValueChange,
  value,
}: SelectProps) {
  const labelClassName = hideLabel
    ? "select-field__label select-field__label--hidden"
    : "select-field__label";
  const triggerClassName = compact
    ? "select-trigger select-trigger--compact"
    : "select-trigger";

  return (
    <div className="select-field">
      <label className={labelClassName} htmlFor={id}>
        {label}
      </label>
      <SelectPrimitive.Root onValueChange={onValueChange} value={value}>
        <SelectPrimitive.Trigger
          aria-label={label}
          className={triggerClassName}
          id={id}
        >
          <SelectPrimitive.Value />
          <SelectPrimitive.Icon aria-hidden className="select-trigger__icon">
            <ChevronDown />
          </SelectPrimitive.Icon>
        </SelectPrimitive.Trigger>
        <SelectPrimitive.Portal>
          <SelectPrimitive.Content
            className="select-content"
            position="popper"
            sideOffset={6}
          >
            <SelectPrimitive.Viewport className="select-viewport">
              {items.map((item) => (
                <SelectPrimitive.Item
                  className="select-item"
                  key={item.value}
                  value={item.value}
                >
                  <SelectPrimitive.ItemText>
                    {item.label}
                  </SelectPrimitive.ItemText>
                </SelectPrimitive.Item>
              ))}
            </SelectPrimitive.Viewport>
          </SelectPrimitive.Content>
        </SelectPrimitive.Portal>
      </SelectPrimitive.Root>
    </div>
  );
}
