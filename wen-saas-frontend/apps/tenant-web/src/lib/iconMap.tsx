import type { ReactNode } from 'react';
import {
  ApartmentOutlined,
  ApiOutlined,
  AppstoreOutlined,
  AuditOutlined,
  BankOutlined,
  BellOutlined,
  BookOutlined,
  CloudServerOutlined,
  CreditCardOutlined,
  FolderOutlined,
  HomeOutlined,
  InboxOutlined,
  KeyOutlined,
  MessageOutlined,
  SafetyOutlined,
  SyncOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';

/** manifest 里的 icon 是字符串，按此映射成 ReactNode；侧栏菜单与工作台常用入口共用同一套，保持视觉一致。 */
const ICON_MAP: Record<string, ReactNode> = {
  home: <HomeOutlined />,
  api: <ApiOutlined />,
  key: <KeyOutlined />,
  book: <BookOutlined />,
  team: <TeamOutlined />,
  user: <UserOutlined />,
  apartment: <ApartmentOutlined />,
  app: <AppstoreOutlined />,
  message: <MessageOutlined />,
  inbox: <InboxOutlined />,
  folder: <FolderOutlined />,
  safety: <SafetyOutlined />,
  bank: <BankOutlined />,
  'credit-card': <CreditCardOutlined />,
  cloud: <CloudServerOutlined />,
  bell: <BellOutlined />,
  sync: <SyncOutlined />,
  audit: <AuditOutlined />,
};

export function resolveIcon(name?: string): ReactNode | undefined {
  if (!name) return undefined;
  return ICON_MAP[name] ?? <AppstoreOutlined />;
}
