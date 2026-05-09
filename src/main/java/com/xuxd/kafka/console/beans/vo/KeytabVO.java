package com.xuxd.kafka.console.beans.vo;

import lombok.Data;

/**
 * 已上传的 keytab 文件描述。
 *
 * @author kerberos-integration
 **/
@Data
public class KeytabVO {

    /** 文件 ID（UUID），同时也是磁盘上的文件名（不含扩展名）。 */
    private String fileId;

    /** 用户上传时的原始文件名。 */
    private String originalFilename;

    /** 字节数。 */
    private long size;

    /** 上传时间（毫秒）。 */
    private long uploadTime;

    /** 是否被某个集群引用。 */
    private boolean inUse;
}
