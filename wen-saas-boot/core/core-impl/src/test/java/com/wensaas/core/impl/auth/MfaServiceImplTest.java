package com.wensaas.core.impl.auth;

import com.alibaba.fastjson2.JSON;
import com.wensaas.core.api.auth.login.MfaException;
import com.wensaas.core.api.auth.login.MfaMethod;
import com.wensaas.core.api.verification.VerificationCodeService;
import com.wensaas.core.api.verification.VerificationPurpose;
import com.wensaas.core.impl.auth.mfa.MfaProperties;
import com.wensaas.core.impl.auth.mfa.MfaSecretCipher;
import com.wensaas.core.impl.auth.mfa.MfaService;
import com.wensaas.core.impl.auth.mfa.MfaServiceImpl;
import com.wensaas.core.impl.auth.mfa.SysMfaConfig;
import com.wensaas.core.impl.auth.mfa.SysMfaConfigMapper;
import com.wensaas.core.impl.auth.mfa.SysMfaRecoveryCode;
import com.wensaas.core.impl.auth.mfa.SysMfaRecoveryCodeMapper;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 解耦模型核心行为：登录通道推导链、开关与恢复码生命周期、验证器绑定独立性。
 * 登录流（preAuth/verify 签发会话）由集成测试覆盖，此处只测已登录态语义。
 */
class MfaServiceImplTest {

    private static final long USER_ID = 42L;
    private static final long TENANT_ID = 0L;

    private SysMfaConfigMapper configMapper;
    private SysMfaRecoveryCodeMapper recoveryMapper;
    private SysUserMapper userMapper;
    private VerificationCodeService verificationCodeService;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> redisValues;
    private MfaSecretCipher cipher;
    private MfaServiceImpl service;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        // 纯 Mockito 测试无 SqlSession，手动初始化 MP 实体元数据，使 LambdaUpdateWrapper 方法引用可解析。
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysMfaConfig.class);
        TableInfoHelper.initTableInfo(assistant, SysMfaRecoveryCode.class);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        configMapper = mock(SysMfaConfigMapper.class);
        recoveryMapper = mock(SysMfaRecoveryCodeMapper.class);
        userMapper = mock(SysUserMapper.class);
        verificationCodeService = mock(VerificationCodeService.class);
        redis = mock(StringRedisTemplate.class);
        redisValues = mock(ValueOperations.class);
        lenient().when(redis.opsForValue()).thenReturn(redisValues);
        MfaProperties props = new MfaProperties();
        cipher = new MfaSecretCipher(props.getSecretKey(), props.getSecretSalt());
        service = new MfaServiceImpl(configMapper, recoveryMapper, userMapper, null, null,
                cipher, verificationCodeService, null, null, null, redis, new MfaProperties());
    }

    private void stubConfig(SysMfaConfig config) {
        when(configMapper.selectOne(any())).thenReturn(config);
    }

    private void stubUser(String email) {
        SysUser u = new SysUser();
        u.setId(USER_ID);
        u.setUsername("alice");
        u.setEmail(email);
        when(userMapper.selectById(USER_ID)).thenReturn(u);
    }

    private SysMfaConfig config(String totpSecretPlain, boolean enabled) {
        SysMfaConfig c = new SysMfaConfig();
        c.setId(1L);
        c.setTenantId(TENANT_ID);
        c.setUserId(USER_ID);
        c.setTotpSecret(totpSecretPlain == null ? null : cipher.encrypt(totpSecretPlain));
        c.setLoginMfaEnabled(enabled);
        c.setEnabledAt(enabled ? Instant.now() : null);
        return c;
    }

    // ===== 通道推导链 =====

    @Test
    void status_derivesTotp_whenBound() {
        stubConfig(config("SECRET", true));
        stubUser("a@x.com");
        MfaService.Status s = service.status(USER_ID);
        assertThat(s.loginMfaEnabled()).isTrue();
        assertThat(s.totpBound()).isTrue();
        assertThat(s.loginMethod()).isEqualTo(MfaMethod.TOTP);
    }

    @Test
    void status_derivesEmail_whenNotBoundButEmailPresent() {
        stubConfig(config(null, true));
        stubUser("a@x.com");
        MfaService.Status s = service.status(USER_ID);
        assertThat(s.totpBound()).isFalse();
        assertThat(s.loginMethod()).isEqualTo(MfaMethod.EMAIL);
    }

    @Test
    void status_methodNull_whenNothingAvailable() {
        stubConfig(null);
        stubUser(null);
        MfaService.Status s = service.status(USER_ID);
        assertThat(s.loginMfaEnabled()).isFalse();
        assertThat(s.loginMethod()).isNull();
    }

    // ===== 开启开关 =====

    @Test
    void enableStart_totpBound_noCodeSent() {
        stubConfig(config("SECRET", false));
        stubUser("a@x.com");
        MfaService.ChallengeInfo info = service.enableLoginMfaStart(USER_ID, TENANT_ID);
        assertThat(info.method()).isEqualTo(MfaMethod.TOTP);
        verify(verificationCodeService, never()).send(any(), any(), anyString(), anyLong(), anyLong());
    }

    @Test
    void enableStart_emailFallback_sendsCode() {
        stubConfig(null);
        stubUser("a@x.com");
        when(verificationCodeService.send(eq(VerificationPurpose.MFA_SETUP), any(), eq("a@x.com"),
                anyLong(), anyLong()))
                .thenReturn(new VerificationCodeService.SentResult("a***@x.com", 300, 60));
        MfaService.ChallengeInfo info = service.enableLoginMfaStart(USER_ID, TENANT_ID);
        assertThat(info.method()).isEqualTo(MfaMethod.EMAIL);
        assertThat(info.maskedTarget()).isEqualTo("a***@x.com");
    }

    @Test
    void enableStart_rejects_whenAlreadyEnabled_orNoChannel() {
        stubConfig(config(null, true));
        stubUser("a@x.com");
        assertThatThrownBy(() -> service.enableLoginMfaStart(USER_ID, TENANT_ID))
                .isInstanceOf(MfaException.class)
                .hasFieldOrPropertyWithValue("code", MfaException.Code.ILLEGAL_STATE);

        stubConfig(null);
        stubUser(null);
        assertThatThrownBy(() -> service.enableLoginMfaStart(USER_ID, TENANT_ID))
                .isInstanceOf(MfaException.class)
                .hasFieldOrPropertyWithValue("code", MfaException.Code.TARGET_MISSING);
    }

    @Test
    void enableConfirm_emailCode_enablesAndIssuesRecoveryCodes() {
        stubConfig(config(null, false));
        stubUser("a@x.com");
        when(verificationCodeService.verify(VerificationPurpose.MFA_SETUP, "a@x.com", "123456"))
                .thenReturn(true);
        var codes = service.enableLoginMfaConfirm(USER_ID, TENANT_ID, "123456");
        assertThat(codes).hasSize(new MfaProperties().getRecoveryCodeCount());
        // 开关置开
        verify(configMapper).update(eq(null), any());
        // 恢复码重生成（先删后插）
        verify(recoveryMapper).delete(any());
        verify(recoveryMapper, org.mockito.Mockito.times(codes.size())).insert(any(SysMfaRecoveryCode.class));
    }

    // ===== 关闭开关 =====

    @Test
    void disable_keepsTotpBinding_clearsRecoveryCodes() {
        stubConfig(config("SECRET", true));
        service.disableLoginMfa(USER_ID, TENANT_ID);
        // 行保留（走 update 置关），不删行
        verify(configMapper, never()).deleteById(anyLong());
        verify(configMapper).update(eq(null), any());
        verify(recoveryMapper).delete(any());
    }

    @Test
    void disable_deletesRow_whenNoTotpBinding() {
        stubConfig(config(null, true));
        service.disableLoginMfa(USER_ID, TENANT_ID);
        ArgumentCaptor<Long> id = ArgumentCaptor.forClass(Long.class);
        verify(configMapper).deleteById(id.capture());
        assertThat(id.getValue()).isEqualTo(1L);
        verify(recoveryMapper).delete(any());
    }

    // ===== 验证器绑定独立性 =====

    @Test
    void bindTotpConfirm_writesSecret_withoutTouchingRecoveryCodes() throws Exception {
        stubConfig(null);
        stubUser("a@x.com");
        String secret = "JBSWY3DPEHPK3PXP";
        when(redisValues.get(anyString())).thenReturn(JSON.toJSONString(new BindState(secret)));
        String code = new DefaultCodeGenerator().generate(secret, Instant.now().getEpochSecond() / 30);

        service.bindTotpConfirm(USER_ID, TENANT_ID, code);

        // 建行写入密钥、登录开关保持默认关
        ArgumentCaptor<SysMfaConfig> inserted = ArgumentCaptor.forClass(SysMfaConfig.class);
        verify(configMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getTotpSecret()).isNotBlank();
        assertThat(inserted.getValue().getLoginMfaEnabled()).isFalse();
        // 不动恢复码
        verify(recoveryMapper, never()).delete(any());
        verify(recoveryMapper, never()).insert(any(SysMfaRecoveryCode.class));
    }

    @Test
    void bindTotpConfirm_rejects_withoutStart() {
        when(redisValues.get(anyString())).thenReturn(null);
        assertThatThrownBy(() -> service.bindTotpConfirm(USER_ID, TENANT_ID, "000000"))
                .isInstanceOf(MfaException.class)
                .hasFieldOrPropertyWithValue("code", MfaException.Code.ILLEGAL_STATE);
    }

    /** 与 MfaServiceImpl 内部 TotpBindState 的 JSON 形状一致（仅 secret 字段）。 */
    private record BindState(String secret) {
    }
}
