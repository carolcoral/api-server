<!--
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
-->

<template>
  <div class="record-replay">
    <div class="page-header">
      <h1>{{ $t('recordReplay.title') }}</h1>
      <span class="page-desc">{{ $t('recordReplay.description') }}</span>
    </div>

    <!-- 筛选栏 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="filterForm" size="default">
        <el-form-item :label="$t('recordReplay.project')">
          <el-select v-model="filterForm.projectId" :placeholder="$t('recordReplay.selectProject')" clearable style="width: 200px" @change="handleSearch">
            <el-option v-for="p in projectList" :key="p.id" :label="p.name + ' (' + p.code + ')'" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('recordReplay.path')">
          <el-input v-model="filterForm.path" :placeholder="$t('recordReplay.pathPlaceholder')" clearable style="width: 200px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item :label="$t('recordReplay.method')">
          <el-select v-model="filterForm.method" :placeholder="$t('recordReplay.selectMethod')" clearable style="width: 120px">
            <el-option v-for="m in methods" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('common.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作栏 -->
    <div class="toolbar">
      <el-button
        type="success"
        :disabled="selectedIds.length === 0 || !targetProjectId"
        :loading="batchReplaying"
        @click="handleBatchReplay"
      >
        <el-icon><VideoPlay /></el-icon>
        {{ $t('recordReplay.batchReplay') }} ({{ selectedIds.length }})
      </el-button>
      <el-select v-model="targetProjectId" :placeholder="$t('recordReplay.selectTargetProject')" style="width: 240px; margin-left: 12px">
        <el-option v-for="p in projectList" :key="p.id" :label="p.name + ' (' + p.code + ')'" :value="p.id" />
      </el-select>
      <span class="toolbar-hint">{{ $t('recordReplay.batchReplayHint') }}</span>
      <span class="total-hint">{{ $t('recordReplay.totalRecords', { count: total }) }}</span>
    </div>

    <!-- 录制日志表格 -->
    <el-card shadow="never">
      <el-table
        v-loading="loading"
        :data="recordList"
        border
        stripe
        style="width: 100%"
        :header-cell-style="{ background: '#f5f7fa' }"
        max-height="520"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="45" align="center" />
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="method" :label="$t('recordReplay.method')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="methodTagType(row.method)" size="small">{{ row.method }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" :label="$t('recordReplay.path')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="projectName" :label="$t('recordReplay.project')" width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.projectName">{{ row.projectName }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="statusCode" :label="$t('recordReplay.statusCode')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.statusCode)" size="small">{{ row.statusCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="responseTime" :label="$t('recordReplay.responseTime')" width="110" align="center">
          <template #default="{ row }">
            <span :style="{ color: row.responseTime > 500 ? '#f56c6c' : row.responseTime > 200 ? '#e6a23c' : '#67c23a' }">
              {{ row.responseTime }}ms
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="requestTime" :label="$t('recordReplay.requestTime')" width="170" show-overflow-tooltip />
        <el-table-column :label="$t('permission.role.actions')" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleViewDetail(row)">
              {{ $t('recordReplay.viewDetail') }}
            </el-button>
            <el-button type="success" link :disabled="!canReplay" @click="handleReplaySingle(row)">
              {{ $t('recordReplay.replay') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap" v-if="total > 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="handleSearch"
        />
      </div>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" :title="$t('recordReplay.recordDetail')" width="700px">
      <template v-if="detailData">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item :label="$t('recordReplay.method')">
            <el-tag :type="methodTagType(detailData.method)" size="small">{{ detailData.method }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('recordReplay.path')">{{ detailData.path }}</el-descriptions-item>
          <el-descriptions-item :label="$t('recordReplay.statusCode')">
            <el-tag :type="statusTagType(detailData.statusCode)" size="small">{{ detailData.statusCode }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('recordReplay.responseTime')">{{ detailData.responseTime }}ms</el-descriptions-item>
          <el-descriptions-item :label="$t('recordReplay.project')">{{ detailData.projectName || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('recordReplay.requestTime')">{{ detailData.requestTime }}</el-descriptions-item>
        </el-descriptions>

        <el-divider />

        <h4>{{ $t('recordReplay.requestHeaders') }}</h4>
        <el-input
          type="textarea"
          :rows="4"
          :model-value="formatJson(detailData.requestHeaders)"
          readonly
          style="margin-bottom: 12px"
        />

        <h4>{{ $t('recordReplay.requestBody') }}</h4>
        <el-input
          type="textarea"
          :rows="6"
          :model-value="formatJson(detailData.requestBody)"
          readonly
          style="margin-bottom: 12px"
        />

        <h4>{{ $t('recordReplay.responseBody') }}</h4>
        <el-input
          type="textarea"
          :rows="8"
          :model-value="formatJson(detailData.responseBody)"
          readonly
        />
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VideoPlay } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const userStore = useUserStore()

const canReplay = computed(() => userStore.hasPermission('record-replay:replay'))

const methods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH']

const loading = ref(false)
const recordList = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const selectedIds = ref([])

const projectList = ref([])
const targetProjectId = ref(null)
const batchReplaying = ref(false)

const filterForm = reactive({
  projectId: null,
  path: '',
  method: ''
})

const detailVisible = ref(false)
const detailData = ref(null)

const fetchProjects = async () => {
  try {
    const response = await request.get('/projects/accessible/all')
    if (response.code === 200) {
      projectList.value = response.data || []
    }
  } catch (error) {
    console.error('获取项目列表失败:', error)
  }
}

const fetchRecords = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value
    }
    if (filterForm.projectId) params.projectId = filterForm.projectId
    if (filterForm.path) params.path = filterForm.path
    if (filterForm.method) params.method = filterForm.method

    const response = await request.get('/request-records/list', { params })
    if (response.code === 200) {
      recordList.value = response.data?.list || []
      total.value = response.data?.total || 0
    } else {
      ElMessage.error(response.message || t('recordReplay.fetchFailed'))
    }
  } catch (error) {
    console.error('获取录制记录失败:', error)
    ElMessage.error(t('recordReplay.fetchFailed'))
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchRecords()
}

const handleReset = () => {
  filterForm.projectId = null
  filterForm.path = ''
  filterForm.method = ''
  currentPage.value = 1
  fetchRecords()
}

const handleSelectionChange = (selection) => {
  selectedIds.value = selection.map(r => r.id)
}

const handleViewDetail = async (row) => {
  try {
    const response = await request.get(`/request-records/${row.id}`)
    if (response.code === 200) {
      detailData.value = response.data
      detailVisible.value = true
    } else {
      ElMessage.error(response.message || t('recordReplay.fetchDetailFailed'))
    }
  } catch (error) {
    console.error('获取详情失败:', error)
    ElMessage.error(t('recordReplay.fetchDetailFailed'))
  }
}

const handleReplaySingle = async (row) => {
  if (!targetProjectId.value) {
    ElMessage.warning(t('recordReplay.selectTargetFirst'))
    return
  }
  try {
    await ElMessageBox.confirm(
      t('recordReplay.confirmReplaySingle', { path: row.path, method: row.method }),
      t('recordReplay.replay'),
      { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'info' }
    )
    const response = await request.post(`/request-records/${row.id}/replay`, null, {
      params: { targetProjectId: targetProjectId.value }
    })
    if (response.code === 200) {
      const action = response.data?.action === 'create'
        ? t('recordReplay.created')
        : t('recordReplay.appended')
      ElMessage.success(t('recordReplay.replaySuccess', { action }))
    } else {
      ElMessage.error(response.message || t('recordReplay.replayFailed'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('回放失败:', error)
      ElMessage.error(t('recordReplay.replayFailed'))
    }
  }
}

const handleBatchReplay = async () => {
  if (!targetProjectId.value) {
    ElMessage.warning(t('recordReplay.selectTargetFirst'))
    return
  }
  try {
    await ElMessageBox.confirm(
      t('recordReplay.confirmBatchReplay', { count: selectedIds.value.length }),
      t('recordReplay.batchReplay'),
      { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'info' }
    )
    batchReplaying.value = true
    const response = await request.post('/request-records/batch-replay', selectedIds.value, {
      params: { targetProjectId: targetProjectId.value }
    })
    if (response.code === 200) {
      const success = response.data?.success || 0
      const skip = response.data?.skip || 0
      ElMessage.success(t('recordReplay.batchReplayResult', { success, skip }))
      selectedIds.value = []
    } else {
      ElMessage.error(response.message || t('recordReplay.replayFailed'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('批量回放失败:', error)
      ElMessage.error(t('recordReplay.replayFailed'))
    }
  } finally {
    batchReplaying.value = false
  }
}

const methodTagType = (method) => {
  const map = { GET: 'success', POST: 'primary', PUT: 'warning', DELETE: 'danger', PATCH: 'info' }
  return map[method] || 'info'
}

const statusTagType = (code) => {
  if (code >= 200 && code < 300) return 'success'
  if (code >= 300 && code < 400) return 'warning'
  if (code >= 400) return 'danger'
  return 'info'
}

const formatJson = (data) => {
  if (!data) return ''
  if (typeof data === 'string') {
    try {
      return JSON.stringify(JSON.parse(data), null, 2)
    } catch {
      return data
    }
  }
  try {
    return JSON.stringify(data, null, 2)
  } catch {
    return String(data)
  }
}

onMounted(() => {
  fetchProjects()
  fetchRecords()
})
</script>

<style scoped>
.record-replay {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h1 {
  margin: 0 0 6px 0;
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

.page-desc {
  font-size: 13px;
  color: #909399;
}

.filter-card {
  margin-bottom: 16px;
}

.toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 8px;
}

.toolbar-hint {
  font-size: 12px;
  color: #909399;
  margin-left: 4px;
}

.total-hint {
  font-size: 13px;
  color: #909399;
  margin-left: auto;
}

.text-muted {
  color: #c0c4cc;
}

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

h4 {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px 0;
}
</style>
