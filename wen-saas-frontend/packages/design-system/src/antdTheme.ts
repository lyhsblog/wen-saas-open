import type { ThemeConfig } from 'antd';
import type { Brand } from './brand';

/**
 * Tokens 与 `tokens.css` 保持手工同步——AntD theme 不能 build 期消费 CSS 变量。
 * 改色调/圆角/字号时，**同时改本文件和 tokens.css**。
 *
 * 字段命名对齐 antd v5 design tokens（见 antd Design Token 文档）。
 */

// ─────────────── 与 tokens.css 一对一 ───────────────

const COLOR = {
  brand: '#2B5FFF',
  brandHover: '#1F4DE0',
  brandActive: '#1A3FB8',
  brandBg: '#EEF3FF',

  info: '#2563EB',
  success: '#16A34A',
  warning: '#D97706',
  danger: '#DC2626',

  // neutral palette（与 --bg / --bg-elev / --bg-muted / --border 等一致）
  bg: '#F5F7FA',
  bgElev: '#FFFFFF',
  bgMuted: '#F8FAFC',
  border: '#E5E8EE',
  borderStrong: '#D4D8E0',
  divider: '#EEF0F4',

  text: '#1A1F2E',
  textSecondary: '#4B5365',
  textTertiary: '#6B7280',
  textDisabled: '#B5BAC6',
};

const FONT_FAMILY =
  '"PingFang SC", "Inter", -apple-system, BlinkMacSystemFont, "Helvetica Neue", "Microsoft YaHei", sans-serif';

/** 通用 token 集合，对齐 tokens.css 的颜色 / 字号 / 圆角 / 阴影。 */
const baseTokens = {
  // 主色与状态色
  colorPrimary: COLOR.brand,
  colorInfo: COLOR.info,
  colorSuccess: COLOR.success,
  colorWarning: COLOR.warning,
  colorError: COLOR.danger,

  // 文本层级
  colorTextBase: COLOR.text,
  colorText: COLOR.text,
  colorTextSecondary: COLOR.textSecondary,
  colorTextTertiary: COLOR.textTertiary,
  colorTextQuaternary: COLOR.textDisabled,

  // 背景层级（影响 Layout 背景、Card 背景、Table label 等）
  colorBgLayout: COLOR.bg,
  colorBgContainer: COLOR.bgElev,
  colorBgElevated: COLOR.bgElev,
  colorBgSpotlight: COLOR.bgMuted,
  // antd 在 Descriptions label / Table header / hover 行 等位置使用 colorFillAlter
  colorFillAlter: COLOR.bgMuted,
  // colorFillSecondary 影响 Tag 的灰色背景等
  colorFillSecondary: COLOR.bgMuted,
  colorFillTertiary: COLOR.divider,

  // 边框（普通 / 强 / 分隔）
  colorBorder: COLOR.border,
  colorBorderSecondary: COLOR.divider,

  // 圆角层次 —— 与 --r-sm/--r-md/--r-lg/--r-xl 对齐
  borderRadiusXS: 2,
  borderRadiusSM: 4,
  borderRadius: 6,
  borderRadiusLG: 8,

  // 字号层次 —— 与 --font-xs/--font-sm/--font-base/--font-md/--font-lg 对齐
  fontSize: 14,
  fontSizeSM: 13,
  fontSizeLG: 16,
  fontSizeXL: 18,
  fontSizeHeading1: 28,
  fontSizeHeading2: 22,
  fontSizeHeading3: 18,
  fontSizeHeading4: 16,
  fontSizeHeading5: 15,

  fontFamily: FONT_FAMILY,

  // 阴影 —— 与 --shadow-md / --shadow-pop 对齐
  boxShadow: '0 2px 8px rgba(15,23,42,0.06), 0 1px 2px rgba(15,23,42,0.04)',
  boxShadowSecondary: '0 8px 24px rgba(15,23,42,0.10), 0 2px 4px rgba(15,23,42,0.04)',
  boxShadowTertiary: '0 1px 2px rgba(15,23,42,0.04), 0 1px 1px rgba(15,23,42,0.03)',

  // 控件高度（密度由 ops/tenant 覆盖）
  controlHeightLG: 38,
  controlHeightSM: 26,

  // 链接色
  colorLink: COLOR.brand,
  colorLinkHover: COLOR.brandHover,
  colorLinkActive: COLOR.brandActive,
};

/** 组件级 token —— 对齐 wa-* 组件样式。 */
const baseComponents = {
  // 按钮：稍紧的视觉、强 hover
  Button: {
    primaryShadow: 'none',
    defaultShadow: 'none',
    fontWeight: 500,
  },
  // Card：圆角 8px、淡阴影
  Card: {
    borderRadiusLG: 8,
    paddingLG: 20,
    headerBg: 'transparent',
    headerFontSize: 15,
  },
  // Descriptions：label 列用 bgMuted 而不是 antd 默认的 colorFillAlter（确保偏暖白）
  Descriptions: {
    labelBg: COLOR.bgMuted,
    titleMarginBottom: 12,
    itemPaddingBottom: 12,
  },
  // Table：表头使用 bgMuted；行 hover 使用 brand-bg 浅色
  Table: {
    headerBg: COLOR.bgMuted,
    headerColor: COLOR.textSecondary,
    rowHoverBg: COLOR.bgMuted,
    rowSelectedBg: COLOR.brandBg,
    rowSelectedHoverBg: COLOR.brandBg,
    borderColor: COLOR.divider,
  },
  // Tag：圆角 4px，与 StatusPill / CountChip 徽章形态一致
  Tag: {
    borderRadiusSM: 4,
    defaultBg: COLOR.bgMuted,
    defaultColor: COLOR.textSecondary,
  },
  // Modal：圆角 12px，与卡片圆角体系一致
  Modal: {
    borderRadiusLG: 12,
    headerBg: 'transparent',
    titleFontSize: 16,
  },
  // Tabs：active 品牌色 + 下划线，12px 纵向留白
  Tabs: {
    inkBarColor: COLOR.brand,
    itemSelectedColor: COLOR.brand,
    itemActiveColor: COLOR.brand,
    itemHoverColor: COLOR.text,
    titleFontSize: 14,
    horizontalItemPadding: '12px 0',
    horizontalItemGutter: 24,
  },
  // Menu（侧栏）：留给 ops/tenant 各自覆盖
  Menu: {
    itemBorderRadius: 6,
  },
  // Input/Select 聚焦光圈与品牌色一致
  Input: {
    activeShadow: `0 0 0 3px ${COLOR.brandBg}`,
  },
  Select: {
    optionSelectedBg: COLOR.brandBg,
  },
  // Tooltip 圆角与阴影对齐。
  // 注意：全局 colorBgSpotlight 被设成了浅色 bgMuted（供别处用），但 antd Tooltip
  // 默认用 colorBgSpotlight 当深色底 + 白字，会变成"白底白字"看似空白；这里把 Tooltip
  // 的底色单独覆盖回深色，保证提示文案可读。
  Tooltip: {
    borderRadius: 6,
    colorBgSpotlight: COLOR.text,
    colorTextLightSolid: '#fff',
  },
  // Divider
  Divider: {
    colorSplit: COLOR.divider,
  },
};

// ─────────────── 品牌化 ───────────────

/** 品牌色板 —— hex 与 tokens.css 的 `[data-brand=...]` 变体一一对应，改一处必须同步另一处。 */
export const BRAND_MAP: Record<
  Brand,
  { primary: string; hover: string; active: string; bg: string; bgStrong: string }
> = {
  blue: {
    primary: '#2B5FFF',
    hover: '#1F4DE0',
    active: '#1A3FB8',
    bg: '#EEF3FF',
    bgStrong: '#DCE5FF',
  },
  indigo: {
    primary: '#5B5BD6',
    hover: '#4949C4',
    active: '#3E3EAB',
    bg: '#EEEEFD',
    bgStrong: '#DDDDFB',
  },
  teal: {
    primary: '#0E7C66',
    hover: '#0B6555',
    active: '#084E42',
    bg: '#E6F5F1',
    bgStrong: '#C9EAE0',
  },
  slate: {
    primary: '#1F2937',
    hover: '#111827',
    active: '#030712',
    bg: '#F1F5F9',
    bgStrong: '#E2E8F0',
  },
};

/**
 * 按品牌生成租户端主题：AntD 不消费 CSS 变量，运行时切换主题色必须让 ConfigProvider
 * 的 token 一并换（CSS 变量侧由 `applyBrand` 翻 data-brand 驱动，两条链路都走全站才一致）。
 * 语义色（success / warning / ...）不跟随品牌，只换 brand 一族。
 */
export function buildTenantTheme(brand: Brand): ThemeConfig {
  const c = BRAND_MAP[brand] ?? BRAND_MAP.blue;
  return {
    token: {
      ...baseTokens,
      colorPrimary: c.primary,
      colorLink: c.primary,
      colorLinkHover: c.hover,
      colorLinkActive: c.active,
    },
    components: {
      ...baseComponents,
      Table: {
        ...baseComponents.Table,
        rowSelectedBg: c.bg,
        rowSelectedHoverBg: c.bg,
      },
      Tabs: {
        ...baseComponents.Tabs,
        inkBarColor: c.primary,
        itemSelectedColor: c.primary,
        itemActiveColor: c.primary,
      },
      Input: {
        ...baseComponents.Input,
        activeShadow: `0 0 0 3px ${c.bg}`,
      },
      Select: {
        ...baseComponents.Select,
        optionSelectedBg: c.bg,
      },
    },
  };
}

// ─────────────── 三端 theme ───────────────

/** 租户后台：默认密度（44px 行高、留白舒适）、默认品牌蓝。运行时切换品牌用 buildTenantTheme。 */
export const tenantTheme: ThemeConfig = {
  token: baseTokens,
  components: baseComponents,
};

/** 运营后台：紧凑密度 + 深色侧栏依赖 CSS 变量 --ops-side-*。 */
export const opsTheme: ThemeConfig = {
  token: {
    ...baseTokens,
    controlHeight: 30,
  },
  components: {
    ...baseComponents,
    Menu: {
      ...baseComponents.Menu,
      itemHeight: 36,
    },
    Table: {
      ...baseComponents.Table,
      cellPaddingBlock: 6,
    },
  },
};
