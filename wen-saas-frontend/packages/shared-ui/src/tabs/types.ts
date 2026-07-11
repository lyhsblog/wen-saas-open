import type { ReactNode } from 'react';

/** 单个页签。`key` 在一组页签内唯一且稳定（通常用路由相对路径）。 */
export type TabItem = {
  /** 唯一稳定标识；切换 / 保活以它为准。 */
  key: string;
  /** 页签标题。 */
  title: ReactNode;
  /** 是否可关闭；默认 true。首页 / 工作台等设 false 以常驻。 */
  closable?: boolean;
  /**
   * 「未保存」脏标记。页面进入表单填写态后置 true，页签上以醒目圆点提示、关闭前二次确认，
   * 避免误关丢失填写内容；保存 / 重置后页面自行置回 false。
   */
  dirty?: boolean;
  /** 最近一次被激活的时间戳（ms）。用于「多久没访问」与 LRU 排序，由控制器维护。 */
  lastActiveAt?: number;
};

/** 右键菜单动作。 */
export type TabContextAction = 'closeOthers' | 'closeRight' | 'closeAll';
