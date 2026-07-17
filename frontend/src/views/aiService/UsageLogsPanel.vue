<!--
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
-->

<template>
  <div class="panel">
    <div class="panel-toolbar">
      <el-select v-model="filterUserId" :placeholder="$t('aiService.filterUser')" clearable style="width:180px" @change="loadData">
        <el-option label="全部用户" :value="null" />
      </el-select>
      <el-button @click="loadData" :loading="loading">
        <el-icon style="margin-right:4px"><Refresh /></el-icon>
        {{ $t('common.refresh') }}
      </el-button>
    </div>

    <el-table :data="logs" v-loading="loading" stripe border style="width:100%">
      <el-table-column prop="id" :label="$t('aiService.id')" width="70" />
      <el-table-column :label="$t('aiService.user')" width="150">
        <template #default="{ row }">
          <div class="user-info-cell">
            <span class="user-name">{{ row.user?.username }}</span>
            <span class="user-email" v-if="row.user?.email">{{ row.user.email }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column :label="$t('aiService.model')" width="140">
        <template #default="{ row }">{{ row.model?.modelName || '—' }}</template>
      </el-table-column>
      <el-table-column :label="$t('aiService.provider')" width="120">
        <template #default="{ row }">{{ row.provider?.name || '—' }}</template>
      </el-table-column>
      <el-table-column :label="$t('aiService.tokens')" width="160" align="center">
        <template #default="{ row }">
          <span class="token-detail">{{ row.promptTokens || 0 }} / {{ row.completionTokens || 0 }} / <strong>{{ row.totalTokens || 0 }}</strong></span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('aiService.cost')" width="100" align="right">
        <template #default="{ row }">{{ row.cost != null ? '$' + row.cost.toFixed(6) : '—' }}</template>
      </el-table-column>
      <el-table-column :label="$t('aiService.latency')" width="100" align="right">
        <template #default="{ row }">{{ row.latencyMs != null ? row.latencyMs + 'ms' : '—' }}</template>
      </el-table-column>
      <el-table-column :label="$t('aiService.fallbackFrom')" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.fallbackFrom" type="warning" size="small" effect="plain" round>Yes</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('aiService.httpStatus')" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.statusCode === 200 ? 'success' : 'danger'" size="small" effect="plain" round>
            {{ row.statusCode || '—' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" :label="$t('aiService.callTime')" width="170" />
    </el-table>

    <!-- 分页 -->
    <div class="pagination-box">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @change="loadData"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getUsageLogs } from '@/api/aiService'

const loading = ref(false)
const logs = ref([])
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filterUserId = ref(null)

async function loadData() {
  loading.value = true
  try {
    const params = { page: currentPage.value, size: pageSize.value }
    if (filterUserId.value) params.userId = filterUserId.value
    const res = await getUsageLogs(params)
    if (res.code === 200) {
      logs.value = res.data.list || []
      total.value = res.data.total || 0
    }
  } catch (e) {
    ElMessage.error('加载日志失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => loadData())

defineExpose({ loadData })
</script>

<style scoped>
.panel { padding-top: 16px; }
.panel-toolbar { display: flex; gap: 8px; margin-bottom: 16px; }
.pagination-box { display: flex; justify-content: flex-end; margin-top: 16px; }
.token-detail { font-size: 12px; color: #606266; }
.user-info-cell { display: flex; flex-direction: column; }
.user-name { font-weight: 500; color: #303133; font-size: 13px; }
.user-email { font-size: 11px; color: #909399; margin-top: 1px; }
</style>
