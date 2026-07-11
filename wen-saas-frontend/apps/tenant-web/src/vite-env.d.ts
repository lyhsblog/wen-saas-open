/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 'mock' 时 dev 启用 MSW；其它值（含未设置）走真后端代理。 */
  readonly VITE_API_MODE?: 'mock' | 'live';
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
