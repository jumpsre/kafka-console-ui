# Kerberos 集成使用说明

控制台 v1.1.x 起支持以 Kerberos(GSSAPI) 机制管理 Kafka 集群。

## 前置条件

1. KDC 可达，已经为客户端发放了 principal 和 keytab。常用 principal 形如 `kafka-client@EXAMPLE.COM`。
2. Kafka 服务端已配置 SASL（SASL_PLAINTEXT 或 SASL_SSL），并启用了 GSSAPI 机制。
3. JDK 8+。JDK 9+ 由启动脚本自动追加 `--add-opens java.security.jgss/sun.security.jgss=ALL-UNNAMED`。

## 一、krb5.conf 配置

`java.security.krb5.conf` 是 JVM 全局配置，**整个进程只有一份**。多个 Kerberos 集群必须共享同一份 `krb5.conf`，通过在 `[realms]` 段下叠加多个 realm 来支持。

### 步骤
1. 拷贝 `config/krb5.conf.template` 为 `config/krb5.conf`
2. 修改 `default_realm` 为业务默认 realm
3. 在 `[realms]` 下补全所有 KDC 信息
4. 在 `[domain_realm]` 下把域名映射到对应 realm
5. 重启 `bin/start.sh`（或 Windows 下的 `start.bat` / `start.ps1`），启动脚本会自动检测并注入 `-Djava.security.krb5.conf`

### 多 realm 示例

```
[libdefaults]
    default_realm = EXAMPLE.COM
    dns_lookup_realm = false
    dns_lookup_kdc = false
    ticket_lifetime = 24h
    forwardable = true

[realms]
    EXAMPLE.COM = {
        kdc = kdc1.example.com
        kdc = kdc2.example.com
    }
    FOO.LOCAL = {
        kdc = kdc.foo.local
    }

[domain_realm]
    .example.com = EXAMPLE.COM
    .foo.local = FOO.LOCAL
```

> **注意**：修改 krb5.conf 后必须**重启进程**才能生效，控制台 UI 上没有热加载入口。

## 二、上传 keytab

1. 进入 [运维 → 集群切换 → 新增集群]
2. 在 [认证方式] 下拉里选 `SASL_PLAINTEXT + Kerberos(GSSAPI)` 或 `SASL_SSL + Kerberos(GSSAPI)`
3. 填写：
   - Kerberos 服务名：默认 `kafka`，与 Kafka 服务端 `sasl.kerberos.service.name` 一致
   - Principal：例如 `kafka-client@EXAMPLE.COM`
   - Keytab：点击 [上传新 keytab]，文件大小默认上限 64KB
4. 点击 [测试连接]，看到 `连接成功 clusterId=xxx, broker 数=N` 表示 Kerberos 协商通过
5. 提交保存

服务端会把 keytab 存放在 `${data.dir}/data/keytab/{uuid}.keytab`，权限 600（POSIX 系统）。

## 三、ACL 与用户管理

- **ACL 操作**（producer/consumer 授权、查看、删除）：Kerberos 集群完全支持
- **SCRAM 用户管理**：Kerberos 集群下隐藏（按钮置灰），principal 由 KDC 统一管理，控制台不越权操作

## 四、与 SASL_SSL 叠加

如果服务端是 `SASL_SSL`，除了上面的 Kerberos 字段，需要在表单的 [高级] 文本框里追加 SSL truststore 配置：

```
ssl.truststore.location=/path/to/client.truststore.jks
ssl.truststore.password=changeit
```

## 五、常见报错

| 报错信息 | 可能原因 | 排查 |
|---|---|---|
| `KrbException: Cannot locate default realm` | krb5.conf 未加载 | 启动日志找 `Found krb5.conf` 字样；缺则把模板拷成 `config/krb5.conf` 后重启 |
| `KrbException: Pre-authentication information was invalid (24)` | keytab 与 principal 不匹配 / keytab 过期 | 在 KDC 机器上 `klist -k -t /path/to.keytab` 看 KVNO；与 KDC 上 `kadmin -q "getprinc xxx"` 对比 |
| `GSS initiate failed [...] No valid credentials provided` | 时间偏差 > 5 分钟 / SPN 不对 | 检查 broker 主机名解析、`sasl.kerberos.service.name`、`krb5.conf [domain_realm]` 映射；同步 NTP |
| `LoginException: Unable to obtain password from user` | keytab 文件不可读 / 路径错误 | 看应用日志中实际拼出的 keyTab 路径；检查权限和拼写 |
| `Server not found in Kerberos database (7)` | broker 主机名 / 反向解析在 KDC 中没注册 | 在 broker 端执行 `kinit -kt server.keytab kafka/host@REALM`；正向反向解析必须互通 |
| `org.apache.kafka.common.errors.SaslAuthenticationException: Authentication failed` | 服务端不接受该 principal | broker 日志看具体拒绝原因；ACL 中是否给该 principal 授权 |
| `ClusterAuthorizationException: Request ... is not authorized` + 错误里能看到 `Session(User:xxx,...)` | **Kerberos 认证已成功**，但 broker ACL 没给这个 principal `Cluster:Describe` 等权限。控制台所有页面都依赖 `DescribeCluster`，没有就全报这个 | 见下方"Cluster ACL 授权"小节 |

### Cluster ACL 授权（最常踩的坑）

如果错误信息形如：
```
ClusterAuthorizationException: Request Request(...session=Session(User:xxx,...))
listenerName=ListenerName(SASL_PLAINTEXT)... is not authorized
```

`Session(User:xxx,...)` 出现就说明 **Kerberos 认证已通过**，纯粹是 broker ACL 没给这个 principal 集群级别（Cluster scope）的权限。控制台所有页面都依赖 `DescribeCluster`（取 broker 列表 + cluster id），没这个授权连首页都打不开。

由 broker 管理员（具备 `super.users` 权限的账号）执行：

**最小授权（仅控制台基本浏览）**
```bash
kafka-acls.sh --bootstrap-server <broker:9092> \
  --command-config admin.properties \
  --add \
  --allow-principal "User:<你的-principal-短名>" \
  --operation Describe --operation DescribeConfigs \
  --cluster
```

**完整授权（含改配置、重分配、限流等运维功能）**
```bash
kafka-acls.sh --bootstrap-server <broker:9092> \
  --command-config admin.properties \
  --add \
  --allow-principal "User:<你的-principal-短名>" \
  --operation Describe --operation DescribeConfigs \
  --operation Alter --operation AlterConfigs \
  --operation ClusterAction \
  --cluster
```

**注意**：`User:xxx` 的 `xxx` 是错误信息 `Session(User:xxx,...)` 里的格式（**短名，不带 `@REALM`**）。写成 fullname 会匹配不上。

**查现状**：
```bash
kafka-acls.sh --bootstrap-server <broker:9092> \
  --command-config admin.properties \
  --list \
  --principal "User:<你的-principal-短名>"
```

**super.users 方式**（不推荐生产）：在 `server.properties` 里追加 `super.users=User:admin;User:<your-principal>`，重启 broker 或动态配置。

### 打开调试日志

排错时把 `bin/start.sh` 里 `-Dsun.security.krb5.debug=false` 改为 `true` 重启，会打印详细的 Kerberos 协商过程。

## 六、运维操作命令

```bash
# 查看 keytab 中的 principal 和 KVNO
klist -k -t /path/to/xxx.keytab

# 验证 keytab + principal 能否成功获取 ticket
kinit -kt /path/to/xxx.keytab kafka-client@EXAMPLE.COM
klist
kdestroy
```

## 七、限制说明

- `java.security.krb5.conf` 是 JVM 全局，多个 Kerberos 集群必须使用同一份 krb5.conf（多 realm 共存）
- 控制台不提供 keytab 下载功能（已上传的 keytab 仅服务端可读，避免外泄）
- 不支持运行时切换 krb5.conf；上传/替换文件后需重启
- Kerberos 集群下"用户管理"按钮置灰；如需新增 principal，请联系 KDC 管理员
