import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { CreateShareRequest, FileSpace } from '@wen-saas/api-client';
import { fileCenterApi } from './fileCenterApi';

export const fileKeys = {
  all: ['fileCenter'] as const,
  list: (space: FileSpace, folderId: string | null, keyword: string, page: number) =>
    [...fileKeys.all, 'list', space, folderId ?? 'root', keyword, page] as const,
  recycle: (space: FileSpace) => [...fileKeys.all, 'recycle', space] as const,
  quota: () => [...fileKeys.all, 'quota'] as const,
};

export function useFileList(
  space: FileSpace,
  folderId: string | null,
  keyword: string,
  page: number,
  size = 50,
) {
  return useQuery({
    queryKey: fileKeys.list(space, folderId, keyword, page),
    queryFn: () => fileCenterApi.list(space, { folderId, keyword, page, size }),
  });
}

export function useRecycle(space: FileSpace, enabled: boolean) {
  return useQuery({
    queryKey: fileKeys.recycle(space),
    queryFn: () => fileCenterApi.recycle(space),
    enabled,
  });
}

export function useQuota() {
  return useQuery({ queryKey: fileKeys.quota(), queryFn: () => fileCenterApi.quota() });
}

/** 任一文件中心写操作后，统一失效所有文件中心查询（列表 / 回收站 / 配额）。 */
function useInvalidateAll() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: fileKeys.all });
}

export function useCreateFolder() {
  const invalidate = useInvalidateAll();
  return useMutation({
    mutationFn: (vars: { space: FileSpace; parentId: string | null; name: string }) =>
      fileCenterApi.createFolder({ space: vars.space, parentId: vars.parentId, name: vars.name }),
    onSuccess: invalidate,
  });
}

export function useRenameFolder() {
  const invalidate = useInvalidateAll();
  return useMutation({
    mutationFn: (vars: { folderId: string; name: string }) =>
      fileCenterApi.renameFolder(vars.folderId, vars.name),
    onSuccess: invalidate,
  });
}

export function useMoveFolder() {
  const invalidate = useInvalidateAll();
  return useMutation({
    mutationFn: (vars: { folderId: string; newParentId: string | null }) =>
      fileCenterApi.moveFolder(vars.folderId, vars.newParentId),
    onSuccess: invalidate,
  });
}

export function useDeleteFolder() {
  const invalidate = useInvalidateAll();
  return useMutation({
    mutationFn: (folderId: string) => fileCenterApi.deleteFolder(folderId),
    onSuccess: invalidate,
  });
}

export function useRenameFile() {
  const invalidate = useInvalidateAll();
  return useMutation({
    mutationFn: (vars: { fileId: string; name: string }) =>
      fileCenterApi.renameFile(vars.fileId, vars.name),
    onSuccess: invalidate,
  });
}

export function useMoveFile() {
  const invalidate = useInvalidateAll();
  return useMutation({
    mutationFn: (vars: { fileId: string; targetSpace: FileSpace; targetFolderId: string | null }) =>
      fileCenterApi.moveFile(vars.fileId, vars.targetSpace, vars.targetFolderId),
    onSuccess: invalidate,
  });
}

export function useDeleteFile() {
  const invalidate = useInvalidateAll();
  return useMutation({
    mutationFn: (fileId: string) => fileCenterApi.deleteFile(fileId),
    onSuccess: invalidate,
  });
}

export function useRestore() {
  const invalidate = useInvalidateAll();
  return useMutation({
    mutationFn: (vars: { fileIds?: string[]; folderIds?: string[] }) => fileCenterApi.restore(vars),
    onSuccess: invalidate,
  });
}

export function usePurge() {
  const invalidate = useInvalidateAll();
  return useMutation({
    mutationFn: (vars: { fileIds?: string[]; folderIds?: string[] }) => fileCenterApi.purge(vars),
    onSuccess: invalidate,
  });
}

export function useCreateShare() {
  return useMutation({
    mutationFn: (vars: { fileId: string; body: CreateShareRequest }) =>
      fileCenterApi.createShare(vars.fileId, vars.body),
  });
}

export function useShareInfo(shareToken: string | undefined) {
  return useQuery({
    queryKey: [...fileKeys.all, 'share', shareToken] as const,
    queryFn: () => fileCenterApi.getShare(shareToken!),
    enabled: !!shareToken,
    retry: false,
  });
}

export function useConsumeShare() {
  return useMutation({
    mutationFn: (vars: { shareToken: string; password?: string }) =>
      fileCenterApi.consumeShare(vars.shareToken, vars.password),
  });
}
