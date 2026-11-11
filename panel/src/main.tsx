import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import { bootstrap } from './bootstrap';
import { BrandProvider } from './theme/BrandContext';
import './styles.css';

const { brand, theme } = await bootstrap(document.documentElement);
createRoot(document.getElementById('root')!).render(
  <StrictMode><BrandProvider brand={brand} theme={theme}><App /></BrandProvider></StrictMode>,
);
