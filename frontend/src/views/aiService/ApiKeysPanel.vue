<!--
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
-->

<template>
  <div class="panel">
    <div class="panel-toolbar">
      <el-button type="primary" @click="showAddDialog">
        <el-icon style="margin-right:4px"><Plus /></el-icon>
        {{ $t('aiService.generateKey') }}
      </el-button>
      <el-button @click="loadData" :loading="loading">
        <el-icon style="margin-right:4px"><Refresh /></el-icon>
        {{ $t('common.refresh') }}
      </el-button>
    </div>

    <el-table :data="apiKeys" v-loading="loading" stripe border style="width:100%">
      <el-table-column prop="id" :label="$t('aiService.id')" width="70" />
      <el-table-column :label="$t('aiService.user')" width="160">
        <template #default="{ row }">
          <div class="user-info-cell">
            <span class="user-name">{{ row.user?.username }}</span>
            <span class="user-email" v-if="row.user?.email">{{ row.user.email }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="keyName" :label="$t('aiService.keyName')" min-width="120" />
      <el-table-column prop="apiKey" :label="$t('aiService.apiKey')" min-width="240">
        <template #default="{ row }">
          <div class="key-cell">
            <code>{{ maskKey(row.apiKey) }}</code>
            <el-button size="small" text type="primary" @click="copyKey(row.apiKey)">
              <el-icon><CopyDocument /></el-icon>
            </el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column :label="$t('aiService.lastUsed')" width="170">
        <template #default="{ row }">{{ row.lastUsed || '—' }}</template>
      </el-table-column>
      <el-table-column :label="$t('aiService.tokenUsed')" width="130" align="right">
        <template #default="{ row }">{{ formatNumber(row.totalTokensUsed) }}</template>
      </el-table-column>
      <el-table-column :label="$t('aiService.status')" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status ? 'success' : 'info'" size="small" effect="plain" round>
            {{ row.status ? $t('ai.enabled') : $t('ai.disabled') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('aiService.actions')" width="100" fixed="right">
        <template #default="{ row }">
          <el-popconfirm :title="$t('ai.deleteConfigConfirm')" @confirm="handleDelete(row.id)">
            <template #reference>
              <el-button size="small" type="danger" plain>
                <el-icon><Delete /></el-icon>
              </el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 生成 API Key 对话框 -->
    <el-dialog v-model="dialogVisible" :title="$t('aiService.generateKey')" width="480px" destroy-on-close :close-on-click-modal="false" align-center>
      <el-form :model="form" label-width="100px">
        <el-form-item :label="$t('aiService.user')" required>
          <el-input-number v-model="form.userId" :min="1" :placeholder="$t('aiService.userIdPlaceholder')" style="width:100%" />
        </el-form-item>
        <el-form-item :label="$t('aiService.keyName')">
          <el-input v-model="form.keyName" :placeholder="$t('aiService.keyNamePlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleGenerate" :loading="saving">{{ $t('aiService.generate') }}</el-button>
      </template>
    </el-dialog>

    <!-- 显示生成的 Key -->
    <el-dialog v-model="showKeyDialog" :title="$t('aiService.generatedKey')" width="520px" :close-on-click-modal="false" align-center>
      <div class="generated-key-box">
        <div class="key-display">
          <code>{{ generatedKey }}</code>
        </div>
        <p class="key-warning">{{ $t('aiService.keyWarning') }}</p>
        <el-button type="primary" @click="copyGeneratedKey">
          <el-icon style="margin-right:4px"><CopyDocument /></el-icon>
          {{ $t('aiService.copyKey') }}
        </el-button>
      </div>
      <template #footer>
        <el-button @click="showKeyDialog = false">{{ $t('common.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Delete, CopyDocument } from '@element-plus/icons-vue'
import { listApiKeys, createApiKey, deleteApiKey } from '@/api/aiService'

const loading = ref(false)
const saving = ref(false)
const apiKeys = ref([])
const dialogVisible = ref(false)
const showKeyDialog = ref(false)
const generatedKey = ref('')

const form = ref({ userId: null, keyName: 'Default' })

function formatNumber(n) {
  if (n == null) return '—'
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
  if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K'
  return String(n)
}

function maskKey(key) {
  if (!key || key.length <= 16) return key
  return key.substring(0, 8) + '****' + key.substring(key.length - 8)
}

async function copyKey(key) {
  try {
    await navigator.clipboard.writeText(key)
    ElMessage.success('已复制')
  } catch (e) {
    ElMessage.error('复制失败')
  }
}

async function copyGeneratedKey() {
  await copyKey(generatedKey.value)
}

async function loadData() {
  loading.value = true
  try {
    const res = await listApiKeys()
    if (res.code === 200) apiKeys.value = res.data || []
  } catch (e) {
    ElMessage.error('加载API Key失败')
  } finally {
    loading.value = false
  }
}

function showAddDialog() {
  form.value = { userId: null, keyName: 'Default' }
  dialogVisible.value = true
}

async function handleGenerate() {
  if (!form.value.userId) {
    ElMessage.warning('请填写用户ID')
    return
  }
  saving.value = true
  try {
    const res = await createApiKey(form.value)
    if (res.code === 200) {
      dialogVisible.value = false
      generatedKey.value = res.data.apiKey
      showKeyDialog.value = true
      await loadData()
    }
  } catch (e) {
    ElMessage.error('生成失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(id) {
  try {
    const res = await deleteApiKey(id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      await loadData()
    }
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

onMounted(() => loadData())

defineExpose({ loadData })
</script>

<style scoped>
.panel { padding-top: 16px; }
.panel-toolbar { display: flex; gap: 8px; margin-bottom: 16px; }

.user-info-cell { display: flex; flex-direction: column; }
.user-name { font-weight: 500; color: #303133; font-size: 13px; }
.user-email { font-size: 11px; color: #909399; margin-top: 1px; }

.key-cell { display: flex; align-items: center; gap: 4px; }
.key-cell code { font-size: 12px; background: #f5f7fa; padding: 2px 8px; border-radius: 4px; color: #606266; }

.generated-key-box { text-align: center; padding: 16px 0; }
.key-display { background: #f0f9eb; border: 1px solid #b7eb8f; border-radius: 8px; padding: 16px; margin-bottom: 12px; word-break: break-all; }
.key-display code { font-size: 14px; color: #67c23a; }
.key-warning { font-size: 12px; color: #e6a23c; margin-bottom: 12px; }
</style>
