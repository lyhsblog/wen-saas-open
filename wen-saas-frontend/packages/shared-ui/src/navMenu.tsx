import type { ReactNode } from 'react';
import {
  ApartmentOutlined,
  ApiOutlined,
  AppstoreOutlined,
  AuditOutlined,
  BankOutlined,
  BellOutlined,
  BookOutlined,
  CloudOutlined,
  CreditCardOutlined,
  DashboardOutlined,
  FolderOutlined,
  HomeOutlined,
  InboxOutlined,
  KeyOutlined,
  LineChartOutlined,
  MenuOutlined,
  MessageOutlined,
  NotificationOutlined,
  ProfileOutlined,
  SafetyCertificateOutlined,
  SafetyOutlined,
  SettingOutlined,
  SyncOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import type { AppShellMenuItem } from './AppShell';

/**
 * 后端 `sys_menu` 导航节点（capabilities.menus）。GROUP 节点 path 为 null（分组标题）、
 * PAGE 节点带 path；后端已按当前用户权限解析可见性 + 排序。结构型，避免 shared-ui 反向依赖 permissions 包。
 */
export type NavMenuNode = {
  path: string | null;
  name: string;
  icon: string | null;
  sort: number;
  children: NavMenuNode[];
};

/** 图标名（sys_menu.icon，种子里用的 antd 风格短名）→ 图标元素。 */
const ICONS: Record<string, ReactNode> = {
  dashboard: <DashboardOutlined />,
  team: <TeamOutlined />,
  user: <UserOutlined />,
  appstore: <AppstoreOutlined />,
  book: <BookOutlined />,
  'safety-certificate': <SafetyCertificateOutlined />,
  bell: <BellOutlined />,
  notification: <NotificationOutlined />,
  'line-chart': <LineChartOutlined />,
  message: <MessageOutlined />,
  safety: <SafetyOutlined />,
  setting: <SettingOutlined />,
  profile: <ProfileOutlined />,
  menu: <MenuOutlined />,
  home: <HomeOutlined />,
  apartment: <ApartmentOutlined />,
  inbox: <InboxOutlined />,
  folder: <FolderOutlined />,
  sync: <SyncOutlined />,
  bank: <BankOutlined />,
  'credit-card': <CreditCardOutlined />,
  cloud: <CloudOutlined />,
  audit: <AuditOutlined />,
  api: <ApiOutlined />,
  key: <KeyOutlined />,
};

/** 有 icon 名但映射缺键时回落 appstore，保证侧栏各项图标不缺位。 */
export function iconByName(name: string | null | undefined): ReactNode | undefined {
  if (!name) return undefined;
  return ICONS[name] ?? <AppstoreOutlined />;
}

function isGroup(node: NavMenuNode): boolean {
  return !node.path;
}

/**
 * 把后端导航树拍平成 {@link AppShellMenuItem}[]：顶层 GROUP → `{section}` + 其子 PAGE 链接；
 * 顶层 PAGE → 链接。后端已过滤空组 / 不可见节点，前端只渲染。
 */
export function flattenNavTree(menus: NavMenuNode[]): AppShellMenuItem[] {
  const out: AppShellMenuItem[] = [];
  for (const node of menus) {
    if (isGroup(node)) {
      out.push({ section: node.name });
      for (const child of node.children ?? []) {
        if (child.path) out.push(linkItem(child));
      }
    } else {
      out.push(linkItem(node));
    }
  }
  return out;
}

function linkItem(node: NavMenuNode): AppShellMenuItem {
  return { key: node.path as string, label: node.name, icon: iconByName(node.icon) };
}

/** 侧栏选中键：取 pathname 的最长前缀匹配链接 key；无匹配返回 fallback。 */
export function selectNavKey(
  items: AppShellMenuItem[],
  pathname: string,
  fallback: string,
): string {
  let matched = '';
  for (const item of items) {
    if (!('key' in item)) continue;
    const key = item.key;
    if (pathname === key || pathname.startsWith(`${key}/`)) {
      if (key.length > matched.length) matched = key;
    }
  }
  return matched || fallback;
}

/** 在拍平后的菜单里找某 key 的 label（面包屑用）。 */
export function navLabelOf(items: AppShellMenuItem[], key: string): ReactNode {
  const found = items.find((m) => 'key' in m && m.key === key) as { label: ReactNode } | undefined;
  return found?.label ?? '';
}
