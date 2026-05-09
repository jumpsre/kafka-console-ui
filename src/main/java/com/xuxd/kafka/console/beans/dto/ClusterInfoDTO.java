package com.xuxd.kafka.console.beans.dto;

import com.xuxd.kafka.console.beans.dos.ClusterInfoDO;
import com.xuxd.kafka.console.utils.ConvertUtil;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * kafka-console-ui.
 *
 * @author xuxd
 * @date 2022-01-04 20:19:03
 **/
@Data
public class ClusterInfoDTO {
    private Long id;

    private String clusterName;

    private String address;

    /**
     * 自由格式属性（key=value 行）。Kerberos 的 sasl.jaas.config 不应直接写这里，
     * 而通过下面的结构化字段 + keytabFileId 由后端拼装。
     */
    private String properties;

    private String updateTime;

    /** 安全协议：PLAINTEXT / SASL_PLAINTEXT / SASL_SSL / SSL，留空不启用。 */
    private String securityProtocol;

    /** SASL 机制：GSSAPI / SCRAM-SHA-256 / SCRAM-SHA-512 / PLAIN，仅当 securityProtocol 是 SASL_* 时生效。 */
    private String saslMechanism;

    /** Kerberos 服务名，默认 kafka。 */
    private String kerberosServiceName;

    /** Kerberos principal，例如 kafka-client@EXAMPLE.COM。 */
    private String kerberosPrincipal;

    /** keytab 文件 ID（已通过 /cluster/keytab 上传），后端会解析为绝对路径。 */
    private String keytabFileId;

    /**
     * 简单 DTO→DO 转换，仅用于不需要 Kerberos 拼装的场景（如数据导入恢复）。
     * 集群表单走 ClusterServiceImpl 内部的 assembleDO，会调用这里之外的 Kerberos 拼装逻辑。
     */
    public ClusterInfoDO to() {
        ClusterInfoDO infoDO = new ClusterInfoDO();
        infoDO.setId(id);
        infoDO.setClusterName(clusterName);
        infoDO.setAddress(address);

        if (StringUtils.isNotBlank(properties)) {
            infoDO.setProperties(ConvertUtil.propertiesStr2JsonStr(properties));
        }

        return infoDO;
    }
}
