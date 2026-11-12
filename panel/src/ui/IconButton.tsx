import { Button, type ButtonProps } from './Button';
import type { IconName } from './Icon';
import styles from './IconButton.module.css';

type Props = Omit<ButtonProps, 'icon' | 'children'> & { icon: IconName; label: string };

export function IconButton({ icon, label, className, variant = 'ghost', ...rest }: Props) {
  return <Button {...rest} variant={variant} icon={icon} aria-label={label} className={[styles.iconButton, className].filter(Boolean).join(' ')} />;
}
