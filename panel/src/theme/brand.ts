export type BrandColors = { accent: string; base: string };
export type BrandCover = { title: string; subtitle: string; note: string };
export type Brand = { name?: string; logoUrl?: string; logoAlt?: string; colors: BrandColors; cover?: BrandCover };

export const NEUTRAL_BRAND: Brand = { colors: { accent: '#4f6d8f', base: '#2b3036' } };

const HEX = /^#[0-9a-fA-F]{6}$/;
const LOGO_FILE = /^[\w.-]+\.(png|webp|svg)$/;
const BRAND_JSON_URL = '/brand/brand.json';

type Json = Record<string, unknown>;

function isObject(value: unknown): value is Json {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function text(value: unknown, max: number): string | undefined {
  return typeof value === 'string' && value.length <= max ? value : undefined;
}

function parseColors(raw: unknown): BrandColors | undefined {
  if (!isObject(raw)) return undefined;
  const { accent, base } = raw;
  return typeof accent === 'string' && typeof base === 'string' && HEX.test(accent) && HEX.test(base) ? { accent, base } : undefined;
}

function parseCover(raw: unknown): BrandCover | undefined {
  if (!isObject(raw)) return undefined;
  const [title, subtitle, note] = [text(raw.title, 80), text(raw.subtitle, 160), text(raw.note, 100)];
  return title !== undefined && subtitle !== undefined && note !== undefined ? { title, subtitle, note } : undefined;
}

function optionalText(raw: Json, key: string, max: number): { ok: boolean; value?: string } {
  if (raw[key] === undefined) return { ok: true };
  const value = text(raw[key], max);
  return { ok: value !== undefined, value };
}

export function parseBrand(raw: unknown): Brand | undefined {
  if (!isObject(raw)) return undefined;
  const colors = parseColors(raw.colors);
  const name = optionalText(raw, 'name', 60);
  const logoAlt = optionalText(raw, 'logoAlt', 80);
  const logoOk = raw.logo === undefined || (typeof raw.logo === 'string' && LOGO_FILE.test(raw.logo) && !raw.logo.includes('..'));
  const cover = raw.cover === undefined ? undefined : parseCover(raw.cover);
  if (!colors || !name.ok || !logoAlt.ok || !logoOk || (raw.cover !== undefined && !cover)) return undefined;
  const brand: Brand = { colors };
  if (name.value !== undefined) brand.name = name.value;
  if (typeof raw.logo === 'string') brand.logoUrl = '/brand/' + raw.logo;
  if (logoAlt.value !== undefined) brand.logoAlt = logoAlt.value;
  if (cover) brand.cover = cover;
  return brand;
}

async function fetchBrandJson(): Promise<unknown> {
  const response = await fetch(BRAND_JSON_URL, { cache: 'no-cache' });
  if (!response.ok) throw new Error(`respuesta ${response.status}`);
  return response.json();
}

export async function loadBrand(fetchBrand: () => Promise<unknown> = fetchBrandJson, timeoutMs = 2000): Promise<Brand> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  const timeout = new Promise<never>((_, reject) => {
    timer = setTimeout(() => reject(new Error(`sin respuesta tras ${timeoutMs} ms`)), timeoutMs);
  });
  try {
    const brand = parseBrand(await Promise.race([fetchBrand(), timeout]));
    if (!brand) throw new Error('la marca no es válida');
    return brand;
  } catch (error) {
    console.error(`Marca no disponible, se usa el tema neutro: ${error instanceof Error ? error.message : String(error)}`);
    return NEUTRAL_BRAND;
  } finally {
    clearTimeout(timer);
  }
}
