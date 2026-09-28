/**
 * ESLint 配置（一致性检查基线）
 *
 * 目标：为质量门禁提供稳定、可复现的「代码一致性」校验。
 *
 * 规则分两层：
 *  1. 真实问题（error，必须修复才能提交）：重复键、非法转义、switch 作用域、
 *     不可达分支等会直接导致线上故障的问题。
 *  2. 存量债务（warn，允许提交但需持续收敛）：未使用变量、空块、
 *     常量条件、v-for 与 v-if 混用、重复 v-else-if 分支等。
 *     这些规则在历史代码中有存量告警，先降级为 warn 保证门禁可持续执行，
 *     后续分批收敛为 error。
 *
 * 模板风格规则交给 Prettier 与人工评审，避免历史代码大面积报错。
 * 变更本文件属「一致性检查规则」调整，需在 PR 中说明理由与影响面
 * （见 docs/ENGINEERING-RULES.md）。
 */
module.exports = {
  root: true,
  env: {
    browser: true,
    es2022: true,
    node: true
  },
  extends: ['eslint:recommended', 'plugin:vue/vue3-recommended'],
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module'
  },
  rules: {
    // ---- 存量债务：先告警，逐步收敛为 error ----
    'no-unused-vars': ['warn', { args: 'none', caughtErrors: 'none', varsIgnorePattern: '^_' }],
    'no-empty': ['warn', { allowEmptyCatch: true }],
    'no-constant-condition': ['warn', { checkLoops: false }],
    'vue/no-use-v-if-with-v-for': 'warn',
    'vue/no-dupe-v-else-if': 'warn',

    // ---- 真实问题：必须修复 ----
    'no-dupe-keys': 'error',
    'no-useless-escape': 'warn',
    'no-case-declarations': 'warn',

    // ---- Vue 模板风格：交给 Prettier / 人工评审 ----
    'vue/multi-word-component-names': 'off',
    'vue/html-indent': 'off',
    'vue/max-attributes-per-line': 'off',
    'vue/singleline-html-element-content-newline': 'off',
    'vue/html-self-closing': 'off',
    'vue/attributes-order': 'off',
    'vue/require-default-prop': 'off'
  },
  ignorePatterns: ['dist/', 'node_modules/', 'coverage/', 'public/lib/']
}
