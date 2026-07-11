/**
 * 文件存储抽象层（公开 API）。
 *
 * <p>核心切面：
 * <ul>
 *   <li>{@link com.wensaas.core.api.file.FileStorage}：低层后端适配器接口（本地磁盘 / 阿里云 OSS / ...）。
 *   业务模块不直接调，由底座按租户 active 后端配置选择实现。</li>
 *   <li>{@link com.wensaas.core.api.file.FileService}：高层业务 API。业务模块通过它做"上传 / 申请直传签名 /
 *   确认上传 / 下载签名 / 删除 / 元数据查询"。</li>
 *   <li>{@link com.wensaas.core.api.file.FilePermissionChecker}：SPI。业务模块为自己的 {@code owner_module}
 *   注册一个实现，决定 business-only 文件的下载权限；未注册默认放行。</li>
 * </ul>
 */
package com.wensaas.core.api.file;
