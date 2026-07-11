#!/usr/bin/env node
/**
 * gen:types —— 从「运行中的后端实例」导出 OpenAPI 分组 JSON 并生成 TS 类型。
 *
 * 流程：GET {后端地址}/v3/api-docs/{group} → .cache/openapi/openapi-{group}.json
 *       → openapi-typescript → packages/domain/src/openapi-{group}.d.ts
 *
 * - 分组名与后端 GroupedOpenApi 配置（wen-saas-boot core-web OpenApiDocConfiguration）一致，
 *   后端全量分组还有 biz / ops / openapi / files / public / me / file-center / message 等，
 *   前端目前只消费下面四组；需要新分组时在 GROUPS 里加一项即可。
 * - 后端地址默认 http://127.0.0.1:8080，可用环境变量 WEN_SAAS_API_BASE 覆盖。
 * - 用法：先启动后端，再执行 `pnpm gen:types`。
 */
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const apiBase = process.env.WEN_SAAS_API_BASE ?? 'http://127.0.0.1:8080';

/** 需要生成类型的后端 OpenAPI 分组；输出为 packages/domain/src/openapi-<group>.d.ts */
const GROUPS = ['auth', 'base', 'tasks', 'imports'];

const cacheDir = path.join(root, '.cache', 'openapi');
mkdirSync(cacheDir, { recursive: true });

/** 拉取单个分组的 OpenAPI JSON 到缓存目录，返回文件路径。 */
async function fetchGroup(group) {
  const url = `${apiBase}/v3/api-docs/${group}`;
  let res;
  try {
    res = await fetch(url, { signal: AbortSignal.timeout(10_000) });
  } catch {
    console.error(`[gen:types] 无法连接 ${url}`);
    console.error(
      '[gen:types] 请先在本机启动 wen-saas-boot（默认 http://127.0.0.1:8080，' +
        '可用环境变量 WEN_SAAS_API_BASE 指定其他地址）后重试 pnpm gen:types。',
    );
    process.exit(1);
  }
  if (!res.ok) {
    console.error(
      `[gen:types] ${url} 返回 HTTP ${res.status}，` +
        `请确认后端已启用 OpenAPI 文档端点且存在分组 "${group}"。`,
    );
    process.exit(1);
  }
  const jsonFile = path.join(cacheDir, `openapi-${group}.json`);
  writeFileSync(jsonFile, await res.text());
  return jsonFile;
}

for (const group of GROUPS) {
  const jsonFile = await fetchGroup(group);
  const outFile = path.join(root, 'packages', 'domain', 'src', `openapi-${group}.d.ts`);
  execFileSync('pnpm', ['exec', 'openapi-typescript', jsonFile, '-o', outFile], {
    cwd: root,
    stdio: 'inherit',
  });
}

console.log(`[gen:types] 完成：${GROUPS.join(' / ')} 共 ${GROUPS.length} 组类型已更新到 packages/domain/src/`);
