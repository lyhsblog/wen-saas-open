/**
 * 数据范围（data_scope）枚举文案。
 *
 * 同一套语义有**两种线上表示**，按表示分两个独立单源（值不同，故分开定义、不互相引用）：
 * - **字符串版** {@link TENANT_DATA_SCOPE_LABEL}：`ALL / DEPT_AND_BELOW / SELF`。tenant-web 角色用，h5 可复用。
 * - **数字版** {@link RECOMMENDED_DATA_SCOPE_LABEL}：`1 / 2 / 3`。推荐角色用（ops recroles + tenant 推荐角色弹窗共用）。
 *
 * **不并入**：运营域 `ALL / OWNED`（运营 RBAC，值/语义都不同）——留 ops 本地 `sysadminMeta`。
 *
 * 仅放文案（label）；带 sub/risk/icon 等 UI 资产的调权卡片留在消费端本地。
 */
export type TenantDataScope = 'ALL' | 'DEPT_AND_BELOW' | 'DEPT_ONLY' | 'SELF';

export const TENANT_DATA_SCOPE_LABEL: Record<TenantDataScope, string> = {
  ALL: '全部数据',
  DEPT_AND_BELOW: '本部门及下属部门',
  DEPT_ONLY: '仅本部门（不含子级）',
  SELF: '仅本人创建',
};

/** 数字表示（1/2/3/4）；文案与字符串版对齐。推荐角色（ops recroles + tenant 推荐角色弹窗）共用。 */
export type RecommendedDataScope = 1 | 2 | 3 | 4;

export const RECOMMENDED_DATA_SCOPE_LABEL: Record<RecommendedDataScope, string> = {
  1: '全部数据',
  2: '本部门及下属部门',
  4: '仅本部门（不含子级）',
  3: '仅本人创建',
};
