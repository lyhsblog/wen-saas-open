package com.wensaas.core.impl.ops.setting;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 以 {@link String} 形式读写 PG {@code jsonb} 列的 MyBatis TypeHandler。
 *
 * <p>{@link com.wensaas.core.impl.tenant.PgJsonbTypeHandler} 走 Jackson 反序列化为强类型，
 * 而本类直接保留原始 JSON 文本，由调用方按需 {@code ObjectMapper.readValue(json, Class)}。
 *
 * <p>用于 {@code sys_setting.value}：值的类型不固定（Integer / String / Map），
 * 持久化时保留原文，读时由 {@code OpsSettingService.get(category, key, Class<T>, default)} 按 type 反序列化。
 */
public class StringJsonbTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        PGobject obj = new PGobject();
        obj.setType("jsonb");
        obj.setValue(parameter);
        ps.setObject(i, obj);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }
}
