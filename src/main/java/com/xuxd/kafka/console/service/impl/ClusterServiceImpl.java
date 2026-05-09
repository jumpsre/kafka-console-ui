package com.xuxd.kafka.console.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xuxd.kafka.console.beans.BrokerNode;
import com.xuxd.kafka.console.beans.ClusterInfo;
import com.xuxd.kafka.console.beans.Credentials;
import com.xuxd.kafka.console.beans.ResponseData;
import com.xuxd.kafka.console.beans.dos.ClusterInfoDO;
import com.xuxd.kafka.console.beans.dos.ClusterRoleRelationDO;
import com.xuxd.kafka.console.beans.dto.ClusterInfoDTO;
import com.xuxd.kafka.console.beans.vo.BrokerApiVersionVO;
import com.xuxd.kafka.console.beans.vo.ClusterInfoVO;
import com.xuxd.kafka.console.config.AuthConfig;
import com.xuxd.kafka.console.dao.ClusterInfoMapper;
import com.xuxd.kafka.console.dao.ClusterRoleRelationMapper;
import com.xuxd.kafka.console.filter.CredentialsContext;
import com.xuxd.kafka.console.service.ClusterService;
import com.xuxd.kafka.console.service.KeytabService;
import com.xuxd.kafka.console.utils.ConvertUtil;
import com.xuxd.kafka.console.utils.SaslUtil;
import kafka.console.BrokerVersion;
import kafka.console.ClusterConsole;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.NodeApiVersions;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.config.SaslConfigs;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * kafka-console-ui.
 *
 * @author xuxd
 * @date 2021-10-08 14:23:09
 **/
@Slf4j
@Service
public class ClusterServiceImpl implements ClusterService {

    private static final int TEST_CONNECTION_TIMEOUT_MS = 8000;

    private final ClusterConsole clusterConsole;

    private final ClusterInfoMapper clusterInfoMapper;

    private final AuthConfig authConfig;

    private final ClusterRoleRelationMapper clusterRoleRelationMapper;

    @Autowired
    private KeytabService keytabService;

    public ClusterServiceImpl(final ObjectProvider<ClusterConsole> clusterConsole,
                              final ObjectProvider<ClusterInfoMapper> clusterInfoMapper,
                              final AuthConfig authConfig,
                              final ClusterRoleRelationMapper clusterRoleRelationMapper) {
        this.clusterConsole = clusterConsole.getIfAvailable();
        this.clusterInfoMapper = clusterInfoMapper.getIfAvailable();
        this.authConfig = authConfig;
        this.clusterRoleRelationMapper = clusterRoleRelationMapper;
    }

    @Override
    public ResponseData getClusterInfo() {
        ClusterInfo clusterInfo = clusterConsole.clusterInfo();
        Set<BrokerNode> nodes = clusterInfo.getNodes();
        if (nodes == null) {
            log.error("集群节点信息为空，集群地址可能不正确或集群内没有活跃节点");
            return ResponseData.create().failed("集群节点信息为空，集群地址可能不正确或集群内没有活跃节点");
        }
        clusterInfo.setNodes(new TreeSet<>(nodes));
        return ResponseData.create().data(clusterInfo).success();
    }

    @Override
    public ResponseData getClusterInfoListForSelect() {
        return ResponseData.create().
                data(clusterInfoMapper.selectList(null).stream().
                        map(e -> {
                            ClusterInfoVO vo = ClusterInfoVO.from(e);
                            vo.setProperties(Collections.emptyList());
                            vo.setAddress("");
                            return vo;
                        }).collect(Collectors.toList())).success();
    }

    @Override
    public ResponseData getClusterInfoList() {
        // 如果开启权限管理，当前用户没有集群切换->集群信息的编辑权限，隐藏集群的属性信息，避免ACL属性暴露出来
        Credentials credentials = CredentialsContext.get();
        boolean enableClusterAuthority = credentials != null && authConfig.isEnableClusterAuthority();
        final Set<Long> clusterInfoIdSet = new HashSet<>();
        if (enableClusterAuthority) {
            List<Long> roleIdList = credentials.getRoleIdList();
            QueryWrapper<ClusterRoleRelationDO> queryWrapper = new QueryWrapper<>();
            queryWrapper.in("role_id", roleIdList);
            clusterInfoIdSet.addAll(clusterRoleRelationMapper.selectList(queryWrapper).
                    stream().map(ClusterRoleRelationDO::getClusterInfoId).
                    collect(Collectors.toSet()));
        }
        return ResponseData.create().
                data(clusterInfoMapper.selectList(null).stream().
                        filter(e -> !enableClusterAuthority || clusterInfoIdSet.contains(e.getId())).
                        map(e -> {
                            ClusterInfoVO vo = ClusterInfoVO.from(e);
                            if (credentials != null && credentials.isHideClusterProperty()) {
                                vo.setProperties(Collections.emptyList());
                            }
                            return vo;
                        }).collect(Collectors.toList())).success();
    }

    @Override
    public ResponseData addClusterInfo(ClusterInfoDTO dto) {
        ClusterInfoDO infoDO;
        try {
            infoDO = assembleDO(dto);
        } catch (IllegalArgumentException e) {
            return ResponseData.create().failed(e.getMessage());
        }
        QueryWrapper<ClusterInfoDO> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("cluster_name", infoDO.getClusterName());
        if (clusterInfoMapper.selectCount(queryWrapper) > 0) {
            return ResponseData.create().failed("cluster name exist.");
        }
        clusterInfoMapper.insert(infoDO);
        Credentials credentials = CredentialsContext.get();
        boolean enableClusterAuthority = credentials != null && authConfig.isEnableClusterAuthority();
        if (enableClusterAuthority) {
            for (Long roleId : credentials.getRoleIdList()) {
                // 开启集群的数据权限控制，新增集群的时候必须要录入一条信息
                QueryWrapper<ClusterRoleRelationDO> relationQueryWrapper = new QueryWrapper<>();
                relationQueryWrapper.eq("role_id", roleId).
                        eq("cluster_info_id", infoDO.getId());
                Integer count = clusterRoleRelationMapper.selectCount(relationQueryWrapper);
                if (count <= 0) {
                    ClusterRoleRelationDO relationDO = new ClusterRoleRelationDO();
                    relationDO.setRoleId(roleId);
                    relationDO.setClusterInfoId(infoDO.getId());
                    clusterRoleRelationMapper.insert(relationDO);
                }
            }
        }
        return ResponseData.create().success();
    }

    @Override
    public ResponseData deleteClusterInfo(Long id) {
        clusterInfoMapper.deleteById(id);
        Credentials credentials = CredentialsContext.get();
        boolean enableClusterAuthority = credentials != null && authConfig.isEnableClusterAuthority();
        if (enableClusterAuthority) {
            for (Long roleId : credentials.getRoleIdList()) {
                // 开启集群的数据权限控制，删除集群的时候必须要删除对应的数据权限
                QueryWrapper<ClusterRoleRelationDO> relationQueryWrapper = new QueryWrapper<>();
                relationQueryWrapper.eq("role_id", roleId).eq("cluster_info_id", id);
                clusterRoleRelationMapper.delete(relationQueryWrapper);
            }
        }
        return ResponseData.create().success();
    }

    @Override
    public ResponseData updateClusterInfo(ClusterInfoDTO dto) {
        ClusterInfoDO infoDO;
        try {
            infoDO = assembleDO(dto);
        } catch (IllegalArgumentException e) {
            return ResponseData.create().failed(e.getMessage());
        }
        if (infoDO.getProperties() == null) {
            // null 的话不更新，这个是bug，设置为空字符串解决
            infoDO.setProperties("");
        }
        clusterInfoMapper.updateById(infoDO);
        return ResponseData.create().success();
    }

    @Override
    public ResponseData peekClusterInfo() {
        List<ClusterInfoDO> dos = clusterInfoMapper.selectList(null);
        if (CollectionUtils.isEmpty(dos)) {
            return ResponseData.create().failed("No Cluster Info.");
        }
        return ResponseData.create().data(dos.stream().findFirst().map(ClusterInfoVO::from)).success();
    }

    @Override
    public ResponseData getBrokerApiVersionInfo() {
        HashMap<Node, NodeApiVersions> map = clusterConsole.listBrokerVersionInfo();
        List<BrokerApiVersionVO> list = new ArrayList<>(map.size());
        map.forEach(((node, versions) -> {
            BrokerApiVersionVO vo = new BrokerApiVersionVO();
            vo.setBrokerId(node.id());
            vo.setHost(node.host() + ":" + node.port());
            vo.setSupportNums(versions.allSupportedApiVersions().size());
            String versionInfo = versions.toString(true);
            int from = 0;
            int count = 0;
            int index = -1;
            while ((index = versionInfo.indexOf("UNSUPPORTED", from)) >= 0 && from < versionInfo.length()) {
                count++;
                from = index + 1;
            }
            vo.setUnSupportNums(count);
            versionInfo = versionInfo.substring(1, versionInfo.length() - 2);
            vo.setVersionInfo(Arrays.asList(StringUtils.split(versionInfo, ",")));
            list.add(vo);
            // 推测broker版本
            String vs = BrokerVersion.guessBrokerVersion(versions);
            vo.setBrokerVersion(vs);
        }));
        Collections.sort(list, Comparator.comparingInt(BrokerApiVersionVO::getBrokerId));
        return ResponseData.create().data(list).success();
    }

    @Override
    public ResponseData testConnection(ClusterInfoDTO dto) {
        if (StringUtils.isBlank(dto.getAddress())) {
            return ResponseData.create().failed("集群地址不能为空");
        }
        Properties props;
        try {
            props = buildKafkaProps(dto);
        } catch (IllegalArgumentException e) {
            return ResponseData.create().failed(e.getMessage());
        }
        Admin admin = null;
        try {
            admin = Admin.create(props);
            DescribeClusterResult r = admin.describeCluster(
                new DescribeClusterOptions().timeoutMs(TEST_CONNECTION_TIMEOUT_MS));
            String clusterId = r.clusterId().get(TEST_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            int nodeCount = r.nodes().get(TEST_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS).size();
            Map<String, Object> result = new HashMap<>();
            result.put("clusterId", clusterId);
            result.put("nodeCount", nodeCount);
            return ResponseData.create().data(result).success("连接成功");
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            log.warn("test connection failed: {}", root.getMessage());
            return ResponseData.create().failed("连接失败: " + root.getClass().getSimpleName()
                + ": " + StringUtils.defaultString(root.getMessage()));
        } finally {
            if (admin != null) {
                try { admin.close(); } catch (Exception ignore) { }
            }
        }
    }

    /**
     * 把 DTO 转 DO，包含：拼装 Kerberos 结构化字段为 properties 行。
     * 校验失败抛 IllegalArgumentException，由调用方包装为失败响应。
     */
    private ClusterInfoDO assembleDO(ClusterInfoDTO dto) {
        ClusterInfoDO infoDO = new ClusterInfoDO();
        infoDO.setId(dto.getId());
        infoDO.setClusterName(dto.getClusterName());
        infoDO.setAddress(dto.getAddress());
        String composed = composePropertiesString(dto);
        if (StringUtils.isNotBlank(composed)) {
            infoDO.setProperties(ConvertUtil.propertiesStr2JsonStr(composed));
        } else {
            infoDO.setProperties("");
        }
        return infoDO;
    }

    /**
     * 把"自由文本属性"+"结构化 Kerberos 字段"合并为最终 properties 文本（key=value 行）。
     * 结构化字段优先级高于自由文本里同名 key。
     */
    private String composePropertiesString(ClusterInfoDTO dto) {
        Properties merged = new Properties();
        // 先吃自由文本（兼容 SCRAM 等用户手填模式）
        if (StringUtils.isNotBlank(dto.getProperties())) {
            try (java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(dto.getProperties().getBytes())) {
                merged.load(in);
            } catch (Exception e) {
                throw new IllegalArgumentException("属性格式错误: " + e.getMessage());
            }
        }
        // 再叠加结构化字段
        if (StringUtils.isNotBlank(dto.getSecurityProtocol())) {
            merged.setProperty("security.protocol", dto.getSecurityProtocol());
        }
        if (StringUtils.isNotBlank(dto.getSaslMechanism())) {
            merged.setProperty(SaslConfigs.SASL_MECHANISM, dto.getSaslMechanism());
        }
        if (StringUtils.equalsIgnoreCase(dto.getSaslMechanism(), SaslUtil.GSSAPI_MECHANISM)) {
            if (StringUtils.isBlank(dto.getKerberosPrincipal())) {
                throw new IllegalArgumentException("Kerberos principal 不能为空");
            }
            if (StringUtils.isBlank(dto.getKeytabFileId())) {
                throw new IllegalArgumentException("请上传 keytab 并选择");
            }
            String keytabPath = keytabService.resolveAbsolutePath(dto.getKeytabFileId());
            if (keytabPath == null) {
                throw new IllegalArgumentException("keytab 文件不存在或已被删除，请重新上传");
            }
            String serviceName = StringUtils.defaultIfBlank(dto.getKerberosServiceName(), "kafka");
            merged.setProperty(SaslConfigs.SASL_KERBEROS_SERVICE_NAME, serviceName);
            merged.setProperty(SaslConfigs.SASL_JAAS_CONFIG,
                SaslUtil.buildKerberosJaasConfig(dto.getKerberosPrincipal(), keytabPath));
        }
        // 输出为 key=value 行
        StringBuilder sb = new StringBuilder();
        for (String key : merged.stringPropertyNames()) {
            sb.append(key).append('=').append(merged.getProperty(key)).append(System.lineSeparator());
        }
        return sb.toString();
    }

    /**
     * 测试连接专用：直接构造 Kafka 客户端 Properties，不走 ContextConfigHolder，避免污染当前线程上下文。
     */
    private Properties buildKafkaProps(ClusterInfoDTO dto) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, dto.getAddress());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, TEST_CONNECTION_TIMEOUT_MS);
        String composed = composePropertiesString(dto);
        if (StringUtils.isNotBlank(composed)) {
            try (java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(composed.getBytes())) {
                Properties parsed = new Properties();
                parsed.load(in);
                props.putAll(parsed);
            } catch (Exception e) {
                throw new IllegalArgumentException("属性格式错误: " + e.getMessage());
            }
        }
        return props;
    }
}
