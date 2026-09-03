import eslint from '@eslint/js'
import tseslint from 'typescript-eslint'
import pluginVue from 'eslint-plugin-vue'

export default tseslint.config(
  { ignores: ['dist', 'coverage', 'node_modules'] },
  eslint.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    languageOptions: {
      globals: {
        AbortController: 'readonly',
        HTMLDivElement: 'readonly',
        crypto: 'readonly',
        document: 'readonly',
        sessionStorage: 'readonly',
        window: 'readonly',
      },
      parserOptions: { parser: tseslint.parser },
    },
    rules: {
      'vue/multi-word-component-names': 'off',
      'vue/html-self-closing': 'off',
      'vue/max-attributes-per-line': 'off',
      'vue/no-v-html': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      '@typescript-eslint/no-explicit-any': 'off',
    },
  },
)
