package com.wensaas.core.impl.file;

import com.wensaas.core.api.file.FileMetadata;
import com.wensaas.core.api.file.FilePermissionChecker;
import com.wensaas.core.api.file.FileVisibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * business-only 文件下载权限路由。
 *
 * <ul>
 *   <li>file-center 文件：v1 默认放行（UI 会话补"上传人/共享权限"校验）</li>
 *   <li>business-only 文件：找 {@code ownerModule} 对应 {@link FilePermissionChecker} 调用一次；
 *   未注册默认放行（与 {@code NameLookup} 同模式，业务模块按需接入）</li>
 *   <li>同 ownerModule 注册多个 checker → 启动期抛 {@link IllegalStateException}</li>
 * </ul>
 */
public class FilePermissionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(FilePermissionEvaluator.class);

    private final Map<String, FilePermissionChecker> checkerByModule;

    public FilePermissionEvaluator(List<FilePermissionChecker> checkers) {
        Map<String, FilePermissionChecker> map = new HashMap<>();
        for (FilePermissionChecker c : checkers) {
            String key = c.ownerModule();
            if (key == null || key.isBlank()) {
                throw new IllegalStateException(
                        "FilePermissionChecker.ownerModule() 不能为空: " + c.getClass().getName());
            }
            FilePermissionChecker prior = map.put(key, c);
            if (prior != null) {
                throw new IllegalStateException(
                        "ownerModule=" + key + " 同时注册了多个 FilePermissionChecker: "
                                + prior.getClass().getName() + " vs " + c.getClass().getName());
            }
        }
        this.checkerByModule = Map.copyOf(map);
        log.info("[FilePermissionEvaluator] {} checker(s) registered: {}",
                checkerByModule.size(), checkerByModule.keySet());
    }

    /**
     * 当前用户是否可以下载该文件。
     *
     * @param file          文件元数据
     * @param currentUserId 当前用户 ID（可能为 null，如运营端代登录上下文）
     */
    public boolean canDownload(FileMetadata file, Long currentUserId) {
        if (file.visibility() == FileVisibility.FILE_CENTER) {
            // v1 默认放行；UI 会话引入"上传人 / 共享权限"后补
            return true;
        }
        // business-only
        FilePermissionChecker checker = checkerByModule.get(file.ownerModule());
        if (checker == null) {
            // 未注册 checker：默认放行（与 NameLookup 同模式）
            return true;
        }
        return checker.canDownload(file, currentUserId);
    }
}
