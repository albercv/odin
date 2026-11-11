import { Icon, type IconName } from '../ui/Icon';
import { BrandMark } from './BrandMark';
import styles from './Sidebar.module.css';

export type Page = 'companies' | 'company' | 'documents' | 'review' | 'account';

type Props = {
  isGroup: boolean; name: string; companyName?: string; page: Page; pending: number;
  setPage: (page: Page) => void; onLogout: () => void;
};

export function Sidebar({ isGroup, name, companyName, page, pending, setPage, onLogout }: Props) {
  const link = (target: Page, text: string, icon: IconName, count = 0) => (
    <a href="#" aria-current={page === target ? 'page' : undefined} onClick={(e) => { e.preventDefault(); setPage(target); }}>
      <Icon name={icon} />{text}
      {count > 0 && (
        <>
          <span className={styles.count} aria-hidden="true">{count}</span>
          <span className="visually-hidden">, {count} {count === 1 ? 'ficha pendiente' : 'fichas pendientes'}</span>
        </>
      )}
    </a>
  );
  return (
    <aside className={styles.side}>
      <div className={styles.brand}><BrandMark /></div>
      {companyName && <p className={styles.company}>{companyName}</p>}
      <nav aria-label="Principal" className={styles.nav}>
        {isGroup && link('companies', 'Empresas', 'buildings')}
        {link('company', 'Empresa', 'building')}
        {link('documents', 'Documentos', 'file')}
        {link('review', 'Revisión', 'review', pending)}
      </nav>
      <div className={`${styles.nav} ${styles.foot}`}>
        <p className={styles.who}>{name}</p>
        {link('account', 'Mi cuenta', 'user')}
        <button type="button" onClick={onLogout}><Icon name="logout" />Salir</button>
      </div>
    </aside>
  );
}
