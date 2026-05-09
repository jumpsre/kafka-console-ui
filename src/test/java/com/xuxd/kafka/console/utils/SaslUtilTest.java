package com.xuxd.kafka.console.utils;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SaslUtil 单元测试 —— 重点覆盖 Kerberos JAAS 拼装与机制判定。
 */
class SaslUtilTest {

    @Test
    void buildKerberosJaasConfig_basic() {
        String jaas = SaslUtil.buildKerberosJaasConfig(
            "kafka-client@EXAMPLE.COM",
            "/data/keytab/u-1.keytab");

        assertTrue(jaas.startsWith("com.sun.security.auth.module.Krb5LoginModule required"));
        assertTrue(jaas.contains("useKeyTab=true"));
        assertTrue(jaas.contains("storeKey=true"));
        assertTrue(jaas.contains("keyTab=\"/data/keytab/u-1.keytab\""));
        assertTrue(jaas.contains("principal=\"kafka-client@EXAMPLE.COM\""));
        assertTrue(jaas.endsWith(";"));
    }

    @Test
    void buildKerberosJaasConfig_principalRequired() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> SaslUtil.buildKerberosJaasConfig(null, "/x.keytab"));
        assertTrue(e.getMessage().contains("principal"));

        assertThrows(IllegalArgumentException.class,
            () -> SaslUtil.buildKerberosJaasConfig("  ", "/x.keytab"));
    }

    @Test
    void buildKerberosJaasConfig_keytabRequired() {
        assertThrows(IllegalArgumentException.class,
            () -> SaslUtil.buildKerberosJaasConfig("a@B.C", null));
        assertThrows(IllegalArgumentException.class,
            () -> SaslUtil.buildKerberosJaasConfig("a@B.C", ""));
    }

    @Test
    void buildKerberosJaasConfig_escapesQuoteInPath() {
        // 防御性测试：keytab 路径里就算混入了 "（极端情况），生成的 JAAS 串仍是合法的（双引号被转义）
        String jaas = SaslUtil.buildKerberosJaasConfig("p@R", "/abc\"hack/x.keytab");
        // \" 必须被转义为 \\\"，最后字符串里出现的就是 keyTab="\""
        assertTrue(jaas.contains("\\\""));
    }

    @Test
    void findPrincipal_extracts() {
        String jaas = "com.sun.security.auth.module.Krb5LoginModule required "
            + "useKeyTab=true keyTab=\"/x.keytab\" principal=\"foo@BAR.COM\";";
        assertEquals("foo@BAR.COM", SaslUtil.findPrincipal(jaas));
    }

    @Test
    void findPrincipal_nullSafe() {
        assertEquals("", SaslUtil.findPrincipal(null));
        assertEquals("", SaslUtil.findPrincipal("no principal here"));
    }

    @Test
    void findUsername_extracts() {
        String scram = "org.apache.kafka.common.security.scram.ScramLoginModule required "
            + "username=\"alice\" password=\"s3cr3t\";";
        assertEquals("alice", SaslUtil.findUsername(scram));
    }

    @Test
    void isEnableSasl_overload() {
        Properties p = new Properties();
        assertFalse(SaslUtil.isEnableSasl(p));

        p.setProperty("security.protocol", "PLAINTEXT");
        assertFalse(SaslUtil.isEnableSasl(p));

        p.setProperty("security.protocol", "SASL_PLAINTEXT");
        assertTrue(SaslUtil.isEnableSasl(p));

        p.setProperty("security.protocol", "SASL_SSL");
        assertTrue(SaslUtil.isEnableSasl(p));

        p.setProperty("security.protocol", "GARBAGE");
        assertFalse(SaslUtil.isEnableSasl(p));
    }

    @Test
    void isEnableScram_overload() {
        Properties p = new Properties();
        assertFalse(SaslUtil.isEnableScram(p));

        p.setProperty("sasl.mechanism", "SCRAM-SHA-256");
        assertTrue(SaslUtil.isEnableScram(p));

        p.setProperty("sasl.mechanism", "SCRAM-SHA-512");
        assertTrue(SaslUtil.isEnableScram(p));

        p.setProperty("sasl.mechanism", "GSSAPI");
        assertFalse(SaslUtil.isEnableScram(p));

        p.setProperty("sasl.mechanism", "GARBAGE");
        assertFalse(SaslUtil.isEnableScram(p));
    }

    @Test
    void isEnableGSSAPI_overload() {
        Properties p = new Properties();
        // 没设 security.protocol 就一律 false（防止 mechanism=GSSAPI 但协议是 PLAINTEXT 的诡异组合）
        p.setProperty("sasl.mechanism", "GSSAPI");
        assertFalse(SaslUtil.isEnableGSSAPI(p));

        p.setProperty("security.protocol", "SASL_PLAINTEXT");
        assertTrue(SaslUtil.isEnableGSSAPI(p));

        // 大小写不敏感
        p.setProperty("sasl.mechanism", "gssapi");
        assertTrue(SaslUtil.isEnableGSSAPI(p));

        p.setProperty("sasl.mechanism", "SCRAM-SHA-256");
        assertFalse(SaslUtil.isEnableGSSAPI(p));
    }

    @Test
    void isEnableGSSAPI_nullProperties() {
        assertFalse(SaslUtil.isEnableGSSAPI((Properties) null));
    }
}
