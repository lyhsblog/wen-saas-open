package com.wensaas.core.impl.security;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 透明字段加密 TypeHandler。
 *
 * <p>写入时对字符串值用 {@link SensitiveFieldCipher} 确定性加密、读取时解密；明文行容错原样读出。
 * 经 {@code @TableName(autoResultMap = true)} + {@code @TableField(typeHandler = ...)} 挂到
 * {@code sys_user.mobile / email} 等列上，对 MP 自动 SQL（insert/updateById/selectById/列表投影）
 * 全透明。等值查询（唯一性校验）因 MP wrapper 不走 TypeHandler，需调用方显式 {@code encrypt} 入参
 * （见 {@code OpsTeamMemberAdminServiceImpl.existsByField}）。
 *
 * <p>持有者未就绪时退化为明文直存直读（见 {@link SensitiveFieldCipherHolder}）。
 */
@MappedTypes(String.class)
public class EncryptedStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, enc(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return dec(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return dec(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return dec(cs.getString(columnIndex));
    }

    private static String enc(String v) {
        SensitiveFieldCipher cipher = SensitiveFieldCipherHolder.get();
        return cipher == null ? v : cipher.encrypt(v);
    }

    private static String dec(String v) {
        SensitiveFieldCipher cipher = SensitiveFieldCipherHolder.get();
        return cipher == null ? v : cipher.decrypt(v);
    }
}
