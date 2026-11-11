import js from '@eslint/js';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['node_modules/', 'dist/', 'src/api/types.ts'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  { rules: { 'max-lines-per-function': ['error', { max: 30, skipBlankLines: true, skipComments: true }] } },
  { files: ['**/*.test.tsx', 'e2e/**', 'playwright.config.ts'], rules: { 'max-lines-per-function': 'off' } },
);
