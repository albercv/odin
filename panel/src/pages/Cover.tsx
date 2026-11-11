import { BrainCanvas } from '../brain/BrainCanvas';
import { COVER_PLACEMENT } from '../brain/brainRenderer';
import { BrandMark } from '../layout/BrandMark';
import { useBrand } from '../theme/BrandContext';
import styles from './Cover.module.css';

export function Cover({ onStart }: { onStart: () => void }) {
  const cover = useBrand().cover;
  if (!cover) return null;
  return (
    <section className={styles.cover} aria-label="Portada de la demo">
      <BrainCanvas placement={COVER_PLACEMENT} />
      <div className={styles.shade} />
      <BrandMark showOwner />
      <div className={styles.claim}>
        <h1>{cover.title}</h1>
        <p>{cover.subtitle}</p>
        <small>{cover.note}</small>
        <button type="button" className={styles.start} onClick={onStart}>Empezar la demostración</button>
      </div>
    </section>
  );
}
