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
        {{ $t('aiService.addSubscription') }}
      </el-button>
      <el-button @click="loadData" :loading="loading">
        <el-icon style="margin-right:4px"><Refresh /></el-icon>
        {{ $t('common.refresh') }}
      </el-button>
    </div>

    <el-table :data="subscriptions" v-loading="loading" stripe border style="width:100%">
      <el-table-column prop="id" :label="$t('aiService.id')" width="70" />
      <el-table-column :label="$t('aiService.user')" width="150">
        <template #default="{ row }">
          <div class="user-info-cell">
            <span class="user-name">{{ row.user?.username }}</span>
            <span class="user-email" v-if="row.user?.email">{{ row.user.email }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="provider.name" :label="$t('aiService.provider')" width="120" />
      <el-table-column prop="model.modelName" :label="$t('aiService.model')" min-width="150" />
      <el-table-column prop="priority" :label="$t('aiService.priority')" width="90" align="center" />
      <el-table-column prop="weight" :label="$t('aiService.weight')" width="80" align="center" />
      <el-table-column :label="$t('aiService.fallback')" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.fallbackEnabled ? 'success' : 'info'" size="small" effect="plain" round>
            {{ row.fallbackEnabled ? '✓' : '✗' }}
          </el-tag>
        </template>
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
          <el-button size="small" @click="editSubscription(row)">
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
    <el-dialog v-model="dialogVisible" :title="editing ? $t('aiService.editSubscription') : $t('aiService.addSubscription')" width="540px" destroy-on-close :close-on-click-modal="false" align-center>
      <el-form :model="form" label-width="120px">
        <el-form-item :label="$t('aiService.user')" required>
          <el-select
            v-model="form.userIds"
            :placeholder="$t('user.selectUser')"
            style="width:100%"
            :disabled="!!editing"
            multiple
            filterable
            remote
            reserve-keyword
            :remote-method="fetchUsers"
            :loading="userLoading"
          >
            <el-option
              v-for="u in userOptions"
              :key="u.id"
              :label="u.username + (u.email ? ' (' + u.email + ')' : '')"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('aiService.provider')" required>
          <el-select v-model="form.providerId" :placeholder="$t('aiService.selectProvider')" style="width:100%" :disabled="!!editing">
            <el-option v-for="p in providers" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('aiService.model')" required>
          <el-select v-model="form.modelId" :placeholder="$t('aiService.selectModel')" style="width:100%" :disabled="!!editing" filterable>
            <el-option v-for="m in availableModels" :key="m.id" :label="m.modelName" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('aiService.priority')">
          <el-input-number v-model="form.priority" :min="0" :max="100" style="width:100%" />
        </el-form-item>
        <el-form-item :label="$t('aiService.weight')">
          <el-input-number v-model="form.weight" :min="1" :max="100" style="width:100%" />
        </el-form-item>
        <el-form-item :label="$t('aiService.tags')">
          <el-input v-model="form.tags" :placeholder="$t('aiService.tagsPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('aiService.fallback')">
          <el-switch v-model="form.fallbackEnabled" />
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
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Edit, Delete } from '@element-plus/icons-vue'
import { listProviders, listModels, listSubscriptions, createSubscription, updateSubscription, deleteSubscription } from '@/api/aiService'
import { searchUsers, listEnabledUsers } from '@/api/user'

const loading = ref(false)
const saving = ref(false)
const subscriptions = ref([])
const providers = ref([])
const availableModels = ref([])
const dialogVisible = ref(false)
const editing = ref(null)
const userOptions = ref([])
const userLoading = ref(false)

const form = ref({
  userIds: [], providerId: null, modelId: null,
  priority: 0, weight: 1, tags: '', fallbackEnabled: true, status: true
})

function resetForm() {
  form.value = {
    userIds: [], providerId: null, modelId: null,
    priority: 0, weight: 1, tags: '', fallbackEnabled: true, status: true
  }
}

watch(() => form.value.providerId, async (val) => {
  if (!val) { availableModels.value = []; return }
  try {
    const res = await listModels(val)
    if (res.code === 200) availableModels.value = res.data || []
  } catch (e) { availableModels.value = [] }
})

async function loadData() {
  loading.value = true
  try {
    const [subRes, provRes] = await Promise.all([
      listSubscriptions(),
      listProviders()
    ])
    if (subRes.code === 200) subscriptions.value = subRes.data || []
    if (provRes.code === 200) providers.value = provRes.data || []
  } catch (e) {
    ElMessage.error('加载订阅失败')
  } finally {
    loading.value = false
  }
}

async function loadInitialUsers() {
  userLoading.value = true
  try {
    const res = await listEnabledUsers()
    if (res.code === 200) userOptions.value = res.data || []
  } catch (e) {
    userOptions.value = []
  } finally {
    userLoading.value = false
  }
}

async function fetchUsers(keyword) {
  if (!keyword || !keyword.trim()) {
    await loadInitialUsers()
    return
  }
  userLoading.value = true
  try {
    const res = await searchUsers(keyword.trim())
    if (res.code === 200) userOptions.value = res.data || []
  } catch (e) {
    userOptions.value = []
  } finally {
    userLoading.value = false
  }
}

function ensureUserInOptions(user) {
  if (!user || !user.id) return
  if (!userOptions.value.some(u => u.id === user.id)) {
    userOptions.value = [...userOptions.value, user]
  }
}

function showAddDialog() {
  editing.value = null
  resetForm()
  userOptions.value = []
  loadInitialUsers()
  dialogVisible.value = true
}

function editSubscription(row) {
  editing.value = row
  form.value = {
    userIds: row.user?.id ? [row.user.id] : [], providerId: row.provider?.id, modelId: row.model?.id,
    priority: row.priority, weight: row.weight, tags: row.tags || '',
    fallbackEnabled: row.fallbackEnabled, status: row.status
  }
  userOptions.value = []
  if (row.user) ensureUserInOptions(row.user)
  if (row.provider?.id) {
    listModels(row.provider.id).then(res => {
      if (res.code === 200) availableModels.value = res.data || []
    })
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.userIds?.length || !form.value.providerId || !form.value.modelId) {
    ElMessage.warning('请填写必要信息')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      const payload = { ...form.value, userId: form.value.userIds[0] }
      const res = await updateSubscription(editing.value.id, payload)
      if (res.code === 200) {
        ElMessage.success('更新成功')
        dialogVisible.value = false
        await loadData()
      }
    } else {
      const basePayload = {
        providerId: form.value.providerId,
        modelId: form.value.modelId,
        priority: form.value.priority,
        weight: form.value.weight,
        tags: form.value.tags,
        fallbackEnabled: form.value.fallbackEnabled,
        status: form.value.status
      }
      let success = 0
      let failed = 0
      for (const userId of form.value.userIds) {
        try {
          const res = await createSubscription({ ...basePayload, userId })
          if (res.code === 200) success++
          else failed++
        } catch (e) {
          failed++
        }
      }
      if (failed === 0) {
        ElMessage.success(`成功创建 ${success} 条订阅`)
        dialogVisible.value = false
        await loadData()
      } else {
        ElMessage.warning(`成功 ${success} 条，失败 ${failed} 条`)
        if (success > 0) await loadData()
      }
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(id) {
  try {
    const res = await deleteSubscription(id)
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
</style>
