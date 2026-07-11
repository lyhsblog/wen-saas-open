import { expect, test } from '@playwright/test';
import { login } from './helpers';

test.describe('字典管理（MSW mock）', () => {
  test('登录后经侧栏进入字典页，渲染种子分组', async ({ page }) => {
    await login(page);

    // 走 SPA 内导航（点侧栏菜单），贴近真实用户流；不做整页 goto。
    // 侧栏项是自定义 div（非 antd menuitem role），用导航区内文本定位。
    await page.getByRole('navigation').getByText('字典管理').click();
    await expect(page).toHaveURL(/\/system\/dictionaries/);

    // 左侧「字典分组」面板 + mock 种子分组「项目阶段」（见 mocks/data.ts seedGroups）。
    await expect(page.getByText('字典分组')).toBeVisible();
    await expect(page.getByText('项目阶段').first()).toBeVisible();
  });
});
