package com.wensaas.core.impl.permission.datascope;

import com.wensaas.core.api.auth.permission.DataPermission;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.wensaas.core.impl.permission.eval.PermissionCatalogServiceImpl;

/**
 * 启动时扫描所有 {@code @RestController} bean，收集 {@code @DataPermission} 注解中的 action code。
 *
 * <p>只负责"哪些 action code 受数据权限管控"这一事实（去重、保持发现顺序）。
 * 友好名称不在此处解析：由 {@link PermissionCatalogServiceImpl} 从 {@code PermissionRegistry}
 * 派生的完整目录（底座 {@code BasePermissionContributor} + 业务模块注册）翻译，查不到回退裸 code。
 */
public class DataPermissionActionRegistry implements ApplicationContextAware, SmartInitializingSingleton {

    private ApplicationContext ctx;
    private volatile List<String> actionCodes = List.of();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.ctx = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Set<String> codes = new LinkedHashSet<>();
        for (Object bean : ctx.getBeansWithAnnotation(RestController.class).values()) {
            Class<?> beanClass = AopUtils.getTargetClass(bean);
            for (Method method : beanClass.getDeclaredMethods()) {
                DataPermission ann = AnnotationUtils.findAnnotation(method, DataPermission.class);
                if (ann != null && !ann.value().isEmpty()) {
                    codes.add(ann.value());
                }
            }
        }
        this.actionCodes = List.copyOf(new ArrayList<>(codes));
    }

    /** 受数据权限管控的全部 action code（去重，保持发现顺序）。 */
    public List<String> getActionCodes() {
        return actionCodes;
    }
}
