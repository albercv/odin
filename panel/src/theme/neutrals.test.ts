import { contrastRatio } from './contrast';
import { DARK_NEUTRALS, LIGHT_NEUTRALS, SIDEBAR_TEXT, DARK_TEXT_ON_ACCENT, type Neutrals } from './neutrals';

function pairs(n: Neutrals): [string, string, string][] {
  return [
    ['text', n.text, n.background], ['text', n.text, n.surface], ['text', n.text, n.surfaceMuted],
    ['textMuted', n.textMuted, n.background], ['textMuted', n.textMuted, n.surface], ['textMuted', n.textMuted, n.surfaceMuted],
    ['pending', n.pendingText, n.pendingBackground],
    ['danger', n.danger, n.background], ['danger', n.danger, n.surface], ['danger', n.danger, n.surfaceMuted],
  ];
}

const LIGHT_ONLY = ['danger', LIGHT_NEUTRALS.danger, LIGHT_NEUTRALS.dangerBackground] as const;

describe('neutrals', () => {
  it('fixedPairsReachFourAndAHalf', () => {
    for (const [mode, n] of [['light', LIGHT_NEUTRALS], ['dark', DARK_NEUTRALS]] as const) {
      for (const [name, fg, bg] of pairs(n)) {
        expect(contrastRatio(fg, bg), `${mode} ${name} ${fg} on ${bg}`).toBeGreaterThanOrEqual(4.5);
      }
    }
    expect(contrastRatio(LIGHT_ONLY[1], LIGHT_ONLY[2])).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(SIDEBAR_TEXT, '#283039')).toBeGreaterThanOrEqual(4.5);
    expect(DARK_TEXT_ON_ACCENT).toBe('#172026');
  });
});
