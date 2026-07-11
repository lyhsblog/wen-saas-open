import { authHandlers } from './handlers/auth';
import { capabilitiesHandlers } from './handlers/capabilities';
import { deptHandlers } from './handlers/dept';
import { dictHandlers } from './handlers/dict';
import { messageInboxHandlers } from './handlers/messageInbox';
import { opsProfileHandlers } from './handlers/opsProfile';
import { roleHandlers } from './handlers/role';
import { workbenchHandlers } from './handlers/workbench';

export const handlers = [
  ...authHandlers,
  ...capabilitiesHandlers,
  ...deptHandlers,
  ...dictHandlers,
  ...messageInboxHandlers,
  ...opsProfileHandlers,
  ...roleHandlers,
  ...workbenchHandlers,
];

export { resetMockState } from './data';
