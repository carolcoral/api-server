<!--
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
-->

<template>
  <div class="panel">
    <div class="panel-toolbar">
      <el-select v-model="selectedProvider" :placeholder="$t('aiService.selectProvider')" @change="loadData" class="provider-select">
        <el-option v-for="p in providers" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <el-button type="primary" @click="showAddDialog" :disabled="!selectedProvider">
        <el-icon style="margin-right:4px"><Plus /></el-icon>
        {{ $t('aiService.addModel') }}
      </el-button>
      <el-button @click="loadData" :loading="loading">
        <el-icon style="margin-right:4px"><Refresh /></el-icon>
        {{ $t('common.refresh') }}
      </el-button>
    </div>

    <el-table :data="models" v-loading="loading" stripe border style="width:100%">
      <el-table-column prop="id" :label="$t('aiService.id')" width="70" />
      <el-table-column prop="modelName" :label="$t('aiService.modelName')" min-width="150" />
      <el-table-column prop="displayName" :label="$t('aiService.displayName')" min-width="120" />
      <el-table-column :label="$t('aiService.inputPrice')" width="110" align="right">
        <template #default="{ row }">{{ row.inputPrice != null ? '$' + row.inputPrice : '—' }}</template>
      </el-table-column>
      <el-table-column :label="$t('aiService.outputPrice')" width="110" align="right">
        <template #default="{ row }">{{ row.outputPrice != null ? '$' + row.outputPrice : '—' }}</template>
      </el-table-column>
      <el-table-column :label="$t('aiService.maxTokens')" width="100" align="center">
        <template #default="{ row }">{{ row.maxTokens || '—' }}</template>
      </el-table-column>
      <el-table-column :label="$t('aiService.stream')" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.supportsStream ? 'success' : 'info'" size="small" effect="plain" round>
            {{ row.supportsStream ? '✓' : '✗' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('aiService.health')" width="100" align="center">
        <template #default="{ row }">
          <el-tag
            :type="row.healthStatus === 'online' ? 'success' : row.healthStatus === 'offline' ? 'danger' : 'warning'"
            size="small" effect="plain" round
          >
            {{ $t('aiService.' + row.healthStatus) }}
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
      <el-table-column :label="$t('aiService.actions')" width="180" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="healthCheck(row.id)" :loading="checkingId === row.id" type="warning" plain>
            <el-icon><Link /></el-icon>
          </el-button>
          <el-button size="small" @click="editModel(row)">
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
    <el-dialog v-model="dialogVisible" :title="editing ? $t('aiService.editModel') : $t('aiService.addModel')" width="560px" destroy-on-close :close-on-click-modal="false" align-center>
      <!-- 手动添加 -->
      <el-form v-if="!batchMode" :model="form" label-width="120px">
        <el-form-item :label="$t('aiService.modelName')" required>
          <el-input v-model="form.modelName" :placeholder="$t('aiService.modelNamePlaceholder')" :disabled="!!editing" />
        </el-form-item>
        <el-form-item :label="$t('aiService.displayName')">
          <el-input v-model="form.displayName" :placeholder="$t('aiService.displayNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('aiService.inputPrice')">
          <el-input-number v-model="form.inputPrice" :min="0" :precision="6" :step="0.0001" style="width:100%" />
        </el-form-item>
        <el-form-item :label="$t('aiService.outputPrice')">
          <el-input-number v-model="form.outputPrice" :min="0" :precision="6" :step="0.0001" style="width:100%" />
        </el-form-item>
        <el-form-item :label="$t('aiService.maxTokens')">
          <el-input-number v-model="form.maxTokens" :min="1" :max="131072" :step="256" style="width:100%" />
        </el-form-item>
        <el-form-item :label="$t('aiService.stream')">
          <el-switch v-model="form.supportsStream" />
        </el-form-item>
        <el-form-item :label="$t('aiService.status')">
          <el-switch v-model="form.status" :active-text="$t('ai.enabled')" :inactive-text="$t('ai.disabled')" />
        </el-form-item>
      </el-form>

      <!-- 远程获取批量导入 -->
      <div v-else class="batch-section">
        <div class="fetch-row">
          <el-button type="primary" @click="fetchRemoteModelsHandler" :loading="fetchingRemote" :icon="Download">
            {{ $t('aiService.fetchRemoteModels') }}
          </el-button>
          <span v-if="remoteModels.length" class="fetch-count">
            {{ $t('aiService.remoteModelsFound', { count: remoteModels.length }) }}
          </span>
        </div>
        <el-form label-width="90px" v-if="remoteModels.length" style="margin-top:16px">
          <el-form-item :label="$t('aiService.selectModels')">
            <el-select
              v-model="selectedRemoteModels"
              multiple
              filterable
              :placeholder="$t('aiService.selectModelsPlaceholder')"
              style="width:100%"
              collapse-tags
              collapse-tags-tooltip
            >
              <el-option v-for="m in remoteModels" :key="m" :label="m" :value="m" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="alreadyExist.length" :label="$t('aiService.alreadyExist')">
            <el-tag v-for="m in alreadyExist" :key="m" type="info" size="small" style="margin-right:6px;margin-bottom:4px">{{ m }}</el-tag>
          </el-form-item>
        </el-form>
        <div v-if="!remoteModels.length && !fetchingRemote && fetchAttempted" class="empty-hint">
          <el-empty :description="$t('aiService.noRemoteModels')" :image-size="60" />
        </div>
      </div>

      <template #footer>
        <div style="display:flex;justify-content:space-between;width:100%">
          <el-button v-if="!editing" link type="primary" @click="toggleMode">
            {{ batchMode ? $t('aiService.manualAdd') : $t('aiService.batchImport') }}
          </el-button>
          <div style="display:flex;gap:8px">
            <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
            <el-button v-if="batchMode && selectedRemoteModels.length" type="primary" @click="handleBatchAdd" :loading="saving">
              {{ $t('aiService.batchAdd', { count: selectedRemoteModels.length }) }}
            </el-button>
            <el-button v-if="!batchMode" type="primary" @click="handleSave" :loading="saving">{{ $t('common.save') }}</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Edit, Delete, Link, Download } from '@element-plus/icons-vue'
import { listProviders, listModels, createModel, updateModel, deleteModel, healthCheckModel, fetchProviderModels, batchCreateModels } from '@/api/aiService'

const loading = ref(false)
const saving = ref(false)
const checkingId = ref(null)
const models = ref([])
const providers = ref([])
const selectedProvider = ref(null)
const dialogVisible = ref(false)
const editing = ref(null)
const batchMode = ref(false)
const fetchingRemote = ref(false)
const fetchAttempted = ref(false)
const remoteModels = ref([])
const selectedRemoteModels = ref([])

const form = ref({
  modelName: '', displayName: '', inputPrice: null, outputPrice: null,
  maxTokens: 4096, supportsStream: true, status: true
})

// 与已有模型对比，显示已存在列表
const alreadyExist = computed(() => {
  if (!remoteModels.value.length || !models.value.length) return []
  const existingNames = new Set(models.value.map(m => m.modelName))
  return remoteModels.value.filter(m => existingNames.has(m))
})

function resetForm() {
  form.value = {
    modelName: '', displayName: '', inputPrice: null, outputPrice: null,
    maxTokens: 4096, supportsStream: true, status: true
  }
}

function toggleMode() {
  batchMode.value = !batchMode.value
  remoteModels.value = []
  selectedRemoteModels.value = []
  fetchAttempted.value = false
}

async function loadProviders() {
  try {
    const res = await listProviders()
    if (res.code === 200) {
      providers.value = res.data || []
      if (providers.value.length && !selectedProvider.value) {
        selectedProvider.value = providers.value[0].id
      }
    }
  } catch (e) { /* ignore */ }
}

async function loadData() {
  await loadProviders()
  if (!selectedProvider.value) return
  loading.value = true
  try {
    const res = await listModels(selectedProvider.value)
    if (res.code === 200) models.value = res.data || []
  } catch (e) {
    ElMessage.error('加载模型失败')
  } finally {
    loading.value = false
  }
}

async function fetchRemoteModelsHandler() {
  if (!selectedProvider.value) return
  fetchingRemote.value = true
  fetchAttempted.value = true
  try {
    const res = await fetchProviderModels(selectedProvider.value)
    if (res.code === 200) {
      remoteModels.value = res.data || []
      if (remoteModels.value.length) {
        // 默认过滤掉已存在的模型
        const existingNames = new Set(models.value.map(m => m.modelName))
        selectedRemoteModels.value = remoteModels.value.filter(m => !existingNames.has(m))
        ElMessage.success(`获取到 ${remoteModels.value.length} 个远程模型，已自动筛选 ${selectedRemoteModels.value.length} 个未添加的`)
      } else {
        ElMessage.warning('未能获取到远程模型列表')
      }
    } else {
      ElMessage.error(res.message || '获取远程模型失败')
    }
  } catch (e) {
    ElMessage.error('获取远程模型失败，请检查服务商配置')
  } finally {
    fetchingRemote.value = false
  }
}

async function handleBatchAdd() {
  if (!selectedRemoteModels.value.length) return
  saving.value = true
  try {
    const res = await batchCreateModels(selectedProvider.value, selectedRemoteModels.value)
    if (res.code === 200) {
      const created = res.data || []
      ElMessage.success(`成功添加 ${created.length} 个模型`)
      dialogVisible.value = false
      await loadData()
      emit('stats-changed')
    } else {
      ElMessage.error(res.message || '批量添加失败')
    }
  } catch (e) {
    ElMessage.error('批量添加失败')
  } finally {
    saving.value = false
  }
}

const emit = defineEmits(['stats-changed'])

function showAddDialog() {
  editing.value = null
  batchMode.value = false
  remoteModels.value = []
  selectedRemoteModels.value = []
  fetchAttempted.value = false
  resetForm()
  dialogVisible.value = true
}

function editModel(row) {
  editing.value = row
  batchMode.value = false
  remoteModels.value = []
  selectedRemoteModels.value = []
  fetchAttempted.value = false
  form.value = {
    modelName: row.modelName, displayName: row.displayName || '',
    inputPrice: row.inputPrice, outputPrice: row.outputPrice,
    maxTokens: row.maxTokens || 4096, supportsStream: row.supportsStream,
    status: row.status
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.modelName) {
    ElMessage.warning('请填写模型名称')
    return
  }
  saving.value = true
  try {
    let res
    if (editing.value) {
      res = await updateModel(selectedProvider.value, editing.value.id, form.value)
    } else {
      res = await createModel(selectedProvider.value, form.value)
    }
    if (res.code === 200) {
      ElMessage.success(editing.value ? '更新成功' : '创建成功')
      dialogVisible.value = false
      await loadData()
      if (!editing.value) emit('stats-changed')
    }
  } catch (e) {
    ElMessage.error('操作失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(id) {
  try {
    const res = await deleteModel(selectedProvider.value, id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      await loadData()
      emit('stats-changed')
    }
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

async function healthCheck(id) {
  checkingId.value = id
  try {
    const res = await healthCheckModel(id)
    if (res.code === 200) {
      ElMessage.success(`健康检查完成: ${res.data.status}, 延迟 ${res.data.latencyMs || '—'}ms`)
      await loadData()
    }
  } catch (e) {
    ElMessage.error('健康检查失败')
  } finally {
    checkingId.value = null
  }
}

onMounted(async () => {
  await loadProviders()
  if (selectedProvider.value) await loadData()
})

defineExpose({ loadData })
</script>

<style scoped>
.panel { padding-top: 16px; }
.panel-toolbar { display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; align-items: center; }
.provider-select { width: 240px; }
.provider-select :deep(.el-input__wrapper) {
  border-radius: 6px;
}
.panel-toolbar .el-button {
  height: 32px;
  padding: 0 16px;
  border-radius: 6px;
  font-weight: 500;
}
.panel-toolbar .el-button .el-icon {
  font-size: 14px;
}

.batch-section { padding: 0 4px; }
.fetch-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 4px;
}
.fetch-count {
  font-size: 13px;
  color: var(--el-color-success);
  font-weight: 500;
}
.empty-hint {
  margin-top: 8px;
}
</style>
