import type { components as AuthSchema } from './openapi-auth';
import type { components as BaseSchema } from './openapi-base';
import type { components as TasksSchema } from './openapi-tasks';
import type { components as ImportsSchema } from './openapi-imports';

export type {
  paths as AuthPaths,
  components as AuthComponents,
  operations as AuthOperations,
} from './openapi-auth';

export type {
  paths as BasePaths,
  components as BaseComponents,
  operations as BaseOperations,
} from './openapi-base';

export type {
  paths as TasksPaths,
  components as TasksComponents,
  operations as TasksOperations,
} from './openapi-tasks';

export type {
  paths as ImportsPaths,
  components as ImportsComponents,
  operations as ImportsOperations,
} from './openapi-imports';

export type LoginRequest = AuthSchema['schemas']['LoginRequest'];
export type LoginResponse = AuthSchema['schemas']['LoginResponse'];
export type UserSummary = AuthSchema['schemas']['UserSummary'];

export type RoleView = BaseSchema['schemas']['RoleView'];
export type RoleDetail = BaseSchema['schemas']['RoleDetail'];
export type RoleCreateRequest = BaseSchema['schemas']['RoleCreateRequest'];
export type RoleUpdateRequest = BaseSchema['schemas']['RoleUpdateRequest'];
export type RolePermissionsUpdateRequest = BaseSchema['schemas']['RolePermissionsUpdateRequest'];
export type MemberRef = BaseSchema['schemas']['MemberRef'];
export type MemberIdsRequest = BaseSchema['schemas']['MemberIdsRequest'];

export type DeptView = BaseSchema['schemas']['DeptView'];
export type DeptCreateRequest = BaseSchema['schemas']['DeptCreateRequest'];
export type DeptUpdateRequest = BaseSchema['schemas']['DeptUpdateRequest'];
export type DeptMoveRequest = BaseSchema['schemas']['DeptMoveRequest'];

export type DictGroup = BaseSchema['schemas']['DictGroup'];
export type DictItem = BaseSchema['schemas']['DictItem'];
export type DictGroupCreateRequest = BaseSchema['schemas']['DictGroupCreateRequest'];
export type DictGroupUpdateRequest = BaseSchema['schemas']['DictGroupUpdateRequest'];
export type DictItemCreateRequest = BaseSchema['schemas']['DictItemCreateRequest'];
export type DictItemUpdateRequest = BaseSchema['schemas']['DictItemUpdateRequest'];

export type ProblemDetail = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  errors?: Array<{ field?: string; message?: string }>;
  requestId?: string;
  [key: string]: unknown;
};

// 任务中心
export type TaskView = TasksSchema['schemas']['TaskView'];
export type PageResponseTaskView = TasksSchema['schemas']['PageResponseTaskView'];

// 数据导入
export type ImportEntityView = ImportsSchema['schemas']['ImportEntityView'];
export type ImportFieldView = ImportsSchema['schemas']['ImportFieldView'];
export type ParseResultView = ImportsSchema['schemas']['ParseResultView'];
export type PreviewRowView = ImportsSchema['schemas']['PreviewRowView'];
export type RowErrorView = ImportsSchema['schemas']['RowErrorView'];
export type ImportConfirmView = ImportsSchema['schemas']['ConfirmView'];
