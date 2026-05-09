package com.xuxd.kafka.console.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Kerberos 相关配置：keytab 落盘目录、krb5.conf 路径。
 *
 * @author kerberos-integration
 **/
@Data
@Configuration
@ConfigurationProperties(prefix = "kafka-console.kerberos")
public class KerberosProperties {

    /**
     * keytab 文件落盘目录，集群配置里的 keyTab 路径会指向该目录下的文件。
     */
    private String keytabDir;

    /**
     * krb5.conf 路径，仅用于前端展示和上传覆盖。运行时仍由 -Djava.security.krb5.conf 指定（重启生效）。
     */
    private String krb5ConfPath;

    /**
     * 单 keytab 最大字节数，默认 64KB。
     */
    private long keytabMaxBytes = 64L * 1024L;
}
