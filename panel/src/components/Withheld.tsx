import styles from './Withheld.module.css';

/** The API holds back data that few people produced: showing it would tell who did what. */
export function Withheld() {
  return <p className={styles.withheld}>No se muestra para proteger a las personas (pocas en este periodo)</p>;
}
