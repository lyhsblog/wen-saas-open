import axios, { AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios';
import type { ProblemDetail } from '@wen-saas/domain';

/** 对外暴露的 HTTP 客户端类型；业务方按此 type 引用，不直接依赖 axios。 */
export type HttpClient = AxiosInstance;

export type AuthSnapshot = {
  token: string | null;
  tenantId: string | null;
};

export type ApiClientOptions = {
  baseURL: string;
  getAuth: () => AuthSnapshot;
  /** 旧行为：认证端点 401 / 未配置刷新时调用（跳登录）。 */
  onUnauthorized?: () => void;
  /**
   * 刷新成功后回调，把新 access token + user 写回登录态 store。
   * 提供此回调即开启「401 静默刷新」：业务请求收 401 时先静默 /api/auth/refresh，成功则透明重放。
   */
  onAccessToken?: (token: string, user: unknown) => void;
  /**
   * 静默刷新失败（refresh 过期/无效/被重放）时调用。用于唤起「重认证锁定浮层」；
   * 未提供则回退 onUnauthorized。
   */
  onReauthRequired?: () => void;
};

/** 422 字段级错误映射：field -> 第一条 message。 */
export type FieldErrors = Record<string, string>;

export class ApiError extends Error {
  constructor(
    public status: number,
    public problem: ProblemDetail,
    public requestId: string | undefined,
  ) {
    super(problem.detail ?? problem.title ?? `HTTP ${status}`);
    this.name = 'ApiError';
  }

  /** 提取后端 ProblemDetail.errors[] 为 field -> message 映射，供 RHF / AntD Form 直接挂内联错误。 */
  get fieldErrors(): FieldErrors {
    const out: FieldErrors = {};
    for (const e of this.problem.errors ?? []) {
      if (e.field && e.message && !(e.field in out)) out[e.field] = e.message;
    }
    return out;
  }

  isUnauthorized(): boolean {
    return this.status === 401;
  }
  isForbidden(): boolean {
    return this.status === 403;
  }
  isNotFound(): boolean {
    return this.status === 404;
  }
  isConflict(): boolean {
    return this.status === 409;
  }
  isValidation(): boolean {
    return this.status === 422;
  }
  isRateLimited(): boolean {
    return this.status === 429;
  }
  isServerError(): boolean {
    return this.status >= 500;
  }
  /** 网络中断 / CORS 失败等，axios 拿不到 response。 */
  isNetwork(): boolean {
    return this.status === 0;
  }
}

function generateRequestId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `req-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

/** 续期端点（白名单 @PublicEndpoint）；凭 httpOnly refresh cookie 续期。 */
const REFRESH_PATH = '/api/auth/refresh';

export function createApiClient(opts: ApiClientOptions): HttpClient {
  const instance = axios.create({
    baseURL: opts.baseURL,
    timeout: 30_000,
    headers: { 'Content-Type': 'application/json' },
    // 带上 httpOnly refresh cookie（同源部署，SameSite=Strict 生效）。
    withCredentials: true,
  });

  instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const { token, tenantId } = opts.getAuth();
    const requestId = generateRequestId();
    config.headers.set('X-Request-ID', requestId);
    if (token) config.headers.set('Authorization', `Bearer ${token}`);
    if (tenantId) config.headers.set('X-Tenant-Id', tenantId);
    // FormData（文件上传）需让浏览器自动带 multipart/form-data + boundary；
    // 实例默认的 application/json 会压住 axios 的自动判定，这里显式移除。
    if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
      config.headers.delete('Content-Type');
    }
    return config;
  });

  // ===== 401 静默刷新编排：进程内单飞行 + 跨标签页 Web Locks 串行 + 队列重放 =====
  const canRefresh = (): boolean => !!opts.onAccessToken;
  let refreshing: Promise<boolean> | null = null;

  // 真正打 /api/auth/refresh（用裸 axios，避免走本实例拦截器递归；withCredentials 带 refresh cookie）。
  const refreshOnce = (): Promise<boolean> =>
    axios
      .post(`${opts.baseURL}${REFRESH_PATH}`, null, { withCredentials: true })
      .then((res) => {
        const data = res.data as { token?: string; user?: unknown };
        if (data?.token) {
          opts.onAccessToken?.(data.token, data.user);
          return true;
        }
        return false;
      })
      .catch(() => false);

  /** 单飞行刷新；tokenBeforeRefresh 为失败请求所用 token，进锁后若已被别的标签页换新则直接复用。 */
  const refreshTokens = (tokenBeforeRefresh: string | null): Promise<boolean> => {
    if (refreshing) return refreshing;
    const run = (): Promise<boolean> => {
      const current = opts.getAuth().token;
      if (current && current !== tokenBeforeRefresh) return Promise.resolve(true);
      return refreshOnce();
    };
    const withLock = async (): Promise<boolean> => {
      // 跨标签页串行：避免多页并发刷新互相轮换触发后端「重用检测」误杀会话。
      if (typeof navigator !== 'undefined' && navigator.locks?.request) {
        let ok = false;
        await navigator.locks.request('wen-auth-refresh', async () => {
          ok = await run();
        });
        return ok;
      }
      return run();
    };
    refreshing = withLock().finally(() => {
      refreshing = null;
    });
    return refreshing;
  };

  const reauth = (): void => {
    if (opts.onReauthRequired) opts.onReauthRequired();
    else opts.onUnauthorized?.();
  };

  instance.interceptors.response.use(
    (res) => res,
    (error: AxiosError<ProblemDetail>) => {
      const status = error.response?.status ?? 0;
      const requestId =
        (error.response?.headers?.['x-request-id'] as string | undefined) ??
        (error.config?.headers?.get?.('X-Request-ID') as string | undefined);
      const problem: ProblemDetail = error.response?.data ?? {
        title: error.message,
        status,
      };

      const cfg = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;
      const url = cfg?.url ?? '';
      const isAuthEndpoint = url.includes('/api/auth/');

      if (status === 401 && cfg && canRefresh() && !isAuthEndpoint && !cfg._retried) {
        const usedAuth = cfg.headers?.get?.('Authorization') as string | undefined;
        const before = opts.getAuth().token;
        // 当前 token 已与失败请求所用不同 → 已被刷新 → 直接重放，无需再刷。
        if (before && usedAuth && usedAuth !== `Bearer ${before}`) {
          cfg._retried = true;
          return instance(cfg);
        }
        return refreshTokens(before).then((ok) => {
          if (ok) {
            cfg._retried = true;
            return instance(cfg);
          }
          reauth();
          return Promise.reject(new ApiError(status, problem, requestId));
        });
      }

      // 认证端点 401（如登录失败）/ 未配置刷新 → 旧行为。
      if (status === 401 && (isAuthEndpoint || !canRefresh())) opts.onUnauthorized?.();
      return Promise.reject(new ApiError(status, problem, requestId));
    },
  );

  return instance;
}

/**
 * 把 ApiError 统一翻译成 toast 文案。
 * - 422：提示「请检查表单」让调用方知道还需要消费 fieldErrors；
 * - 429：提示稍后重试；
 * - 网络异常：固定文案；
 * - 所有分支统一附「· 请求编号 {requestId}」便于用户报障时运维定位（无 requestId 则不拼）。
 */
export function describeApiError(err: unknown, fallback = '请求失败'): string {
  if (!(err instanceof ApiError)) return fallback;
  const base = err.problem.detail ?? err.problem.title ?? fallback;
  let text: string;
  if (err.isNetwork()) text = '网络异常，请检查连接后重试';
  else if (err.isRateLimited()) text = `${base} · 请稍后重试`;
  else if (err.isValidation()) text = `${base} · 请检查表单`;
  else text = base;
  return err.requestId ? `${text} · 请求编号 ${err.requestId}` : text;
}
