import { useEffect, useRef, useState } from "react";
import {
  deleteFavouriteStack,
  getFavouriteStacks,
  saveFavouriteStack,
  type FavouriteStack,
  type FavouriteStackRequest,
} from "../api/init";
import { Button } from "./Button";

type Props = {
  /** Current wizard selection — used when saving a new favourite. */
  currentSelection: FavouriteStackRequest;
  /** Called when the user clicks "Load" on a saved favourite. */
  onLoad: (favourite: FavouriteStack) => void;
  /** Whether the wizard has enough state to save a meaningful favourite. */
  canSave: boolean;
};

export function FavouriteStacks({ currentSelection, onLoad, canSave }: Props) {
  const [favourites, setFavourites] = useState<FavouriteStack[]>([]);
  const [unauthenticated, setUnauthenticated] = useState(false);
  const [saveName, setSaveName] = useState("");
  const [showSaveForm, setShowSaveForm] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const nameInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    getFavouriteStacks()
      .then(setFavourites)
      .catch((err: unknown) => {
        const message = err instanceof Error ? err.message : "";
        if (
          message.includes("401") ||
          message.toLowerCase().includes("unauthori")
        ) {
          setUnauthenticated(true);
        }
      });
  }, []);

  async function handleSave() {
    if (!saveName.trim()) return;
    setSaving(true);
    setError(null);
    try {
      const saved = await saveFavouriteStack({
        ...currentSelection,
        name: saveName.trim(),
      });
      setFavourites((prev) => [saved, ...prev]);
      setSaveName("");
      setShowSaveForm(false);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Could not save favourite.",
      );
    } finally {
      setSaving(false);
    }
  }

  function openSaveForm() {
    setShowSaveForm(true);
    setTimeout(() => nameInputRef.current?.focus(), 50);
  }

  async function handleDelete(id: string) {
    try {
      await deleteFavouriteStack(id);
      setFavourites((prev) => prev.filter((f) => f.id !== id));
    } catch {
      // Ignore — item stays visible; user can retry.
    }
  }

  return (
    <section aria-labelledby="favourites-heading" className="review favourites">
      {/* Header row — matches the .review__head style */}
      <div className="review__head favourites__head">
        <span className="review__eyebrow">Favourites</span>
        {canSave && !showSaveForm && !unauthenticated && (
          <Button
            className="button--small"
            variant="secondary"
            title="Save current stack as a favourite"
            onClick={openSaveForm}
          >
            ★ Save current
          </Button>
        )}
      </div>

      {/* Save form */}
      {showSaveForm && (
        <div className="favourites__save-form">
          <input
            ref={nameInputRef}
            autoFocus
            className="favourites__name-input"
            maxLength={64}
            placeholder="e.g. My React + Spring setup"
            type="text"
            value={saveName}
            onChange={(e) => setSaveName(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") handleSave();
              if (e.key === "Escape") setShowSaveForm(false);
            }}
          />
          <div className="favourites__form-actions">
            <Button disabled={!saveName.trim() || saving} onClick={handleSave}>
              {saving ? "Saving…" : "Save"}
            </Button>
            <Button variant="secondary" onClick={() => setShowSaveForm(false)}>
              Cancel
            </Button>
          </div>
          {error && <span className="favourites__error">{error}</span>}
        </div>
      )}

      {/* Unauthenticated hint */}
      {unauthenticated && (
        <p className="favourites__hint">
          Sign in to save and recall favourite stack presets.
        </p>
      )}

      {/* Saved items list */}
      {favourites.length > 0 && (
        <ul className="favourites__list" role="list">
          {favourites.map((fav) => (
            <li key={fav.id} className="favourites__item">
              <div className="favourites__item-body">
                <span className="favourites__item-name">{fav.name}</span>
                <span className="favourites__item-meta">
                  {fav.frontend} · {fav.backend} · {fav.pipeline}
                </span>
              </div>
              <div className="favourites__item-actions">
                <Button variant="secondary" onClick={() => onLoad(fav)}>
                  Load
                </Button>
                <button
                  aria-label={`Remove ${fav.name}`}
                  className="favourites__delete"
                  type="button"
                  onClick={() => handleDelete(fav.id)}
                >
                  ✕
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      {/* Empty state */}
      {!unauthenticated && favourites.length === 0 && !showSaveForm && (
        <p className="favourites__hint">
          {canSave
            ? 'Click "★ Save current" to bookmark this stack.'
            : "Complete your stack selection to save a favourite."}
        </p>
      )}
    </section>
  );
}
