package com.wensaas.crmdemo.file;

import com.wensaas.core.api.file.FileMetadata;
import com.wensaas.core.api.file.FilePermissionChecker;
import com.wensaas.crmdemo.repository.CrmDemoCustomerMapper;
import org.springframework.stereotype.Component;

/**
 * crm_demo 业务文件下载权限（{@link FilePermissionChecker} 的首个业务实现）。
 *
 * <p>客户附件以 {@code ownerModule=crm_demo / ownerRefType=customer / ownerRefId=客户id}
 * 存 {@code sys_file}（business-only，零自建表），下载签名 URL 生成时经本 checker 鉴权：
 * 引用的客户必须在当前租户内存在且未删除（Mapper 走 TenantLine 自动隔离 + @TableLogic
 * 过滤软删）。客户删除后其附件即不可再下载。
 *
 * <p>更细粒度（如按数据权限行级可见性）可在此基础上加查询条件，demo 保持存在性校验。
 */
@Component
public class CrmDemoFilePermissionChecker implements FilePermissionChecker {

    /** 客户附件的 sys_file.owner_ref_type 值。 */
    public static final String REF_TYPE_CUSTOMER = "customer";

    private final CrmDemoCustomerMapper customerMapper;

    public CrmDemoFilePermissionChecker(CrmDemoCustomerMapper customerMapper) {
        this.customerMapper = customerMapper;
    }

    @Override
    public String ownerModule() {
        return "crm_demo";
    }

    @Override
    public boolean canDownload(FileMetadata file, Long currentUserId) {
        if (!REF_TYPE_CUSTOMER.equals(file.ownerRefType()) || file.ownerRefId() == null) {
            return false;
        }
        return customerMapper.selectById(file.ownerRefId()) != null;
    }
}
