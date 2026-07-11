import js from '@eslint/js';
import prettier from 'eslint-config-prettier';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  {
    ignores: [
      '**/dist/**',
      '**/node_modules/**',
      '**/.cache/**',
      '**/.pnpm-store/**',
      '**/public/mockServiceWorker.js',
      'pnpm-lock.yaml',
      'scripts/**', // 独立 Node ops 脚本（如 seed-live-fixtures.mjs），不纳入 typed-lint
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.recommendedTypeChecked,
  {
    files: ['*.config.cjs', '*.config.mjs', 'eslint.config.mjs', 'vitest.config.ts', 'tests/**'],
    rules: {
      '@typescript-eslint/no-unsafe-assignment': 'off',
      '@typescript-eslint/no-unsafe-argument': 'off',
      '@typescript-eslint/no-unsafe-call': 'off',
      '@typescript-eslint/no-unsafe-member-access': 'off',
    },
  },
  {
    languageOptions: {
      parserOptions: {
        projectService: {
          allowDefaultProject: [
            '*.config.cjs',
            '*.config.mjs',
            'eslint.config.mjs',
            'vitest.config.ts',
            'playwright.config.ts',
            'playwright.live.config.ts',
            'tests/setup.ts',
            'tests/msw.ts',
          ],
        },
        tsconfigRootDir: import.meta.dirname,
      },
      globals: {
        ...globals.browser,
        ...globals.es2022,
        ...globals.node,
      },
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      // XSS 守卫：禁止把字符串当 HTML 注入 DOM 的所有 sink。
      // 全前端当前 0 处；如确需渲染富文本，须先经 DOMPurify 净化再单行 eslint-disable + 注释，禁整文件关规则。
      'no-restricted-syntax': [
        'error',
        {
          selector: "JSXAttribute[name.name='dangerouslySetInnerHTML']",
          message:
            '禁止 dangerouslySetInnerHTML（XSS 风险）。如确需渲染富文本，先经 DOMPurify 白名单净化，并在本行单独 eslint-disable 注明净化来源。',
        },
        {
          selector:
            "AssignmentExpression[left.type='MemberExpression'][left.property.name=/^(inner|outer)HTML$/]",
          message:
            '禁止直接赋值 innerHTML / outerHTML（XSS 风险）。请用 textContent 或 React 受控渲染。',
        },
        {
          selector: "CallExpression[callee.property.name='insertAdjacentHTML']",
          message: '禁止 insertAdjacentHTML（XSS 风险）。请用受控渲染或先经 DOMPurify 净化。',
        },
        {
          // 静态 Modal API 不吃 ConfigProvider 主题（<Modal> 组件用法不受限）
          selector:
            "CallExpression[callee.object.name='Modal'][callee.property.name=/^(confirm|info|success|error|warning|warn)$/]",
          message:
            '禁止静态 Modal.confirm/info/…（不吃 ConfigProvider 主题）。用 App.useApp() 的 modal。',
        },
        {
          // 数据列表统一走 shared-ui 的 DataTable（hover/行选中/空态/分页由令牌与组件承载）
          selector: "JSXOpeningElement[name.name='table']",
          message:
            '禁止手写 <table> 渲染数据列表，用 @wen-saas/shared-ui 的 DataTable。勾选矩阵等交互控件矩阵（非数据展示表）确需手写时，单行 eslint-disable 注明用途。',
        },
        {
          // 交互控件统一走 AntD（wa-* 只保留布局/排版工具类，名录见 design-system tokens.css）
          selector:
            "JSXAttribute[name.name='className'][value.value=/wa-(btn|input|select|textarea)/]",
          message:
            '禁止裸 wa-btn / wa-input / wa-select / wa-textarea 交互类（无 AntD 校验态/焦点环/主题联动）。按钮用 antd Button（或 WaBtn 薄封装），表单控件用 antd Input/Select。',
        },
        {
          // icon-only 按钮对读屏器只是「按钮」二字，必须给可访问名（Tooltip 只挂 aria-describedby，不构成可访问名）
          selector:
            "JSXOpeningElement[selfClosing=true][name.name='Button']:has(JSXAttribute[name.name='icon']):not(:has(JSXAttribute[name.name='aria-label'], JSXAttribute[name.name='aria-labelledby'], JSXSpreadAttribute))",
          message:
            'icon-only 的 Button（有 icon 无 children）必须带 aria-label，否则读屏器无法辨识用途。',
        },
      ],
      // 静态 message/notification 不吃 ConfigProvider 主题，统一走 App.useApp()
      'no-restricted-imports': [
        'error',
        {
          paths: [
            {
              name: 'antd',
              importNames: ['message', 'notification'],
              message:
                '禁止导入静态 message/notification（不吃 ConfigProvider 主题）。用 App.useApp()。',
            },
          ],
        },
      ],
    },
  },
  prettier,
);
