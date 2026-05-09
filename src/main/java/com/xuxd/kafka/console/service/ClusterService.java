package com.xuxd.kafka.console.service;

import com.xuxd.kafka.console.beans.ResponseData;
import com.xuxd.kafka.console.beans.dto.ClusterInfoDTO;

/**
 * kafka-console-ui.
 *
 * @author xuxd
 * @date 2021-10-08 14:22:30
 **/
public interface ClusterService {
    ResponseData getClusterInfo();

    ResponseData getClusterInfoListForSelect();

    ResponseData getClusterInfoList();

    ResponseData addClusterInfo(ClusterInfoDTO dto);

    ResponseData deleteClusterInfo(Long id);

    ResponseData updateClusterInfo(ClusterInfoDTO dto);

    ResponseData peekClusterInfo();

    ResponseData getBrokerApiVersionInfo();

    /** 不写库的连通性检测：用 DTO 拼出 props，跑一次 describeCluster。 */
    ResponseData testConnection(ClusterInfoDTO dto);
}
