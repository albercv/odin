import { createContext, useContext } from 'react';
import type { ColorPreference } from './colorPreference';

export type ColorChoice = { preference: ColorPreference; setPreference(p: ColorPreference): void };

// Filled once by the root (useColorMode); consumers never create a second instance.
export const ColorModeContext = createContext<ColorChoice>({ preference: 'system', setPreference: () => undefined });
export const useColorChoice = () => useContext(ColorModeContext);
