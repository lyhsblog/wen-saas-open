import type { ReactNode } from 'react';
import { TabActiveContext, TabKeyContext, TabSetDirtyContext } from './tabStatusContext';

export function TabKeyProvider({ value, children }: { value: string; children: ReactNode }) {
  return <TabKeyContext.Provider value={value}>{children}</TabKeyContext.Provider>;
}

export function TabActiveProvider({ value, children }: { value: boolean; children: ReactNode }) {
  return <TabActiveContext.Provider value={value}>{children}</TabActiveContext.Provider>;
}

export function TabStatusProvider({
  setDirty,
  children,
}: {
  setDirty: (key: string, dirty: boolean) => void;
  children: ReactNode;
}) {
  return <TabSetDirtyContext.Provider value={setDirty}>{children}</TabSetDirtyContext.Provider>;
}
