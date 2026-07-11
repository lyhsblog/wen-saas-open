import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { Brand } from '@wen-saas/design-system';

type UiPrefState = {
  brand: Brand;
  setBrand: (brand: Brand) => void;
};

const STORAGE_KEY = 'wen-saas:tenant-ui-pref';

/**
 * 界面偏好（当前仅主题色）。设备级偏好、localStorage 持久化，点选即生效不设「保存」。
 * 消费方：App 根据 brand 驱动 applyBrand(CSS 变量侧) + buildTenantTheme(AntD 侧)。
 */
export const useUiPrefStore = create<UiPrefState>()(
  persist(
    (set) => ({
      brand: 'blue',
      setBrand: (brand) => set({ brand }),
    }),
    {
      name: STORAGE_KEY,
      storage: createJSONStorage(() => localStorage),
    },
  ),
);
