package com.xuxd.kafka.console.utils;

import java.util.regex.Pattern;

/**
 * 审计日志脱敏：把 sasl.jaas.config 整段、各种 password 替换为 ***，避免 keytab 路径、SCRAM 密码、
 * truststore 密码等敏感信息落到操作日志里。
 *
 * @author kerberos-integration
 */
public final class SensitiveLogMasker {

    /**
     * sasl.jaas.config="..."（JSON 形式，properties 序列化后是这个样子）。
     * 注意：JSON 里值是 "..."，所以匹配到下一个未转义双引号为止。
     */
    private static final Pattern JAAS_QUOTED = Pattern.compile(
        "(?i)(sasl\\.jaas\\.config\"?\\s*[:=]\\s*\")(?:\\\\\"|[^\"])*(\")");

    /**
     * sasl.jaas.config=... （properties 文本形式，到行尾或下一个分隔符为止）。
     */
    private static final Pattern JAAS_UNQUOTED = Pattern.compile(
        "(?i)(sasl\\.jaas\\.config\\s*=\\s*)[^,\\]\\}\\n\\r]+");

    /** JSON 里成对的 password key/value： "password":"xxx"。 */
    private static final Pattern PASSWORD_JSON = Pattern.compile(
        "(?i)(\"[^\"]*password[^\"]*\"\\s*:\\s*\")(?:\\\\\"|[^\"])*(\")");

    /** properties 文本形式：something.password=xxx。 */
    private static final Pattern PASSWORD_PROP = Pattern.compile(
        "(?i)([\\w.-]*password\\s*=\\s*)[^,\\]\\}\\n\\r\\s]+");

    /** JAAS 内嵌 password="xxx"（即使 JAAS 整段已被另一规则覆盖，这条作为兜底）。 */
    private static final Pattern PASSWORD_JAAS_INLINE = Pattern.compile(
        "(?i)(password\\s*=\\s*\")[^\"]+(\")");

    private SensitiveLogMasker() {
    }

    public static String mask(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        String r = s;
        r = JAAS_QUOTED.matcher(r).replaceAll("$1***$2");
        r = JAAS_UNQUOTED.matcher(r).replaceAll("$1***");
        r = PASSWORD_JSON.matcher(r).replaceAll("$1***$2");
        r = PASSWORD_PROP.matcher(r).replaceAll("$1***");
        r = PASSWORD_JAAS_INLINE.matcher(r).replaceAll("$1***$2");
        return r;
    }
}
