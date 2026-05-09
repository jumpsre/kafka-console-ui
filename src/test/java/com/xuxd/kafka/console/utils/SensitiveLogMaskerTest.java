package com.xuxd.kafka.console.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SensitiveLogMasker 单元测试 —— 确保审计日志中的 jaas.config / 各类 password
 * 不会以明文落盘。
 */
class SensitiveLogMaskerTest {

    @Test
    void mask_nullAndEmpty() {
        assertNull(SensitiveLogMasker.mask(null));
        assertEquals("", SensitiveLogMasker.mask(""));
    }

    @Test
    void mask_passesThroughBenignText() {
        String s = "ClusterInfoDTO(id=1, clusterName=prod, address=broker:9092)";
        assertEquals(s, SensitiveLogMasker.mask(s));
    }

    @Test
    void mask_jsonPasswordValue() {
        String input = "{\"username\":\"alice\",\"password\":\"s3cr3t\"}";
        String masked = SensitiveLogMasker.mask(input);
        assertFalse(masked.contains("s3cr3t"), "raw password leaked: " + masked);
        assertTrue(masked.contains("\"password\":\"***\""));
        assertTrue(masked.contains("alice"), "username should not be masked");
    }

    @Test
    void mask_propertiesPasswordLine() {
        String input = "ssl.truststore.password=changeit\nrequest.timeout.ms=10000";
        String masked = SensitiveLogMasker.mask(input);
        assertFalse(masked.contains("changeit"));
        assertTrue(masked.contains("ssl.truststore.password=***"));
        assertTrue(masked.contains("request.timeout.ms=10000"));
    }

    @Test
    void mask_saslJaasConfigJson() {
        // 模拟 properties 字段已被序列化为 JSON 之后落到日志里的形态
        String input = "ClusterInfoDTO(properties={\"sasl.jaas.config\":"
            + "\"com.sun.security.auth.module.Krb5LoginModule required keyTab=\\\"/data/keytab/u.keytab\\\" "
            + "principal=\\\"kafka@EX.COM\\\";\"})";
        String masked = SensitiveLogMasker.mask(input);
        assertFalse(masked.contains("Krb5LoginModule"), "JAAS body leaked: " + masked);
        assertFalse(masked.contains("/data/keytab/u.keytab"), "keytab path leaked: " + masked);
        assertTrue(masked.contains("\"sasl.jaas.config\":\"***\""));
    }

    @Test
    void mask_saslJaasConfigPlainProperties() {
        // properties 文本格式（key=value 行）
        String input = "security.protocol=SASL_PLAINTEXT\n"
            + "sasl.mechanism=GSSAPI\n"
            + "sasl.jaas.config=com.sun.security.auth.module.Krb5LoginModule required keyTab=\"/x.keytab\" principal=\"a@B.C\";\n"
            + "request.timeout.ms=10000";
        String masked = SensitiveLogMasker.mask(input);
        assertFalse(masked.contains("Krb5LoginModule"), "JAAS leaked: " + masked);
        assertFalse(masked.contains("/x.keytab"));
        assertTrue(masked.contains("sasl.jaas.config=***"));
        // 其它无敏感字段保留
        assertTrue(masked.contains("security.protocol=SASL_PLAINTEXT"));
        assertTrue(masked.contains("sasl.mechanism=GSSAPI"));
        assertTrue(masked.contains("request.timeout.ms=10000"));
    }

    @Test
    void mask_scramJaasInlinePassword() {
        // SCRAM JAAS 整段没有特别配置，但内嵌的 password="..." 应该被遮罩
        String input = "sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required "
            + "username=\"alice\" password=\"hunter2\";";
        String masked = SensitiveLogMasker.mask(input);
        assertFalse(masked.contains("hunter2"), "SCRAM password leaked: " + masked);
    }

    @Test
    void mask_caseInsensitive() {
        String input = "SASL.JAAS.CONFIG=secretvalue";
        String masked = SensitiveLogMasker.mask(input);
        assertFalse(masked.contains("secretvalue"));
    }
}
