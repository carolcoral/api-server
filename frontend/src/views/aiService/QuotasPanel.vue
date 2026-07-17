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
        {{ $t('aiService.addQuota') }}
      </el-button>
      <el-button @click="loadData" :loading="loading">
        <el-icon style="margin-right:4px"><Refresh /></el-icon>
        {{ $t('common.refresh') }}
      </el-button>
    </div>

    <el-table :data="quotas" v-loading="loading" stripe border style="width:100%">
      <el-table-column prop="id" :label="$t('aiService.id')" width="70" />
      <el-table-column :label="$t('aiService.user')" width="150">
        <template #default="{ row }">
          <div class="user-info-cell">
            <span class="user-name">{{ row.user?.username }}</span>
            <span class="user-email" v-if="row.user?.email">{{ row.user.email }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="tokenLimit" :label="$t('aiService.tokenLimit')" width="120" align="right">
        <template #default="{ row }">{{ formatNumber(row.tokenLimit) }}</template>
      </el-table-column>
      <el-table-column :label="$t('aiService.tokenUsed')" width="120" align="right">
        <template #default="{ row }">
          <span :style="{ color: row.tokenUsed > row.tokenLimit * 0.8 ? '#f56c6c' : '#606266' }">
            {{ formatNumber(row.tokenUsed) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('aiService.usagePercent')" width="140">
        <template #default="{ row }">
          <el-progress :percentage="getPercent(row)" :color="getPercent(row) > 80 ? '#f56c6c' : '#409eff'" />
        </template>
      </el-table-column>
      <el-table-column :label="$t('aiService.timeWindow')" width="130">
        <template #default="{ row }">{{ formatSeconds(row.timeWindowSeconds) }}</template>
      </el-table-column>
      <el-table-column :label="$t('aiService.status')" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status ? 'success' : 'info'" size="small" effect="plain" round>
            {{ row.status ? $t('ai.enabled') : $t('ai.disabled') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('aiService.actions')" width="140" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="editQuota(row)">
            <el-icon><Edit /></el-icon>
          </el-button>
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

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editing ? $t('aiService.editQuota') : $t('aiService.addQuota')" width="500px" destroy-on-close :close-on-click-modal="false" align-center>
      <el-form :model="form" label-width="140px">
        <el-form-item :label="$t('aiService.user')" required>
          <el-select
            v-model="form.userId"
            filterable
            :placeholder="$t('aiService.selectUser')"
            style="width:100%"
            :disabled="!!editing"
          >
            <el-option
              v-for="u in userList"
              :key="u.id"
              :label="`${u.username} (${u.email || ''})`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('aiService.tokenLimit')" required>
          <el-input-number v-model="form.tokenLimit" :min="1" :step="1000" style="width:100%" />
        </el-form-item>
        <el-form-item :label="$t('aiService.timeWindow')">
          <el-select v-model="form.timeWindowSeconds" style="width:100%">
            <el-option :label="$t('aiService.window1h')" :value="3600" />
            <el-option :label="$t('aiService.window5h')" :value="18000" />
            <el-option :label="$t('aiService.window24h')" :value="86400" />
            <el-option :label="$t('aiService.window7d')" :value="604800" />
            <el-option :label="$t('aiService.window30d')" :value="2592000" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('aiService.status')">
          <el-switch v-model="form.status" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Edit, Delete } from '@element-plus/icons-vue'
import { listQuotas, createQuota, updateQuota, deleteQuota, listAiUsers } from '@/api/aiService'
import request from '@/utils/request'

const loading = ref(false)
const saving = ref(false)
const quotas = ref([])
const userList = ref([])
const dialogVisible = ref(false)
const editing = ref(null)

const form = ref({
  userId: null, tokenLimit: 100000, timeWindowSeconds: 18000, status: true
})

function resetForm() {
  form.value = { userId: null, tokenLimit: 100000, timeWindowSeconds: 18000, status: true }
}

function formatNumber(n) {
  if (n == null) return '—'
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
  if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K'
  return String(n)
}

function formatSeconds(s) {
  if (s >= 86400) return Math.floor(s / 86400) + '天'
  if (s >= 3600) return Math.floor(s / 3600) + '小时'
  return Math.floor(s / 60) + '分钟'
}

function getPercent(row) {
  if (!row.tokenLimit) return 0
  return Math.min(100, Math.round((row.tokenUsed || 0) / row.tokenLimit * 100))
}

async function loadData() {
  loading.value = true
  try {
    const [quotaRes, userRes] = await Promise.all([
      listQuotas(),
      listAiUsers()
    ])
    if (quotaRes.code === 200) quotas.value = quotaRes.data || []
    if (userRes.code === 200) userList.value = userRes.data || []
  } catch (e) {
    ElMessage.error('加载额度失败')
  } finally {
    loading.value = false
  }
}

function showAddDialog() {
  editing.value = null
  resetForm()
  dialogVisible.value = true
}

function editQuota(row) {
  editing.value = row
  form.value = {
    userId: row.user?.id,
    tokenLimit: row.tokenLimit,
    timeWindowSeconds: row.timeWindowSeconds,
    status: row.status
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.userId || !form.value.tokenLimit) {
    ElMessage.warning('请填写必要信息')
    return
  }
  saving.value = true
  try {
    let res
    if (editing.value) {
      res = await updateQuota(editing.value.id, form.value)
    } else {
      res = await createQuota(form.value)
    }
    if (res.code === 200) {
      ElMessage.success(editing.value ? '更新成功' : '创建成功')
      dialogVisible.value = false
      await loadData()
    }
  } catch (e) {
    ElMessage.error('操作失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(id) {
  try {
    const res = await deleteQuota(id)
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
.user-info-cell { display: flex; flex-direction: column; line-height: 1.4; }
.user-name { font-weight: 500; color: #303133; }
.user-email { font-size: 12px; color: #909399; }
</style>
