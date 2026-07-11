import { type ReactNode, useEffect, useState } from 'react';
import { Watermark } from 'antd';
import { useImpersonation, useScreenshotWatermark } from './capabilities';

export type WatermarkLayerProps = {
  /** 当前登录用户名（来自各端 authStore，水印内容用）。 */
  username?: string | null;
  children: ReactNode;
};

/** 'YYYY-MM-DD HH:mm'（本地时区，分钟精度）。 */
function formatMinute(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

/**
 * 全页水印层。包裹 AppShell 内容区，按当前会话态渲染单层 antd `Watermark`：
 *
 * - **代登录态**：更显眼的带色警示水印（红橙调、较大字号），内容含「代登录·操作人·当前用户」，
 *   与顶部红色标识条配合强提示「正处于运营代登录」。
 * - **防截屏水印**（普通登录态且租户开关开启）：小而浅的可溯源底纹，内容 = 真实姓名 + 用户名 +
 *   实时时间（分钟级刷新），可溯源到人而不喧宾夺主。
 * - 两者互斥（代登录优先，其内容本身已可溯源），始终单层 Watermark，避免嵌套。
 */
export function WatermarkLayer({ username, children }: WatermarkLayerProps) {
  const { impersonating, operatorName } = useImpersonation();
  const { enabled: screenshotEnabled, realName } = useScreenshotWatermark();
  const [now, setNow] = useState(() => new Date());

  // 实时刷新：60s tick 保持分钟级新鲜，可溯源到截屏时刻；单个低频定时器，重渲染开销可忽略。
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 60_000);
    return () => window.clearInterval(timer);
  }, []);

  if (impersonating) {
    // 代登录水印：暖灰红、低透明、细字密铺 + 固定倾角，可溯源到操作人/被代登录人而不喧宾夺主
    // （强提示交给顶部红色标识条 ImpersonationBanner）。
    return (
      <Watermark
        content={['代登录', operatorName ?? '', username ?? ''].filter(Boolean)}
        font={{ color: 'rgba(190, 50, 50, 0.10)', fontSize: 13, fontWeight: 400 }}
        rotate={-22}
        gap={[96, 96]}
        style={{ minHeight: '100%' }}
      >
        {children}
      </Watermark>
    );
  }

  if (screenshotEnabled) {
    const who = [realName, username ?? '']
      .map((s) => s.trim())
      .filter(Boolean)
      .join(' ');
    return (
      <Watermark
        content={[who, formatMinute(now)].filter(Boolean)}
        font={{ color: 'rgba(0, 0, 0, 0.045)', fontSize: 12 }}
        gap={[140, 120]}
        style={{ minHeight: '100%' }}
      >
        {children}
      </Watermark>
    );
  }

  return <>{children}</>;
}
