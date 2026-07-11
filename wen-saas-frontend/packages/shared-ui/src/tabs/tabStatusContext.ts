import { createContext, useContext, useEffect, useMemo } from 'react';

/** 当前页面所属页签的 key（由 KeepAlivePanes 逐面板注入）。 */
export const TabKeyContext = createContext<string | null>(null);

/** 设置任意页签脏标记的能力（由承载页签的外壳注入，通常接控制器的 setDirty）。 */
export const TabSetDirtyContext = createContext<((key: string, dirty: boolean) => void) | null>(
  null,
);

/** 本页面所属页签当前是否为激活态（由 KeepAlivePanes 逐面板注入；非页签场景默认 true）。 */
export const TabActiveContext = createContext<boolean>(true);

export type UseTabStatus = {
  /** 本页面所属页签 key；不在页签内时为 null。 */
  tabKey: string | null;
  /** 标记本页签「未保存」脏态：进入编辑置 true、保存/重置后置 false。页签外调用为安全空操作。 */
  setDirty: (dirty: boolean) => void;
};

/**
 * 供页面组件标记「自己所在页签」的脏状态。例：表单 `onValuesChange` 里 `setDirty(true)`、
 * 提交成功后 `setDirty(false)`。即便页签被切到后台（保活），标记依旧保留，关闭前会二次确认。
 */
export function useTabStatus(): UseTabStatus {
  const tabKey = useContext(TabKeyContext);
  const setDirtyFn = useContext(TabSetDirtyContext);
  return useMemo<UseTabStatus>(
    () => ({
      tabKey,
      setDirty: (dirty: boolean) => {
        if (tabKey && setDirtyFn) setDirtyFn(tabKey, dirty);
      },
    }),
    [tabKey, setDirtyFn],
  );
}

/**
 * 页面打开了临时浮层（Modal/Drawer）时调用：所在页签被切到后台（保活但不再激活）时
 * 自动关闭，避免路由切到其它页签后弹窗仍悬浮在新页面上方。页签外调用为安全空操作。
 */
export function useCloseOnTabLeave(open: boolean, onClose: () => void): void {
  const active = useContext(TabActiveContext);
  useEffect(() => {
    if (!active && open) onClose();
  }, [active, open, onClose]);
}
