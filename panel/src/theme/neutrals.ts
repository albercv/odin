import { mixColors } from './contrast';

export type Neutrals = {
  background: string; surface: string; surfaceMuted: string; line: string; lineStrong: string;
  text: string; textMuted: string; pendingText: string; pendingBackground: string; danger: string; dangerBackground: string;
};

export const LIGHT_NEUTRALS: Neutrals = {
  background: '#f3f6f7', surface: '#ffffff', surfaceMuted: '#eef2f4', line: '#dbe2e6', lineStrong: '#c3cdd3',
  text: '#1d252c', textMuted: '#56636d', pendingText: '#8a4b00', pendingBackground: '#fdf1dc',
  danger: '#b42318', dangerBackground: '#fdecea',
};

export const DARK_NEUTRALS: Neutrals = {
  background: '#1b2229', surface: '#232c34', surfaceMuted: '#2a343d', line: '#34404a', lineStrong: '#46535e',
  text: '#e8eef1', textMuted: '#9fb0bb', pendingText: '#f2b85b', pendingBackground: mixColors('#f2b85b', '#232c34', 0.08),
  danger: '#f97066', dangerBackground: mixColors('#f97066', '#232c34', 0.08),
};

export const SIDEBAR_TEXT = '#c5ced4';
export const DARK_TEXT_ON_ACCENT = '#172026';
