<script setup lang="ts">
import { ref } from "vue";
import axios from "axios";

interface SyncResult {
  added: number;
  skipped: number;
  message: string;
}

interface PolicyItem {
  metadata: { name: string };
  spec: { displayName: string; templateName: string };
}

const policies = ref<PolicyItem[]>([]);
const selectedPolicy = ref("");
const syncing = ref(false);
const result = ref<SyncResult | null>(null);
const error = ref("");

async function loadPolicies() {
  try {
    const { data } = await axios.get("/api/v1alpha1/storage.halo.run/policies");
    policies.value = (data.items || []).filter(
      (p: PolicyItem) => p.spec.templateName === "openlist"
    );
    if (policies.value.length > 0 && !selectedPolicy.value) {
      selectedPolicy.value = policies.value[0].metadata.name;
    }
  } catch (e: any) {
    error.value = "加载存储策略失败: " + (e.message || e);
  }
}

async function doSync() {
  if (!selectedPolicy.value) return;
  syncing.value = true;
  result.value = null;
  error.value = "";
  try {
    const { data } = await axios.post(
      `/apis/api.console.halo.run/v1alpha1/openlist/sync`,
      null,
      { params: { policyName: selectedPolicy.value } }
    );
    result.value = data;
  } catch (e: any) {
    error.value = "同步失败: " + (e.response?.data?.message || e.message || e);
  } finally {
    syncing.value = false;
  }
}

loadPolicies();
</script>

<template>
  <div style="padding: 16px; max-width: 600px">
    <h3 style="margin: 0 0 8px 0; font-size: 16px; font-weight: 600">
      同步 OpenList 文件
    </h3>
    <p style="margin: 0 0 16px 0; color: #666; font-size: 14px">
      扫描 OpenList 存储目录，将未在 Halo 中注册的文件自动导入附件库。
    </p>

    <div style="margin-bottom: 12px">
      <label style="display: block; margin-bottom: 4px; font-size: 14px; font-weight: 500">
        存储策略
      </label>
      <select
        v-model="selectedPolicy"
        style="
          width: 100%;
          padding: 8px 12px;
          border: 1px solid #d1d5db;
          border-radius: 6px;
          font-size: 14px;
          outline: none;
        "
      >
        <option v-if="policies.length === 0" value="" disabled>
          未找到 OpenList 存储策略
        </option>
        <option
          v-for="p in policies"
          :key="p.metadata.name"
          :value="p.metadata.name"
        >
          {{ p.spec.displayName || p.metadata.name }}
        </option>
      </select>
    </div>

    <button
      :disabled="syncing || !selectedPolicy"
      style="
        padding: 8px 20px;
        background: #4f46e5;
        color: white;
        border: none;
        border-radius: 6px;
        font-size: 14px;
        cursor: pointer;
        opacity: 1;
      "
      :style="{ opacity: syncing || !selectedPolicy ? 0.5 : 1 }"
      @click="doSync"
    >
      {{ syncing ? "同步中..." : "同步文件" }}
    </button>

    <div
      v-if="result"
      style="
        margin-top: 16px;
        padding: 12px 16px;
        background: #f0fdf4;
        border: 1px solid #bbf7d0;
        border-radius: 6px;
        font-size: 14px;
        color: #166534;
      "
    >
      同步完成：新增 {{ result.added }} 个文件，跳过 {{ result.skipped }} 个已存在文件。
    </div>

    <div
      v-if="error"
      style="
        margin-top: 16px;
        padding: 12px 16px;
        background: #fef2f2;
        border: 1px solid #fecaca;
        border-radius: 6px;
        font-size: 14px;
        color: #991b1b;
      "
    >
      {{ error }}
    </div>
  </div>
</template>
