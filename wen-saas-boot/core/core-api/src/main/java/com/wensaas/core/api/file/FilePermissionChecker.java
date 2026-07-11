package com.wensaas.core.api.file;

/**
 * 业务文件下载权限 SPI。
 *
 * <p>业务模块为自己的 {@code ownerModule} 注册一个 {@code @Component} 实现，决定
 * {@code visibility = business-only} 文件的下载权限；未注册默认放行（与
 * {@code NameLookup} 同模式）。
 *
 * <p>调用时机：{@code FileService.signedDownloadUrl} 在 Redis 缓存未命中时，
 * 按文件 {@code ownerModule} 找对应 checker 调用一次；缓存命中则跳过。
 *
 * <p>契约：
 * <ul>
 *   <li>{@link #ownerModule()} 必须与 sys_file.owner_module 列值一致；同一 ownerModule
 *   只允许一个 checker（启动期重复抛 {@link IllegalStateException}）</li>
 *   <li>{@link #canDownload} 仅返回 true/false；具体原因码由调用方包装 FileException</li>
 *   <li>实现应当是只读 + 幂等的</li>
 *   <li>租户隔离由实现内部负责（典型实现走 {@code TenantContext.require()}）</li>
 * </ul>
 *
 * <p>示例（业务模块内）：
 * <pre>{@code
 * @Component
 * public class CrmFilePermissionChecker implements FilePermissionChecker {
 *     private final CustomerService service;
 *
 *     @Override public String ownerModule() { return "crm"; }
 *
 *     @Override
 *     public boolean canDownload(FileMetadata file, Long currentUserId) {
 *         return service.canAccess(currentUserId, file.ownerRefId());
 *     }
 * }
 * }</pre>
 */
public interface FilePermissionChecker {

    /** 处理的业务模块 code（与 sys_file.owner_module 列值一致）。 */
    String ownerModule();

    /** 当前用户是否可以下载该 business-only 文件。 */
    boolean canDownload(FileMetadata file, Long currentUserId);
}
