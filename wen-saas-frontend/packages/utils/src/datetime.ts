import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';

dayjs.extend(utc);
dayjs.extend(timezone);

/**
 * 平台统一展示时区。后端时间字段带 +08:00（如 `2026-06-05T22:44:46.833593+08:00`），
 * 前端一律按上海时区落地展示、不再带时区后缀，保证跨浏览器时区一致。
 */
export const DISPLAY_TZ = 'Asia/Shanghai';

const EMPTY = '—';

/**
 * 日期时间展示（上海时区）：`2026-06-05 22:44:46`。
 * 入参为后端 ISO-8601 字符串；null / 非法值返回占位 `—`。
 */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return EMPTY;
  const d = dayjs(iso);
  if (!d.isValid()) return EMPTY;
  return d.tz(DISPLAY_TZ).format('YYYY-MM-DD HH:mm:ss');
}

/**
 * 分钟精度的日期时间展示（上海时区）：`2026-06-05 22:44`。
 * 用于列表等秒数无意义的紧凑场景；null / 非法值返回占位 `—`。
 */
export function formatDateTimeMinute(iso: string | null | undefined): string {
  if (!iso) return EMPTY;
  const d = dayjs(iso);
  if (!d.isValid()) return EMPTY;
  return d.tz(DISPLAY_TZ).format('YYYY-MM-DD HH:mm');
}

/**
 * 仅日期展示（上海时区）：`2026-06-05`。
 * 用于到期日 / 生日等天粒度字段；null / 非法值返回占位 `—`。
 */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return EMPTY;
  const d = dayjs(iso);
  if (!d.isValid()) return EMPTY;
  return d.tz(DISPLAY_TZ).format('YYYY-MM-DD');
}

/**
 * 相对时间（移动端列表用）：刚刚 / N 分钟前 / N 小时前 / `M-D`（超一天落到上海时区日期）。
 * 空 / 非法值返回空串（列表场景占位符会显得突兀）。
 */
export function formatRelative(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) return '';
  const d = dayjs(iso);
  if (!d.isValid()) return '';
  const diff = now - d.valueOf();
  if (diff < 60_000) return '刚刚';
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`;
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`;
  return d.tz(DISPLAY_TZ).format('M-D');
}

/**
 * 相对时间（桌面列表用，如工单最近动态）：刚刚 / N 分钟前 / N 小时前 / N 天前（30 天内），
 * 更早落到上海时区日期 `YYYY-MM-DD`。空 / 非法值返回占位 `—`。
 */
export function fromNow(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) return EMPTY;
  const d = dayjs(iso);
  if (!d.isValid()) return EMPTY;
  const min = Math.floor((now - d.valueOf()) / 60_000);
  if (min < 1) return '刚刚';
  if (min < 60) return `${min} 分钟前`;
  const h = Math.floor(min / 60);
  if (h < 24) return `${h} 小时前`;
  const day = Math.floor(h / 24);
  if (day < 30) return `${day} 天前`;
  return d.tz(DISPLAY_TZ).format('YYYY-MM-DD');
}

/**
 * 紧凑日期时间（便签卡片等省年份场景，上海时区）：`MM-DD HH:mm`。
 * 空 / 非法值返回空串。
 */
export function formatDateTimeCompact(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = dayjs(iso);
  if (!d.isValid()) return '';
  return d.tz(DISPLAY_TZ).format('MM-DD HH:mm');
}

/**
 * 当天感知时间（工作台动态等）：同一天（上海时区口径）显示「今天 HH:mm」，
 * 否则落到日期 `YYYY-MM-DD`。空 / 非法值返回占位 `—`。
 */
export function formatTodayOrDate(
  iso: string | null | undefined,
  now: number = Date.now(),
): string {
  if (!iso) return EMPTY;
  const d = dayjs(iso);
  if (!d.isValid()) return EMPTY;
  const zoned = d.tz(DISPLAY_TZ);
  return zoned.isSame(dayjs(now).tz(DISPLAY_TZ), 'day')
    ? `今天 ${zoned.format('HH:mm')}`
    : zoned.format('YYYY-MM-DD');
}
