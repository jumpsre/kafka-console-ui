package com.xuxd.kafka.console.service;

import com.xuxd.kafka.console.beans.ResponseData;
import org.springframework.web.multipart.MultipartFile;

/**
 * keytab 文件管理：上传、查询、删除。文件存储在 {@code kafka-console.kerberos.keytab-dir} 下，
 * 以 UUID 命名（保留原扩展名 .keytab），并带 metadata 文件记录原始文件名。
 *
 * @author kerberos-integration
 **/
public interface KeytabService {

    /**
     * 上传一个 keytab 文件，返回 fileId（UUID）。该 fileId 用于在集群属性里通过
     * {@code keyTab="<keytab-dir>/<fileId>.keytab"} 引用。
     */
    ResponseData upload(MultipartFile file);

    /** 列出所有已上传的 keytab。 */
    ResponseData list();

    /** 删除一个 keytab；若仍被集群引用则拒绝。 */
    ResponseData delete(String fileId);

    /**
     * 把 fileId 解析为绝对路径。返回 null 表示文件不存在或 fileId 不合法。
     */
    String resolveAbsolutePath(String fileId);
}
