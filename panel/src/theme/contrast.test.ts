import { contrastRatio, mixColors, shiftLightness } from './contrast';

function hue(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255) as [number, number, number];
  const max = Math.max(r, g, b);
  const d = max - Math.min(r, g, b);
  if (d === 0) return 0;
  const h = max === r ? ((g - b) / d) % 6 : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
  return (h * 60 + 360) % 360;
}

describe('contrast', () => {
  it('matchesKnownWcagValues', () => {
    expect(contrastRatio('#05b4b9', '#ffffff')).toBeCloseTo(2.55, 1);
    expect(contrastRatio('#ffffff', '#000000')).toBeCloseTo(21, 5);
    expect(contrastRatio('#03797c', '#ffffff')).toBeCloseTo(5.21, 1);
  });

  it('mixesColors', () => {
    expect(mixColors('#05b4b9', '#232c34', 0.14)).toBe('#1f3f47');
  });

  it('shiftLightnessKeepsTheHue', () => {
    const shifted = shiftLightness('#05b4b9', -0.2);
    expect(Math.abs(hue(shifted) - hue('#05b4b9'))).toBeLessThan(2);
    expect(contrastRatio(shifted, '#ffffff')).toBeGreaterThan(contrastRatio('#05b4b9', '#ffffff'));
  });
});
