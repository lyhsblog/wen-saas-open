import { expect, test } from '@playwright/test';
import { login, MOCK_CREDS } from './helpers';

test.describe('租户登录（MSW mock）', () => {
  test('错误凭据 → 显示告警且停留在登录页', async ({ page }) => {
    await page.goto('/login');
    await page.getByPlaceholder('账号 / 手机号').fill(MOCK_CREDS.username);
    await page.getByPlaceholder('密码').fill('wrong-password');
    await page.getByRole('button', { name: /登\s*录/ }).click();

    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });

  test('正确凭据 → 进入工作台', async ({ page }) => {
    await login(page);
    await expect(page).toHaveURL(/\/workbench/);
  });
});
