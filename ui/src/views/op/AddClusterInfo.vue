<template>
  <a-modal
    title="增加集群配置"
    :visible="show"
    :width="1000"
    :mask="false"
    :destroyOnClose="true"
    :footer="null"
    :maskClosable="false"
    @cancel="handleCancel"
  >
    <div>
      <a-spin :spinning="loading">
        <a-form
          :form="form"
          :label-col="{ span: 5 }"
          :wrapper-col="{ span: 16 }"
          @submit="handleSubmit"
        >
          <a-form-item label="集群名称">
            <a-input
              v-decorator="[
                'clusterName',
                {
                  rules: [{ required: true, message: '输入集群名称!' }],
                  initialValue: clusterInfo.clusterName,
                },
              ]"
              placeholder="输入集群名称"
            />
          </a-form-item>
          <a-form-item label="集群地址">
            <a-input
              v-decorator="[
                'address',
                {
                  rules: [{ required: true, message: '输入集群地址!' }],
                  initialValue: clusterInfo.address,
                },
              ]"
              placeholder="例: broker1:9092,broker2:9092"
            />
          </a-form-item>

          <a-form-item label="认证方式">
            <a-select
              v-model="authMode"
              style="width: 320px"
              @change="onAuthModeChange"
            >
              <a-select-option value="NONE">无（PLAINTEXT）</a-select-option>
              <a-select-option value="SASL_PLAINTEXT_SCRAM"
                >SASL_PLAINTEXT + SCRAM</a-select-option
              >
              <a-select-option value="SASL_SSL_SCRAM"
                >SASL_SSL + SCRAM</a-select-option
              >
              <a-select-option value="SASL_PLAINTEXT_GSSAPI"
                >SASL_PLAINTEXT + Kerberos(GSSAPI)</a-select-option
              >
              <a-select-option value="SASL_SSL_GSSAPI"
                >SASL_SSL + Kerberos(GSSAPI)</a-select-option
              >
            </a-select>
          </a-form-item>

          <template v-if="isKerberos">
            <a-form-item label="Kerberos 服务名">
              <a-input
                v-model="kerberos.serviceName"
                placeholder="一般是 kafka"
                style="width: 320px"
              />
            </a-form-item>
            <a-form-item label="Principal">
              <a-input
                v-model="kerberos.principal"
                placeholder="例: kafka-client@EXAMPLE.COM"
                style="width: 420px"
              />
            </a-form-item>
            <a-form-item label="Keytab">
              <div>
                <a-select
                  v-model="kerberos.keytabFileId"
                  show-search
                  style="width: 420px; margin-right: 8px"
                  :filter-option="filterKeytab"
                  placeholder="选择已上传的 keytab"
                >
                  <a-select-option
                    v-for="k in keytabList"
                    :key="k.fileId"
                    :value="k.fileId"
                  >
                    {{ k.originalFilename || k.fileId }} ({{
                      formatSize(k.size)
                    }})
                  </a-select-option>
                </a-select>
                <a-upload
                  :before-upload="beforeKeytabUpload"
                  :show-upload-list="false"
                  accept=".keytab"
                >
                  <a-button>
                    <a-icon type="upload" /> 上传新 keytab
                  </a-button>
                </a-upload>
                <a-button
                  type="link"
                  @click="loadKeytabList"
                  style="margin-left: 4px"
                  >刷新</a-button
                >
              </div>
              <div style="color: #999; font-size: 12px; margin-top: 4px">
                请先上传集群对应 principal 的 keytab；keytab 文件默认存放在
                ${data.dir}/data/keytab 下，仅服务端可读
              </div>
            </a-form-item>
          </template>

          <a-form-item :label="isKerberos ? '高级（其它属性）' : '属性'">
            <a-textarea
              rows="5"
              :placeholder="propertiesPlaceholder"
              v-decorator="[
                'properties',
                { initialValue: clusterInfo.properties },
              ]"
            />
          </a-form-item>
          <a-form-item :wrapper-col="{ span: 16, offset: 5 }">
            <a-button
              :loading="testing"
              @click="handleTestConnection"
              style="margin-right: 8px"
              >测试连接</a-button
            >
            <a-button type="primary" html-type="submit"> 提交</a-button>
          </a-form-item>
        </a-form>
      </a-spin>
    </div>
  </a-modal>
</template>

<script>
import request from "@/utils/request";
import { KafkaClusterApi } from "@/utils/api";
import notification from "ant-design-vue/es/notification";
import { getClusterInfo } from "@/utils/local-cache";
import { mapMutations } from "vuex";
import { CLUSTER } from "@/store/mutation-types";

const SCRAM_PLACEHOLDER = `可选参数，集群其它属性配置：
request.timeout.ms=10000
security.protocol=SASL_PLAINTEXT
sasl.mechanism=SCRAM-SHA-256
sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required username="name" password="password";
`;

const KERBEROS_PLACEHOLDER = `可选参数，例如 SSL truststore（SASL_SSL 时填）：
ssl.truststore.location=/path/to/client.truststore.jks
ssl.truststore.password=xxx
`;

export default {
  name: "AddClusterInfo",
  props: {
    visible: {
      type: Boolean,
      default: false,
    },
    isModify: {
      type: Boolean,
      default: false,
    },
    clusterInfo: {
      type: Object,
      default: () => defaultInfo,
    },
    closeDialogEvent: {
      type: String,
      default: "closeAddClusterInfoDialog",
    },
  },
  data() {
    return {
      show: this.visible,
      loading: false,
      testing: false,
      form: this.$form.createForm(this, { name: "AddClusterInfoForm" }),
      authMode: "NONE",
      kerberos: {
        serviceName: "kafka",
        principal: "",
        keytabFileId: undefined,
      },
      keytabList: [],
    };
  },
  computed: {
    isKerberos() {
      return this.authMode.endsWith("_GSSAPI");
    },
    propertiesPlaceholder() {
      return this.isKerberos ? KERBEROS_PLACEHOLDER : SCRAM_PLACEHOLDER;
    },
  },
  watch: {
    visible(v) {
      this.show = v;
      if (v) {
        this.loadKeytabList();
      }
    },
  },
  mounted() {
    if (this.show) {
      this.loadKeytabList();
    }
  },
  methods: {
    onAuthModeChange() {
      // 切到非 Kerberos 时清掉 Kerberos 字段，避免误提交
      if (!this.isKerberos) {
        this.kerberos.principal = "";
        this.kerberos.keytabFileId = undefined;
      }
    },
    filterKeytab(input, option) {
      const text = (option.componentOptions.children[0].text || "")
        .toString()
        .toLowerCase();
      return text.indexOf(input.toLowerCase()) >= 0;
    },
    formatSize(bytes) {
      if (!bytes) return "0B";
      if (bytes < 1024) return bytes + "B";
      if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + "KB";
      return (bytes / 1024 / 1024).toFixed(1) + "MB";
    },
    loadKeytabList() {
      request({
        url: KafkaClusterApi.listKeytab.url,
        method: KafkaClusterApi.listKeytab.method,
      }).then((res) => {
        if (res.code === 0) {
          this.keytabList = res.data || [];
        }
      });
    },
    beforeKeytabUpload(file) {
      const formData = new FormData();
      formData.append("file", file);
      request({
        url: KafkaClusterApi.uploadKeytab.url,
        method: KafkaClusterApi.uploadKeytab.method,
        data: formData,
        headers: { "Content-Type": "multipart/form-data" },
      }).then((res) => {
        if (res.code === 0) {
          this.$message.success("上传成功");
          this.loadKeytabList();
          if (res.data && res.data.fileId) {
            this.kerberos.keytabFileId = res.data.fileId;
          }
        } else {
          notification.error({ message: "上传失败", description: res.msg });
        }
      });
      return false; // 阻止 antd 默认上传逻辑
    },
    buildPayload(values) {
      const base = this.isModify
        ? Object.assign({}, this.clusterInfo, values)
        : Object.assign({}, values);
      const payload = {
        id: base.id,
        clusterName: base.clusterName,
        address: base.address,
        properties: base.properties,
      };
      switch (this.authMode) {
        case "SASL_PLAINTEXT_SCRAM":
          payload.securityProtocol = "SASL_PLAINTEXT";
          break;
        case "SASL_SSL_SCRAM":
          payload.securityProtocol = "SASL_SSL";
          break;
        case "SASL_PLAINTEXT_GSSAPI":
          payload.securityProtocol = "SASL_PLAINTEXT";
          payload.saslMechanism = "GSSAPI";
          payload.kerberosServiceName = this.kerberos.serviceName || "kafka";
          payload.kerberosPrincipal = this.kerberos.principal;
          payload.keytabFileId = this.kerberos.keytabFileId;
          break;
        case "SASL_SSL_GSSAPI":
          payload.securityProtocol = "SASL_SSL";
          payload.saslMechanism = "GSSAPI";
          payload.kerberosServiceName = this.kerberos.serviceName || "kafka";
          payload.kerberosPrincipal = this.kerberos.principal;
          payload.keytabFileId = this.kerberos.keytabFileId;
          break;
        default:
          break;
      }
      return payload;
    },
    validateKerberosFields() {
      if (!this.isKerberos) return true;
      if (!this.kerberos.principal) {
        this.$message.error("请填写 Principal");
        return false;
      }
      if (!this.kerberos.keytabFileId) {
        this.$message.error("请上传并选择 keytab");
        return false;
      }
      return true;
    },
    handleTestConnection() {
      this.form.validateFields((err, values) => {
        if (err) return;
        if (!this.validateKerberosFields()) return;
        this.testing = true;
        const payload = this.buildPayload(values);
        request({
          url: KafkaClusterApi.testConnection.url,
          method: KafkaClusterApi.testConnection.method,
          data: payload,
        })
          .then((res) => {
            this.testing = false;
            if (res.code === 0) {
              const d = res.data || {};
              notification.success({
                message: "连接成功",
                description: `clusterId=${d.clusterId}, broker 数=${d.nodeCount}`,
              });
            } else {
              notification.error({
                message: "连接失败",
                description: res.msg,
                duration: 0,
              });
            }
          })
          .catch(() => {
            this.testing = false;
          });
      });
    },
    handleSubmit(e) {
      e.preventDefault();
      this.form.validateFields((err, values) => {
        if (err) return;
        if (!this.validateKerberosFields()) return;
        this.loading = true;
        const api = this.isModify
          ? KafkaClusterApi.updateClusterInfo
          : KafkaClusterApi.addClusterInfo;
        const payload = this.buildPayload(values);
        request({
          url: api.url,
          method: api.method,
          data: payload,
        }).then((res) => {
          this.loading = false;
          if (res.code == 0) {
            this.$message.success(res.msg);
            this.$emit(this.closeDialogEvent, { refresh: true });
            if (this.isModify) {
              let clusterInfo = getClusterInfo();
              if (
                clusterInfo &&
                clusterInfo.id &&
                clusterInfo.id == this.clusterInfo.id
              ) {
                this.switchCluster(payload);
              }
            }
          } else {
            notification.error({
              message: "error",
              description: res.msg,
            });
          }
        });
      });
    },
    handleCancel() {
      this.$emit(this.closeDialogEvent, { refresh: false });
    },
    ...mapMutations({
      switchCluster: CLUSTER.SWITCH,
    }),
  },
};
const defaultInfo = { clusterName: "", address: "", properties: "" };
</script>

<style scoped></style>
