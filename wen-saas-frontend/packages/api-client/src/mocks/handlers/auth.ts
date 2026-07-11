import { http, HttpResponse } from 'msw';
import type { LoginRequest, LoginResponse, ProblemDetail } from '@wen-saas/domain';
import { demoUser, validCredentials } from '../data';

const FAKE_TOKEN = 'mock-jwt-token-for-alice';

export const authHandlers = [
  http.post('/api/auth/login', async ({ request }) => {
    const body = (await request.json()) as Partial<LoginRequest>;
    if (
      body.username !== validCredentials.username ||
      body.password !== validCredentials.password
    ) {
      const problem: ProblemDetail = {
        title: '账号或密码不正确',
        status: 401,
        detail: `Mock 凭据：${validCredentials.username} / ${validCredentials.password}`,
      };
      return HttpResponse.json(problem, { status: 401 });
    }
    const res: LoginResponse = { token: FAKE_TOKEN, expiresIn: 7200, user: demoUser };
    return HttpResponse.json(res);
  }),
  http.post('/api/auth/logout', () => HttpResponse.json(null)),
  http.get('/api/auth/me', ({ request }) => {
    const auth = request.headers.get('authorization');
    if (auth !== `Bearer ${FAKE_TOKEN}`) {
      const problem: ProblemDetail = { title: '未认证', status: 401 };
      return HttpResponse.json(problem, { status: 401 });
    }
    return HttpResponse.json(demoUser);
  }),
];
