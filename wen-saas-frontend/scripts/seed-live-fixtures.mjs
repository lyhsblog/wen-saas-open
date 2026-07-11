#!/usr/bin/env node
/**
 * live E2E 夹具种子（幂等）—— 经 8080 真后端 API 重建 tests/e2e/live/** 依赖的全部夹具。
 *
 * 既是 nightly（self-hosted runner）跑 live 前的 setup，也是本地联调「换库 / 清库后重建
 * 夹具账号」的脚本（夹具凭据仅本地联调环境有效）。已存在的对象一律跳过（按 code/username 探测），可反复跑。
 *
 * 建：BIZ_TEST_PKG 套餐 / ops-csm(OPS_CSM,名下 wmtest) /
 *     wmtest 租户 / uitest 租户 + uiadmin / member1(无权基线) /
 *     dictonly + DICT_READONLY 角色(dict.group.list+dict.item.list, DEPT_AND_BELOW)。
 *
 * 前置：8080 后端在跑（admin@ops/admin123 与 OPS_CSM 角色均由 Flyway 种子）。
 *       用法：`node scripts/seed-live-fixtures.mjs`。
 */

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';
const PASSWORD = 'Test@2026!';
const OPS_SUPER = { username: 'admin@ops', password: 'admin123' };

let created = 0;
let skipped = 0;
const log = (msg) => console.log(`  ${msg}`);

async function api(method, path, { token, body } = {}) {
  const res = await fetch(`${BACKEND}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  return res;
}

async function expectOk(res, what) {
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(`${what} 失败 HTTP ${res.status} ${text.slice(0, 200)}`);
  }
  return res;
}

/** 列表响应取数组（兼容 {items} / {records} / 裸数组）。 */
function pluck(body) {
  if (Array.isArray(body)) return body;
  return body?.items ?? body?.records ?? [];
}

async function login(creds) {
  const res = await expectOk(
    await api('POST', '/api/auth/login', { body: creds }),
    `登录 ${creds.username}`,
  );
  return (await res.json()).token;
}

async function activate(token, username) {
  await expectOk(
    await api('POST', `/api/v1/public/reset-password/by-token/${token}`, {
      body: { usernameInput: username, newPassword: PASSWORD },
    }),
    `激活 ${username}`,
  );
}

// ── 套餐 ────────────────────────────────────────────────────────────────────
async function ensureBizPackage(ops) {
  const list = pluck(await (await api('GET', '/api/v1/ops/packages', { token: ops })).json());
  if (list.some((p) => p.code === 'BIZ_TEST_PKG')) {
    skipped++;
    return log('套餐 BIZ_TEST_PKG 已存在，跳过');
  }
  await expectOk(
    await api('POST', '/api/v1/ops/packages', {
      token: ops,
      body: {
        code: 'BIZ_TEST_PKG',
        name: 'IT 业务测试包',
        description: 'live E2E 夹具：无模块业务包',
        defaultTtlDays: 365,
        moduleCodes: [],
        publishImmediately: true,
      },
    }),
    '建套餐 BIZ_TEST_PKG',
  );
  created++;
  log('套餐 BIZ_TEST_PKG 已创建并上架');
}

// ── 运营成员 ──────────────────────────────────────────────────────────────────
async function opsRoleId(ops, code) {
  const list = pluck(await (await api('GET', '/api/v1/ops/team/roles', { token: ops })).json());
  const hit = list.find((r) => r.code === code);
  if (!hit) throw new Error(`运营预置角色 ${code} 不存在（应由 Flyway V1.21.0 种入）`);
  return hit.id;
}

async function opsMemberId(ops, username) {
  const list = pluck(
    await (await api('GET', '/api/v1/ops/team/members?page=1&size=200', { token: ops })).json(),
  );
  return list.find((m) => m.username === username)?.id;
}

async function ensureOpsMember(ops, username, roleCode, mobile) {
  const existing = await opsMemberId(ops, username);
  if (existing) {
    skipped++;
    log(`运营成员 ${username} 已存在，跳过`);
    return existing;
  }
  const roleId = await opsRoleId(ops, roleCode);
  await expectOk(
    await api('POST', '/api/v1/ops/team/members', {
      token: ops,
      body: {
        username,
        password: PASSWORD,
        realName: username,
        email: `${username}@example.com`,
        mobile,
        roleIds: [roleId],
      },
    }),
    `建运营成员 ${username}`,
  );
  created++;
  log(`运营成员 ${username}(${roleCode}) 已创建`);
  return opsMemberId(ops, username);
}

// ── 租户 ────────────────────────────────────────────────────────────────────
async function tenantIdByCode(ops, code) {
  const list = pluck(
    await (await api('GET', '/api/v1/ops/tenants?page=1&size=200', { token: ops })).json(),
  );
  return list.find((t) => t.code === code)?.id;
}

async function ensureTenant(ops, { code, name, adminUsername, adminMobile, csmUserId }) {
  if (await tenantIdByCode(ops, code)) {
    skipped++;
    log(`租户 ${code} 已存在，跳过`);
    return;
  }
  const res = await expectOk(
    await api('POST', '/api/v1/ops/tenants', {
      token: ops,
      body: {
        tenantName: name,
        tenantCode: code,
        adminUsername,
        adminRealName: `${adminUsername}-主管`,
        adminEmail: `${adminUsername}@example.com`,
        adminMobile,
        ...(csmUserId ? { csmUserId } : {}),
      },
    }),
    `开通租户 ${code}`,
  );
  const { resetToken } = await res.json();
  await activate(resetToken, adminUsername);
  created++;
  log(`租户 ${code} 已开通，主管 ${adminUsername} 已激活`);
}

// ── 租户内：部门 / 角色 / 成员 ───────────────────────────────────────────────
async function ensureDept(token, name) {
  const tree = pluck(await (await api('GET', '/api/v1/base/depts', { token })).json());
  const found = (Array.isArray(tree) ? tree : []).find((d) => d.name === name);
  if (found) return found.id;
  const res = await expectOk(
    await api('POST', '/api/v1/base/depts', { token, body: { name } }),
    `建部门 ${name}`,
  );
  log(`部门 ${name} 已创建`);
  return (await res.json()).id;
}

async function ensureRole(token, { code, name, dataScope, codes }) {
  const list = pluck(await (await api('GET', '/api/v1/base/roles', { token })).json());
  const existing = list.find((r) => r.code === code);
  if (existing) {
    skipped++;
    log(`租户角色 ${code} 已存在，跳过`);
    return existing.id ?? existing.roleId;
  }
  const res = await expectOk(
    await api('POST', '/api/v1/base/roles', {
      token,
      body: { code, name, description: 'live E2E 夹具角色', dataScope },
    }),
    `建租户角色 ${code}`,
  );
  const detail = await res.json();
  const roleId = detail.id ?? detail.roleId;
  await expectOk(
    await api('PUT', `/api/v1/base/roles/${roleId}/permissions`, { token, body: { codes } }),
    `给角色 ${code} 配权限`,
  );
  created++;
  log(`租户角色 ${code} 已创建并配权限(${codes.join(',')})`);
  return roleId;
}

async function ensureTenantMember(token, { username, realName, mobile, mainDeptId, roleIds }) {
  // 成员列表 username 是脱敏的（m***r1），按账号精确匹配不可靠；改「建-或-409」幂等。
  const res = await api('POST', '/api/v1/base/members', {
    token,
    body: { username, realName, mobile, mainDeptId, ...(roleIds?.length ? { roleIds } : {}) },
  });
  if (res.status === 409) {
    skipped++;
    return log(`租户成员 ${username} 已存在，跳过`);
  }
  await expectOk(res, `建租户成员 ${username}`);
  const { activationToken } = await res.json();
  await activate(activationToken, username);
  created++;
  log(`租户成员 ${username} 已创建并激活`);
}

async function main() {
  console.log(`[seed] 目标后端 ${BACKEND}`);
  const ops = await login(OPS_SUPER);

  console.log('[seed] 套餐');
  await ensureBizPackage(ops);

  console.log('[seed] 运营分角色成员');
  const csmUserId = await ensureOpsMember(ops, 'ops-csm', 'OPS_CSM', '13900000001');

  console.log('[seed] 租户（wmtest 绑 ops-csm 为客户经理 / uitest）');
  await ensureTenant(ops, {
    code: 'wmtest',
    name: '水印测试租户',
    adminUsername: 'wmadmin',
    adminMobile: '13900000011',
    csmUserId,
  });
  await ensureTenant(ops, {
    code: 'uitest',
    name: 'UI测试租户',
    adminUsername: 'uiadmin',
    adminMobile: '13900000012',
  });

  console.log('[seed] uitest 内：部门 / 角色 / 成员');
  const ui = await login({ username: 'uiadmin', password: PASSWORD, tenantCode: 'uitest' });
  const deptId = await ensureDept(ui, '测试部');
  await ensureTenantMember(ui, {
    username: 'member1',
    realName: '普通成员一号',
    mobile: '13900000021',
    mainDeptId: deptId,
  });
  const dictRoleId = await ensureRole(ui, {
    code: 'DICT_READONLY',
    name: '字典只读',
    dataScope: 'DEPT_AND_BELOW',
    codes: ['dict.group.list', 'dict.item.list'],
  });
  await ensureTenantMember(ui, {
    username: 'dictonly',
    realName: '字典只读员',
    mobile: '13900000022',
    mainDeptId: deptId,
    roleIds: [dictRoleId],
  });

  console.log(`[seed] 完成：新建 ${created} / 跳过 ${skipped}`);
}

main().catch((e) => {
  console.error(`[seed] 失败：${e.message}`);
  process.exit(1);
});
