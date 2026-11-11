import styles from './EmptyState.module.css';

export function EmptyState({ title, description, headingRef }: { title: string; description?: string; headingRef?: React.Ref<HTMLHeadingElement> }) {
  return (
    <div className={styles.empty}>
      <h3 ref={headingRef} tabIndex={headingRef ? -1 : undefined}>{title}</h3>
      {description && <p className={styles.description}>{description}</p>}
    </div>
  );
}
