import styles from './Notice.module.css';

export function Notice({ message }: { message?: string }) {
  return message ? <p role="alert" className={styles.notice}>{message}</p> : null;
}
