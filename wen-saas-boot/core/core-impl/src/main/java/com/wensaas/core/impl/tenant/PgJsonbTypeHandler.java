package com.wensaas.core.impl.tenant;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * PostgreSQL JSONB 专用 TypeHandler。
 *
 * <p>{@link JacksonTypeHandler} 以 {@code setString} 绑参，PostgreSQL JDBC 驱动无法将
 * character varying 隐式转为 jsonb；本 handler 使用 {@link PGobject} 显式指定 type=jsonb。
 */
public class PgJsonbTypeHandler extends JacksonTypeHandler {

    public PgJsonbTypeHandler(Class<?> type) {
        super(type);
    }

    public PgJsonbTypeHandler(Class<?> type, Field field) {
        super(type, field);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType)
            throws SQLException {
        PGobject obj = new PGobject();
        obj.setType("jsonb");
        obj.setValue(toJson(parameter));
        ps.setObject(i, obj);
    }
}
