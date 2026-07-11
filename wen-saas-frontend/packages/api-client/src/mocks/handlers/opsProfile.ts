import { http, HttpResponse } from 'msw';
import type { ProblemDetail } from '@wen-saas/domain';
import type {
  ChangePasswordRequest,
  NotificationCategory,
  NotificationCategoryGroup,
  NotificationChannel,
  NotificationChannelState,
  NotificationPreferenceView,
  OpsLoginLogPageView,
  UpdateNotificationPreferencesRequest,
  UpdateOpsProfileRequest,
} from '../../opsProfile';
import { MOCK_CURRENT_SESSION_ID, MOCK_STEP_UP_TOKEN, state } from '../data';

const base = '/api/v1/ops/profile';

function problem(status: number, title: string, detail: string, code: string) {
  const body: ProblemDetail = { title, status, detail, code };
  return HttpResponse.json(body, { status });
}

function validation(fieldErrors: { field: string; message: string }[]) {
  const body: ProblemDetail = {
    title: '表单字段不通过',
    status: 422,
    detail: '请检查表单',
    code: 'VALIDATION_FAILED',
    errors: fieldErrors,
  };
  return HttpResponse.json(body, { status: 422 });
}

const CATEGORY_LABEL: Record<NotificationCategory, string> = {
  TODO_TASK: '待办与任务',
  TICKET_ALERT: '工单告警',
  CUSTOMER_HEALTH: '客户健康度',
  PACKAGE_EXPIRY: '套餐到期',
  SYSTEM_ANNOUNCEMENT: '系统公告',
  SECURITY_ALERT: '安全提醒',
};

const CHANNEL_LABEL: Record<NotificationChannel, string> = {
  STATION: '站内信',
  EMAIL: '企业邮箱',
  SMS: '短信',
  WECHAT: '微信',
  WECOM: '企业微信',
  DINGTALK: '钉钉',
};

/** 后端只对外暴露 STATION/EMAIL/SMS 三个通道；其余 mock 模式下省略，与前端 UI 一致。 */
const EXPOSED_CHANNELS: NotificationChannel[] = ['STATION', 'EMAIL', 'SMS'];

const CATEGORY_ORDER: NotificationCategory[] = [
  'TODO_TASK',
  'TICKET_ALERT',
  'CUSTOMER_HEALTH',
  'PACKAGE_EXPIRY',
  'SYSTEM_ANNOUNCEMENT',
  'SECURITY_ALERT',
];

function buildPreferenceView(): NotificationPreferenceView {
  const categories: NotificationCategoryGroup[] = CATEGORY_ORDER.map((cat) => {
    const channels: NotificationChannelState[] = EXPOSED_CHANNELS.map((ch) => ({
      channel: ch,
      label: CHANNEL_LABEL[ch],
      enabled: ch === 'STATION' ? true : (state.opsNotifyPrefs[cat]?.[ch] ?? false),
      locked: ch === 'STATION',
    }));
    return {
      category: cat,
      label: CATEGORY_LABEL[cat],
      channels,
    };
  });
  return { categories };
}

function isStrongEnough(pw: string): boolean {
  if (pw.length < 8) return false;
  let kinds = 0;
  if (/[a-z]/.test(pw)) kinds++;
  if (/[A-Z]/.test(pw)) kinds++;
  if (/\d/.test(pw)) kinds++;
  if (/[^A-Za-z0-9]/.test(pw)) kinds++;
  return kinds >= 3;
}

export const opsProfileHandlers = [
  // 个人中心按 tab 拆只读接口，前端切到哪个 tab 拉哪个。
  http.get(`${base}/basic`, () => HttpResponse.json(state.opsProfile.basic)),
  http.get(`${base}/teams-roles`, () => HttpResponse.json(state.opsProfile.teamsRoles)),
  http.get(`${base}/security`, () => HttpResponse.json(state.opsProfile.security)),
  http.get(`${base}/sso`, () => HttpResponse.json(state.opsProfile.sso)),

  http.put(base, async ({ request }) => {
    const body = (await request.json()) as UpdateOpsProfileRequest;
    if (!body.realName || body.realName.trim() === '') {
      return validation([{ field: 'realName', message: '姓名不能为空' }]);
    }
    if (body.realName.length > 64) {
      return validation([{ field: 'realName', message: '姓名最长 64 个字符' }]);
    }
    const prev = state.opsProfile.basic ?? {};
    const nextGender = body.gender ?? prev.gender;
    const nextBirthday = body.birthday ?? prev.birthday;
    const nextAvatarId = body.avatarFileId ?? prev.avatarFileId;
    state.opsProfile.basic = {
      ...prev,
      realName: body.realName,
      ...(nextGender != null ? { gender: nextGender } : {}),
      ...(nextBirthday != null ? { birthday: nextBirthday } : {}),
      ...(nextAvatarId != null ? { avatarFileId: nextAvatarId } : {}),
    };
    return new HttpResponse(null, { status: 204 });
  }),

  http.post(`${base}/avatar`, async ({ request }) => {
    const form = await request.formData();
    const file = form.get('file');
    if (!(file instanceof File)) {
      return validation([{ field: 'file', message: '请选择头像文件' }]);
    }
    if (file.size > 1024 * 1024) {
      return problem(422, '头像无效', '头像文件大小不能超过 1MB', 'INVALID_AVATAR');
    }
    if (!['image/png', 'image/jpeg', 'image/jpg'].includes(file.type)) {
      return problem(422, '头像无效', '头像仅支持 JPG / PNG 格式', 'INVALID_AVATAR');
    }
    const avatarFileId = `mock-avatar-${Date.now()}`;
    const avatarUrl = `https://mock-files.local/${avatarFileId}`;
    state.opsProfile.basic = { ...(state.opsProfile.basic ?? {}), avatarFileId, avatarUrl };
    return HttpResponse.json({ avatarFileId, avatarUrl });
  }),

  http.post(`${base}/change-password`, async ({ request }) => {
    const body = (await request.json()) as ChangePasswordRequest;
    if (!body.stepUpToken || !body.newPassword) {
      return validation([
        ...(!body.stepUpToken ? [{ field: 'stepUpToken', message: '请先验证身份' }] : []),
        ...(!body.newPassword ? [{ field: 'newPassword', message: '新密码必填' }] : []),
      ]);
    }
    if (body.stepUpToken !== MOCK_STEP_UP_TOKEN) {
      return problem(401, '身份验证失败', '身份验证已过期，请重新验证', 'TICKET_INVALID');
    }
    if (!isStrongEnough(body.newPassword)) {
      return validation([
        { field: 'newPassword', message: '密码至少 8 位，包含大写/小写/数字/符号中的 3 种' },
      ]);
    }
    if (body.newPassword.length > 128) {
      return validation([{ field: 'newPassword', message: '新密码最长 128 位' }]);
    }
    // 后端会强制全下线（含当前），mock 同步清掉所有会话便于观察。
    state.opsSessions = [];
    return new HttpResponse(null, { status: 204 });
  }),

  http.get(`${base}/sessions`, () => HttpResponse.json(state.opsSessions)),

  http.delete(`${base}/sessions/:sessionId`, ({ params }) => {
    const sid = String(params.sessionId);
    if (sid === MOCK_CURRENT_SESSION_ID) {
      return problem(409, '冲突', '当前会话不可下线', 'CURRENT_SESSION_FORBIDDEN');
    }
    const idx = state.opsSessions.findIndex((s) => s.sessionId === sid);
    if (idx < 0) {
      return problem(404, '资源不存在', `会话 ${sid} 不存在`, 'SESSION_NOT_FOUND');
    }
    state.opsSessions.splice(idx, 1);
    return new HttpResponse(null, { status: 204 });
  }),

  http.post(`${base}/sessions/revoke-others`, () => {
    state.opsSessions = state.opsSessions.filter((s) => s.current === true);
    return new HttpResponse(null, { status: 204 });
  }),

  http.get(`${base}/login-logs`, ({ request }) => {
    const url = new URL(request.url);
    const page = Math.max(1, Number(url.searchParams.get('page') ?? '1'));
    // wire 参数名已统一为 size（boot PageQuery 规范，默认 20）。
    const pageSize = Math.max(1, Math.min(200, Number(url.searchParams.get('size') ?? '20')));
    const total = state.opsLoginLogs.length;
    const start = (page - 1) * pageSize;
    const records = state.opsLoginLogs.slice(start, start + pageSize);
    const view: OpsLoginLogPageView = { records, total, page, size: pageSize };
    return HttpResponse.json(view);
  }),

  http.get(`${base}/notification-preferences`, () => HttpResponse.json(buildPreferenceView())),

  http.put(`${base}/notification-preferences`, async ({ request }) => {
    const body = (await request.json()) as UpdateNotificationPreferencesRequest;
    for (const item of body.preferences ?? []) {
      if (!item.category || !item.channel) continue;
      // STATION 强制开启，忽略前端误送的 false。
      if (item.channel === 'STATION') continue;
      const bucket = state.opsNotifyPrefs[item.category];
      if (bucket) bucket[item.channel] = item.enabled === true;
    }
    return new HttpResponse(null, { status: 204 });
  }),
];
