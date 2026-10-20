// @ts-check
const eslint = require('@eslint/js');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');
const prettier = require('eslint-config-prettier');

module.exports = tseslint.config(
  { ignores: ['dist/', '.angular/', 'coverage/', 'playwright-report/', 'test-results/', 'node_modules/'] },
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      ...tseslint.configs.recommended,
      ...tseslint.configs.stylistic,
      ...angular.configs.tsRecommended,
      prettier,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      '@angular-eslint/directive-selector': ['error', { type: 'attribute', prefix: ['app', 'ui'], style: 'camelCase' }],
      '@angular-eslint/component-selector': ['error', { type: 'element', prefix: ['app', 'ui'], style: 'kebab-case' }],
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/consistent-type-definitions': 'off',
      eqeqeq: ['error', 'always'],
      'no-console': ['error', { allow: ['warn', 'error'] }],
    },
  },
  // Module boundaries (the frontend counterpart of backend/.../ArchitectureTest; see docs/adr/0003).
  // Layers: app shell -> features -> core -> shared. Features only talk to each other through a public API.
  {
    files: ['src/app/features/**/*.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['../../*', '../../**'],
              message: 'Leave the feature through the @core, @shared or @features aliases, not relative paths.',
            },
            {
              group: [
                '@features/login/**',
                '@features/payment/**',
                '@features/transactions/**',
                '@features/accounts/pages/**',
                '@features/accounts/state/*',
                '@features/accounts/*.routes',
              ],
              message:
                'Features may only depend on the public API of the accounts feature (@features/accounts/state).',
            },
          ],
        },
      ],
    },
  },
  {
    files: ['src/app/features/accounts/**/*.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['../../*', '../../**'],
              message: 'Leave the feature through the @core, @shared or @features aliases, not relative paths.',
            },
            {
              group: ['@features/**'],
              message: 'The accounts feature is a leaf and must not depend on other features.',
            },
          ],
        },
      ],
    },
  },
  {
    files: ['src/app/core/**/*.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        { patterns: [{ group: ['@features/**'], message: 'core must not depend on features.' }] },
      ],
    },
  },
  {
    files: ['src/app/shared/**/*.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            { group: ['@features/**'], message: 'shared must not depend on features.' },
            { group: ['@core/**'], message: 'shared must not depend on core.' },
          ],
        },
      ],
    },
  },
  {
    files: ['**/*.html'],
    extends: [...angular.configs.templateRecommended, ...angular.configs.templateAccessibility, prettier],
  },
);
