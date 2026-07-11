package com.wensaas.crmdemo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.event.DomainEventPublisher;
import com.wensaas.core.api.namesource.EntityTypes;
import com.wensaas.core.api.notification.send.MessageSendRequest;
import com.wensaas.core.api.notification.send.MessageService;
import com.wensaas.core.api.notification.send.RecipientSpec;
import com.wensaas.core.api.namesource.TransField;
import com.wensaas.core.api.namesource.TransFill;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.api.customfield.CustomFieldDisplayResolver;
import com.wensaas.crmdemo.entity.CrmDemoCustomer;
import com.wensaas.crmdemo.entity.CustomerStatus;
import com.wensaas.crmdemo.event.CrmDemoCustomerEvents;
import com.wensaas.crmdemo.repository.CrmDemoCustomerMapper;
import com.wensaas.crmdemo.spi.CrmDemoModule;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客户业务服务（分层架构）。
 *
 * <p>写路径填充 {@code tenant_id / creator_id / owner_dept_id}（业务方职责）；
 * 自定义字段值的校验由底座 {@code CustomFieldWriteInterceptor} 自动完成，查询过滤由
 * TenantLine + DataPermission + CustomFieldQueryRewriter 三拦截器自动叠加。
 *
 * <p>每个写动作完成后经 {@link DomainEventPublisher} 发一条领域事件（{@link CrmDemoCustomerEvents}），
 * 供 webhook 订阅与业务方 MQ 消费；发布异步、失败仅记日志，不影响主流程返回。
 * 响应中的 {@code ownerName / ownerDeptName} 由 {@link TransFill} 按 {@link TransField} 声明批量回填。
 * 负责人变更为他人时经 {@link MessageService} 发「客户分配通知」站内信（模板
 * {@code crm_demo.customer_assigned}，投递异步）。
 */
@Service
public class CrmDemoCustomerService {


    private final CrmDemoCustomerMapper mapper;
    private final CustomFieldDisplayResolver displayResolver;
    private final DomainEventPublisher eventPublisher;
    private final TransFill transFill;
    private final MessageService messageService;

    public CrmDemoCustomerService(CrmDemoCustomerMapper mapper, CustomFieldDisplayResolver displayResolver,
                                  DomainEventPublisher eventPublisher, TransFill transFill,
                                  MessageService messageService) {
        this.mapper = mapper;
        this.displayResolver = displayResolver;
        this.eventPublisher = eventPublisher;
        this.transFill = transFill;
        this.messageService = messageService;
    }

    // ============================== 查询 ==============================

    public PageResult page(Query q) {
        QueryWrapper<CrmDemoCustomer> w = new QueryWrapper<>();
        if (q.status() != null && !q.status().isBlank()) {
            w.eq("status", q.status());
        }
        if (q.ownerId() != null) {
            w.eq("owner_id", q.ownerId());
        }
        if (q.source() != null && !q.source().isBlank()) {
            w.eq("source", q.source());
        }
        if (q.keyword() != null && !q.keyword().isBlank()) {
            String kw = q.keyword().trim();
            w.and(x -> x.like("name", kw).or().like("code", kw));
        }
        // 自定义字段等值过滤（cf_* 键由 @CustomFieldEquals 收集）：列名经 CustomFieldQueryRewriter
        // 翻译为 extra->>'cf_x'；仅索引化字段允许，未索引化字段会被底座拦截抛 UNINDEXED_QUERY_NOT_ALLOWED。
        if (q.cfEquals() != null) {
            q.cfEquals().forEach(w::eq);
        }
        w.orderByDesc("created_at");

        Page<CrmDemoCustomer> page = mapper.selectPage(new Page<>(q.pageNo(), q.size()), w);
        List<CrmDemoCustomer> rows = page.getRecords();

        // 批量解析自定义字段视图（enum/ref 补 __label）
        List<Map<String, Object>> views = displayResolver.resolveViews(
                CrmDemoModule.OBJECT_CUSTOMER,
                rows.stream().map(r -> r.getExtra() == null ? Map.<String, Object>of() : r.getExtra()).toList());

        List<ListItem> items = new java.util.ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            CrmDemoCustomer r = rows.get(i);
            items.add(new ListItem(r.getId(), r.getName(), r.getCode(), r.getStatus(),
                    labelOf(r.getStatus()), r.getSource(), r.getOwnerId(), null, r.getOwnerDeptId(),
                    r.getCreatedAt(), views.get(i)));
        }
        transFill.fill(items);   // ownerName 按 @TransField 声明批量回填（整页一次取数）
        return new PageResult(items, page.getTotal());
    }

    /** 导入预校验取数：{@code codes} 中已存在于本租户客户表的编号集合（租户/软删由拦截器自动过滤）。 */
    public Set<String> existingCodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Set.of();
        }
        QueryWrapper<CrmDemoCustomer> w = new QueryWrapper<CrmDemoCustomer>()
                .select("code").in("code", codes);
        return mapper.selectList(w).stream()
                .map(CrmDemoCustomer::getCode)
                .collect(Collectors.toSet());
    }

    public Detail get(Long id) {
        CrmDemoCustomer r = mapper.selectById(id);
        if (r == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "客户不存在");
        }
        Map<String, Object> view = displayResolver.resolveView(r).orElseGet(Map::of);
        Detail d = new Detail(r.getId(), r.getName(), r.getCode(), r.getStatus(), labelOf(r.getStatus()),
                r.getSource(), r.getOwnerId(), null, r.getOwnerDeptId(), null, r.getCreatorId(),
                r.getCreatedAt(), r.getUpdatedAt(), view);
        transFill.fill(d);
        return d;
    }

    // ============================== 写入 ==============================

    public Brief create(CreateCommand c) {
        return create(c, true);
    }

    /** {@code notifyAssignment=false} 供批量导入：负责人分配不逐行发站内信（事件照发）。 */
    public Brief create(CreateCommand c, boolean notifyAssignment) {
        long tenantId = TenantContext.require();
        UserPrincipal me = UserContext.require();
        LocalDateTime now = LocalDateTime.now();

        CustomerStatus status = c.status() == null ? CustomerStatus.LEAD : CustomerStatus.parse(c.status());
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法客户状态: " + c.status());
        }

        CrmDemoCustomer r = new CrmDemoCustomer();
        r.setTenantId(tenantId);
        r.setName(c.name());
        r.setCode(c.code());
        r.setStatus(status.name());
        r.setSource(c.source());
        r.setOwnerId(c.ownerId() != null ? c.ownerId() : me.userId());
        r.setOwnerDeptId(c.ownerDeptId() != null ? c.ownerDeptId() : me.mainDeptId());
        r.setCreatorId(me.userId());
        r.setExtra(c.custom());
        r.setStageChangedAt(now);
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        mapper.insert(r);   // CustomFieldWriteInterceptor 自动校验 extra
        eventPublisher.publish(CrmDemoCustomerEvents.CUSTOMER_CREATED,
                CrmDemoCustomerEvents.CustomerPayload.of(r));
        if (notifyAssignment) {
            notifyAssigned(r, me);   // 建档即指定他人负责时通知
        }
        return Brief.of(r);
    }

    public Brief update(Long id, UpdateCommand c) {
        CrmDemoCustomer r = mapper.selectById(id);
        if (r == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "客户不存在");
        }
        Long previousOwner = r.getOwnerId();
        r.setName(c.name());
        r.setCode(c.code());
        r.setSource(c.source());
        if (c.ownerId() != null) {
            r.setOwnerId(c.ownerId());
        }
        if (c.ownerDeptId() != null) {
            r.setOwnerDeptId(c.ownerDeptId());
        }
        r.setExtra(c.custom());
        r.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(r);   // CustomFieldWriteInterceptor 自动校验 extra
        eventPublisher.publish(CrmDemoCustomerEvents.CUSTOMER_UPDATED,
                CrmDemoCustomerEvents.CustomerPayload.of(r));
        if (!java.util.Objects.equals(previousOwner, r.getOwnerId())) {
            notifyAssigned(r, UserContext.require());
        }
        return Brief.of(r);
    }

    /**
     * 负责人为他人时发「客户分配通知」站内信（负责人是操作者本人则不发）。
     * 投递切 MQ 异步，失败不影响主流程。
     */
    private void notifyAssigned(CrmDemoCustomer r, UserPrincipal operator) {
        Long owner = r.getOwnerId();
        if (owner == null || owner == operator.userId()) {
            return;
        }
        String operatorName = operator.realName() != null ? operator.realName() : operator.username();
        messageService.send(MessageSendRequest.builder(CrmDemoModule.TPL_CUSTOMER_ASSIGNED)
                .variables(Map.of("customerName", r.getName(), "operatorName", operatorName))
                .recipients(List.of(RecipientSpec.member(owner)))
                .related(CrmDemoModule.OBJECT_CUSTOMER, String.valueOf(r.getId()))
                .link("/crm-demo/my-customers")
                .build());
    }

    public Brief delete(Long id) {
        CrmDemoCustomer r = mapper.selectById(id);
        if (r == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "客户不存在");
        }
        mapper.deleteById(id);  // @TableLogic 软删
        eventPublisher.publish(CrmDemoCustomerEvents.CUSTOMER_DELETED,
                CrmDemoCustomerEvents.CustomerPayload.of(r));
        return Brief.of(r);
    }

    /** 推进状态机。非法流转抛 400。 */
    public Brief transition(Long id, String target) {
        CrmDemoCustomer r = mapper.selectById(id);
        if (r == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "客户不存在");
        }
        CustomerStatus from = CustomerStatus.parse(r.getStatus());
        CustomerStatus to = CustomerStatus.parse(target);
        if (to == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法目标状态: " + target);
        }
        if (from == null || !from.canTransitionTo(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "不允许从 " + r.getStatus() + " 流转到 " + to.name());
        }
        String fromStatus = r.getStatus();
        LocalDateTime now = LocalDateTime.now();
        r.setStatus(to.name());
        r.setStageChangedAt(now);
        r.setUpdatedAt(now);
        mapper.updateById(r);
        eventPublisher.publish(CrmDemoCustomerEvents.CUSTOMER_STAGE_CHANGED,
                CrmDemoCustomerEvents.StageChangedPayload.of(r, fromStatus, to.name()));
        return Brief.of(r);
    }

    private static String labelOf(String status) {
        CustomerStatus s = CustomerStatus.parse(status);
        return s == null ? status : s.label();
    }

    // ============================== DTO ==============================

    public record Query(String status, String keyword, Long ownerId, String source,
                        Map<String, Object> cfEquals, long pageNo, long size) {
    }

    public record PageResult(List<ListItem> records, long total) {
    }

    /**
     * 列表项。可变 Lombok POJO（fluent 访问器保持 record 风格 {@code field()} 调用），
     * {@code ownerName} 经 {@link TransFill} 反射回填；{@code @JsonAutoDetect} 走字段序列化，
     * JSON 结构与原 record 一致。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
            getterVisibility = JsonAutoDetect.Visibility.NONE,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static final class ListItem {
        private Long id;
        private String name;
        private String code;
        private String status;
        private String statusLabel;
        /** 来源字典项 code；中文名由前端按字典项缓存翻译。 */
        private String source;
        private Long ownerId;
        @TransField(type = EntityTypes.SYS_USER, ref = "ownerId")
        private String ownerName;
        private Long ownerDeptId;
        private LocalDateTime createdAt;
        private Map<String, Object> extra;
    }

    /** 详情。同 {@link ListItem} 模板，另回填 {@code ownerDeptName}。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
            getterVisibility = JsonAutoDetect.Visibility.NONE,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static final class Detail {
        private Long id;
        private String name;
        private String code;
        private String status;
        private String statusLabel;
        private String source;
        private Long ownerId;
        @TransField(type = EntityTypes.SYS_USER, ref = "ownerId")
        private String ownerName;
        private Long ownerDeptId;
        @TransField(type = EntityTypes.SYS_DEPT, ref = "ownerDeptId")
        private String ownerDeptName;
        private Long creatorId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Map<String, Object> extra;
    }

    /** 写操作统一返回的简报：审计 {@code objectDescExpr} 与前端提示都从这里取。 */
    public record Brief(Long id, String name, String status, String statusLabel) {

        static Brief of(CrmDemoCustomer r) {
            return new Brief(r.getId(), r.getName(), r.getStatus(), labelOf(r.getStatus()));
        }
    }

    public record CreateCommand(String name, String code, String status, String source,
                                Long ownerId, Long ownerDeptId, Map<String, Object> custom) {
    }

    public record UpdateCommand(String name, String code, String source, Long ownerId, Long ownerDeptId,
                                Map<String, Object> custom) {
    }
}
