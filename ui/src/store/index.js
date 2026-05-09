import Vue from "vue";
import Vuex from "vuex";
import { CLUSTER, AUTH } from "@/store/mutation-types";
import {
  setClusterInfo,
  setPermissions,
  setToken,
  setUsername,
  deleteClusterInfo,
} from "@/utils/local-cache";

Vue.use(Vuex);

export default new Vuex.Store({
  state: {
    clusterInfo: {
      id: undefined,
      clusterName: undefined,
      enableSasl: false,
      // 仅当 mechanism 是 SCRAM-SHA-256 / SCRAM-SHA-512 时为 true
      enableScram: false,
      // 仅当 mechanism 是 GSSAPI 时为 true
      enableGssapi: false,
    },
    auth: {
      enable: false,
      username: "",
      permissions: [],
    },
  },
  mutations: {
    [CLUSTER.SWITCH](state, clusterInfo) {
      state.clusterInfo.id = clusterInfo.id;
      state.clusterInfo.clusterName = clusterInfo.clusterName;
      let enableSasl = false;
      let enableScram = false;
      let enableGssapi = false;
      const props = clusterInfo.properties || [];
      for (let i = 0; i < props.length; i++) {
        const line = props[i] || "";
        if (line.indexOf("security.protocol=SASL") !== -1) {
          enableSasl = true;
        }
        if (line.indexOf("sasl.mechanism=SCRAM") !== -1) {
          enableScram = true;
        }
        if (line.indexOf("sasl.mechanism=GSSAPI") !== -1) {
          enableGssapi = true;
        }
      }
      state.clusterInfo.enableSasl = enableSasl;
      state.clusterInfo.enableScram = enableScram;
      state.clusterInfo.enableGssapi = enableGssapi;
      setClusterInfo(clusterInfo);
    },
    [CLUSTER.DELETE]() {
      deleteClusterInfo();
    },
    [AUTH.ENABLE](state, enable) {
      state.auth.enable = enable;
    },
    [AUTH.SET_TOKEN](state, info) {
      setToken(info);
    },
    [AUTH.SET_USERNAME](state, username) {
      setUsername(username);
      state.auth.username = username;
    },
    [AUTH.SET_PERMISSIONS](state, permissions) {
      setPermissions(permissions);
      state.auth.permissions = permissions;
    },
  },
  actions: {},
  modules: {},
});
