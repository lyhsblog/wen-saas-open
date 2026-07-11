import { setupWorker } from 'msw/browser';
import { handlers } from '@wen-saas/api-client/mocks';

export const worker = setupWorker(...handlers);
