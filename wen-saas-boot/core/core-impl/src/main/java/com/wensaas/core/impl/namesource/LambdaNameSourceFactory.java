package com.wensaas.core.impl.namesource;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.wensaas.core.api.namesource.MatchMode;
import com.wensaas.core.api.namesource.NameLookup;
import com.wensaas.core.api.namesource.NameSourceFactory;
import com.wensaas.core.api.namesource.RenderMode;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link NameSourceFactory} 默认实现：链式 {@link Builder} 收集字段，{@link Builder#build()} 委托
 * {@link LambdaNameSource} 构造。
 *
 * <p>让业务模块只 import api（{@code NameSourceFactory} + {@code MatchMode}/{@code RenderMode}）即可一行
 * 注册名称源，不再 import {@code core.impl.namesource.LambdaNameSource}（架构审查 M10）。
 */
public class LambdaNameSourceFactory implements NameSourceFactory {

    @Override
    public <T> Builder<T> source(String entityType, String displayLabel, boolean tenantSearchable,
                                 Class<T> entityClass, SFunction<T, ?> idGetter) {
        return new Builder<>(entityType, displayLabel, tenantSearchable, entityClass, idGetter);
    }

    /** {@link NameSourceFactory.Builder} 默认实现。 */
    public static final class Builder<T> implements NameSourceFactory.Builder<T> {

        private final String entityType;
        private final String displayLabel;
        private final boolean tenantSearchable;
        private final Class<T> entityClass;
        private final SFunction<T, ?> idGetter;
        private final List<DisplayField<T>> fields = new ArrayList<>(2);
        private RenderMode render = RenderMode.PRIMARY;
        private boolean referenceable = true;

        private Builder(String entityType, String displayLabel, boolean tenantSearchable,
                        Class<T> entityClass, SFunction<T, ?> idGetter) {
            this.entityType = entityType;
            this.displayLabel = displayLabel;
            this.tenantSearchable = tenantSearchable;
            this.entityClass = entityClass;
            this.idGetter = idGetter;
        }

        @Override
        public Builder<T> primary(SFunction<T, String> getter, String label, MatchMode match) {
            if (!fields.isEmpty()) {
                throw new IllegalStateException("primary 只能声明一次：" + entityType);
            }
            fields.add(new DisplayField<>(getter, label, match));
            return this;
        }

        @Override
        public Builder<T> secondary(SFunction<T, String> getter, String label, MatchMode match) {
            if (fields.isEmpty()) {
                throw new IllegalStateException("须先声明 primary 再 secondary：" + entityType);
            }
            if (fields.size() > 1) {
                throw new IllegalStateException("secondary 最多一个：" + entityType);
            }
            fields.add(new DisplayField<>(getter, label, match));
            return this;
        }

        @Override
        public Builder<T> render(RenderMode mode) {
            this.render = mode;
            return this;
        }

        @Override
        public Builder<T> referenceable(boolean referenceable) {
            this.referenceable = referenceable;
            return this;
        }

        @Override
        public NameLookup build() {
            return new LambdaNameSource<>(entityType, displayLabel, tenantSearchable, referenceable,
                    entityClass, idGetter, fields, render);
        }
    }
}
