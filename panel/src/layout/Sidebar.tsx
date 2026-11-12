import { CompanySwitcher, type SwitcherProps } from '../components/CompanySwitcher';
import { Icon, type IconName } from '../ui/Icon';
import { BrandMark } from './BrandMark';
import styles from './Sidebar.module.css';

export type Page = 'companies' | 'company' | 'documents' | 'topics' | 'review' | 'usage' | 'gaps' | 'groupAdmins' | 'account';

type Props = {
  isGroup: boolean; name: string; companyName?: string; switcher?: SwitcherProps; page: Page; pending: number;
  setPage: (page: Page) => void; onLogout: () => void;
};

function CompanySlot({ isGroup, switcher, companyName }: { isGroup: boolean; switcher?: SwitcherProps; companyName?: string }) {
  if (switcher && (isGroup || switcher.companies.length > 1)) return <CompanySwitcher {...switcher} />;
  return companyName ? <p className={styles.company}>{companyName}</p> : null;
}

function PendingCount({ count }: { count: number }) {
  if (count === 0) return null;
  return (
    <>
      <span className={styles.count} aria-hidden="true">{count}</span>
      <span className="visually-hidden">, {count} {count === 1 ? 'ficha pendiente' : 'fichas pendientes'}</span>
    </>
  );
}

export function Sidebar({ isGroup, name, companyName, switcher, page, pending, setPage, onLogout }: Props) {
  const link = (target: Page, text: string, icon: IconName, count = 0) => (
    <a href="#" aria-current={page === target ? 'page' : undefined} onClick={(e) => { e.preventDefault(); setPage(target); }}>
      <Icon name={icon} />{text}
      <PendingCount count={count} />
    </a>
  );
  return (
    <aside className={styles.side}>
      <div className={styles.brand}><BrandMark /></div>
      <CompanySlot isGroup={isGroup} switcher={switcher} companyName={companyName} />
      <nav aria-label="Principal" className={styles.nav}>
        {isGroup && link('companies', 'Grupo', 'buildings')}
        {link('company', 'Empresa', 'building')}
        {link('documents', 'Documentos', 'file')}
        {link('topics', 'Temas', 'spark')}
        {link('review', 'Revisión', 'review', pending)}
        {link('usage', 'Uso', 'people')}
        {link('gaps', 'Huecos', 'file')}
      </nav>
      <div className={`${styles.nav} ${styles.foot}`}>
        <p className={styles.who}>{name}</p>
        {isGroup && link('groupAdmins', 'Administración del grupo', 'lock')}
        {link('account', 'Mi cuenta', 'user')}
        <button type="button" onClick={onLogout}><Icon name="logout" />Salir</button>
      </div>
    </aside>
  );
}
