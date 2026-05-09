package com.xuxd.kafka.console.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xuxd.kafka.console.beans.ResponseData;
import com.xuxd.kafka.console.beans.dos.ClusterInfoDO;
import com.xuxd.kafka.console.beans.vo.KeytabVO;
import com.xuxd.kafka.console.config.KerberosProperties;
import com.xuxd.kafka.console.dao.ClusterInfoMapper;
import com.xuxd.kafka.console.service.KeytabService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * keytab 文件管理实现：落盘存储 + 通过扫描集群 properties 字符串判断引用。
 *
 * @author kerberos-integration
 **/
@Slf4j
@Service
public class KeytabServiceImpl implements KeytabService {

    private static final String KEYTAB_EXT = ".keytab";

    private static final String META_EXT = ".meta";

    /** UUID v4 校验，避免 path traversal。 */
    private static final Pattern FILE_ID_PATTERN =
        Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final KerberosProperties kerberosProperties;

    private final ClusterInfoMapper clusterInfoMapper;

    @Autowired
    public KeytabServiceImpl(KerberosProperties kerberosProperties, ClusterInfoMapper clusterInfoMapper) {
        this.kerberosProperties = kerberosProperties;
        this.clusterInfoMapper = clusterInfoMapper;
    }

    @PostConstruct
    public void init() {
        String dir = kerberosProperties.getKeytabDir();
        if (StringUtils.isBlank(dir)) {
            log.warn("kafka-console.kerberos.keytab-dir is not configured; keytab upload disabled.");
            return;
        }
        try {
            Path p = Paths.get(dir);
            if (!Files.exists(p)) {
                Files.createDirectories(p);
            }
        } catch (IOException e) {
            log.error("create keytab dir failed: {}", dir, e);
        }
    }

    @Override
    public ResponseData upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseData.create().failed("文件为空");
        }
        if (file.getSize() > kerberosProperties.getKeytabMaxBytes()) {
            return ResponseData.create().failed("文件超过 " + kerberosProperties.getKeytabMaxBytes() + " 字节上限");
        }
        Path dir = keytabDir();
        if (dir == null) {
            return ResponseData.create().failed("服务端未配置 kafka-console.kerberos.keytab-dir");
        }
        String fileId = UUID.randomUUID().toString();
        Path target = dir.resolve(fileId + KEYTAB_EXT);
        Path metaTarget = dir.resolve(fileId + META_EXT);
        try {
            Files.write(target, file.getBytes());
            chmod600(target);
            String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
            // 单行 metadata：原始文件名（原始名可能含特殊字符，做最小转义）
            Files.write(metaTarget, sanitizeForMeta(original).getBytes());
            chmod600(metaTarget);
        } catch (IOException e) {
            log.error("save keytab failed", e);
            // 落盘失败时清理半成品
            safeDelete(target);
            safeDelete(metaTarget);
            return ResponseData.create().failed("保存文件失败: " + e.getMessage());
        }
        KeytabVO vo = new KeytabVO();
        vo.setFileId(fileId);
        vo.setOriginalFilename(file.getOriginalFilename());
        vo.setSize(file.getSize());
        vo.setUploadTime(System.currentTimeMillis());
        return ResponseData.create().data(vo).success();
    }

    @Override
    public ResponseData list() {
        Path dir = keytabDir();
        if (dir == null || !Files.exists(dir)) {
            return ResponseData.create().data(Collections.emptyList()).success();
        }
        Set<String> referenced = collectReferencedFileIds();
        List<KeytabVO> list = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(KEYTAB_EXT))
                .forEach(p -> {
                    String name = p.getFileName().toString();
                    String id = name.substring(0, name.length() - KEYTAB_EXT.length());
                    if (!FILE_ID_PATTERN.matcher(id).matches()) {
                        return;
                    }
                    KeytabVO vo = new KeytabVO();
                    vo.setFileId(id);
                    vo.setOriginalFilename(readMeta(dir, id));
                    try {
                        vo.setSize(Files.size(p));
                        vo.setUploadTime(Files.getLastModifiedTime(p).toMillis());
                    } catch (IOException ignore) {
                    }
                    vo.setInUse(referenced.contains(id));
                    list.add(vo);
                });
        } catch (IOException e) {
            log.error("list keytab failed", e);
            return ResponseData.create().failed("列出 keytab 失败: " + e.getMessage());
        }
        list.sort((a, b) -> Long.compare(b.getUploadTime(), a.getUploadTime()));
        return ResponseData.create().data(list).success();
    }

    @Override
    public ResponseData delete(String fileId) {
        if (StringUtils.isBlank(fileId) || !FILE_ID_PATTERN.matcher(fileId).matches()) {
            return ResponseData.create().failed("非法 fileId");
        }
        if (collectReferencedFileIds().contains(fileId)) {
            return ResponseData.create().failed("该 keytab 仍被集群配置引用，无法删除");
        }
        Path dir = keytabDir();
        if (dir == null) {
            return ResponseData.create().failed("服务端未配置 keytab-dir");
        }
        safeDelete(dir.resolve(fileId + KEYTAB_EXT));
        safeDelete(dir.resolve(fileId + META_EXT));
        return ResponseData.create().success();
    }

    @Override
    public String resolveAbsolutePath(String fileId) {
        if (StringUtils.isBlank(fileId) || !FILE_ID_PATTERN.matcher(fileId).matches()) {
            return null;
        }
        Path dir = keytabDir();
        if (dir == null) {
            return null;
        }
        Path target = dir.resolve(fileId + KEYTAB_EXT);
        if (!Files.exists(target)) {
            return null;
        }
        // 统一用正斜杠：避免 Windows 反斜杠在 properties + JAAS 两层 unescape 中被误解析
        return target.toAbsolutePath().toString().replace('\\', '/');
    }

    private Path keytabDir() {
        String dir = kerberosProperties.getKeytabDir();
        return StringUtils.isBlank(dir) ? null : Paths.get(dir);
    }

    /** 扫描 t_cluster_info.properties 字段中所有 fileId 引用。 */
    private Set<String> collectReferencedFileIds() {
        Set<String> ids = new HashSet<>();
        List<ClusterInfoDO> all = clusterInfoMapper.selectList(new QueryWrapper<>());
        if (all == null) {
            return ids;
        }
        for (ClusterInfoDO info : all) {
            String props = info.getProperties();
            if (StringUtils.isBlank(props)) {
                continue;
            }
            // properties 是 JSON 字符串，里面可能包含 keyTab 配置；用正则一次性把所有 UUID 提出
            java.util.regex.Matcher m = Pattern.compile(
                "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\.keytab")
                .matcher(props);
            while (m.find()) {
                ids.add(m.group(1));
            }
        }
        return ids;
    }

    private String readMeta(Path dir, String fileId) {
        Path meta = dir.resolve(fileId + META_EXT);
        if (!Files.exists(meta)) {
            return "";
        }
        try {
            return new String(Files.readAllBytes(meta));
        } catch (IOException e) {
            return "";
        }
    }

    private static String sanitizeForMeta(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\r\\n]", " ");
    }

    private static void safeDelete(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignore) {
        }
    }

    private static void chmod600(Path p) {
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(p, perms);
        } catch (UnsupportedOperationException | IOException ignore) {
            // Windows 等非 POSIX 文件系统忽略
        }
    }
}
