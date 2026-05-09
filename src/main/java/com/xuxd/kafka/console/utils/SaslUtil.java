package com.xuxd.kafka.console.utils;

import com.xuxd.kafka.console.config.ContextConfigHolder;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.ScramMechanism;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.security.auth.SecurityProtocol;

/**
 * kafka-console-ui.
 *
 * @author xuxd
 * @date 2022-01-06 11:07:41
 **/
public class SaslUtil {

    public static final Pattern JAAS_PATTERN = Pattern.compile("^.*(username=\"(.*)\"[ \t]+).*$");

    public static final Pattern PRINCIPAL_PATTERN = Pattern.compile("principal=\"([^\"]+)\"");

    public static final String GSSAPI_MECHANISM = "GSSAPI";

    private SaslUtil() {
    }

    public static String findUsername(String saslJaasConfig) {
        if (saslJaasConfig == null) {
            return "";
        }
        Matcher matcher = JAAS_PATTERN.matcher(saslJaasConfig);
        return matcher.find() ? matcher.group(2) : "";
    }

    public static String findPrincipal(String saslJaasConfig) {
        if (saslJaasConfig == null) {
            return "";
        }
        Matcher matcher = PRINCIPAL_PATTERN.matcher(saslJaasConfig);
        return matcher.find() ? matcher.group(1) : "";
    }

    public static boolean isEnableSasl(Properties properties) {
        if (properties == null || !properties.containsKey(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG)) {
            return false;
        }
        String s = properties.getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG);
        try {
            SecurityProtocol protocol = SecurityProtocol.valueOf(s);
            switch (protocol) {
                case SASL_SSL:
                case SASL_PLAINTEXT:
                    return true;
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isEnableSasl() {
        return isEnableSasl(ContextConfigHolder.CONTEXT_CONFIG.get().getProperties());
    }

    public static boolean isEnableScram(Properties properties) {
        if (properties == null || !properties.containsKey(SaslConfigs.SASL_MECHANISM)) {
            return false;
        }
        String s = properties.getProperty(SaslConfigs.SASL_MECHANISM);
        try {
            ScramMechanism mechanism = ScramMechanism.fromMechanismName(s);
            return mechanism != ScramMechanism.UNKNOWN;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isEnableScram() {
        return isEnableScram(ContextConfigHolder.CONTEXT_CONFIG.get().getProperties());
    }

    public static boolean isEnableGSSAPI(Properties properties) {
        if (!isEnableSasl(properties)) {
            return false;
        }
        String mechanism = properties.getProperty(SaslConfigs.SASL_MECHANISM);
        return GSSAPI_MECHANISM.equalsIgnoreCase(mechanism);
    }

    public static boolean isEnableGSSAPI() {
        return isEnableGSSAPI(ContextConfigHolder.CONTEXT_CONFIG.get().getProperties());
    }

    /**
     * 构造 Kerberos 的 sasl.jaas.config 值。注意：与 jaas.conf 文件不同，sasl.jaas.config
     * 不需要外层 KafkaClient {} 包裹，只需要 LoginModule + 选项 + 末尾分号。
     */
    public static String buildKerberosJaasConfig(String principal, String keytabPath) {
        if (StringUtils.isBlank(principal)) {
            throw new IllegalArgumentException("principal is required for Kerberos");
        }
        if (StringUtils.isBlank(keytabPath)) {
            throw new IllegalArgumentException("keytabPath is required for Kerberos");
        }
        return new StringBuilder()
            .append("com.sun.security.auth.module.Krb5LoginModule required ")
            .append("useKeyTab=true ")
            .append("storeKey=true ")
            .append("keyTab=\"").append(escapeJaas(keytabPath)).append("\" ")
            .append("principal=\"").append(escapeJaas(principal)).append("\";")
            .toString();
    }

    private static String escapeJaas(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
