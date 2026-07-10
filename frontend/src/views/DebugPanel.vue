<!--
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
-->

<template>
  <div class="debug-panel">
    <!-- 顶部概览卡片 -->
    <el-row :gutter="16" class="overview-row">
      <el-col :xs="12" :sm="6">
        <el-card class="overview-card" shadow="hover">
          <div class="overview-label">{{ $t('debug.totalRequests') }}</div>
          <div class="overview-value primary">{{ overview.totalRequests }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="overview-card" shadow="hover">
          <div class="overview-label">{{ $t('debug.avgDelay') }}</div>
          <div class="overview-value warning">{{ overview.avgDelay }}<span class="unit">ms</span></div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="overview-card" shadow="hover">
          <div class="overview-label">{{ $t('debug.errorRate') }}</div>
          <div class="overview-value" :class="overview.errorRate > 0 ? 'danger' : 'success'">{{ overview.errorRate }}<span class="unit">%</span></div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="overview-card" shadow="hover">
          <div class="overview-label">{{ $t('debug.maxDelay') }}</div>
          <div class="overview-value info">{{ overview.maxDelay }}<span class="unit">ms</span></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 延迟分布与百分位 -->
    <el-row :gutter="16" class="chart-row" align="stretch">
      <el-col :xs="24" :lg="14">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <div class="card-header">
              <span>{{ $t('debug.delayDistribution') }}</span>
              <el-select v-model="delayMinutes" size="small" style="width:120px" @change="fetchDelayDistribution">
                <el-option :value="5" label="5 min" />
                <el-option :value="15" label="15 min" />
                <el-option :value="30" label="30 min" />
                <el-option :value="60" label="1 h" />
                <el-option :value="360" label="6 h" />
              </el-select>
            </div>
          </template>
          <div ref="delayChart" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="10">
        <el-card shadow="hover" class="chart-card percentile-card">
          <template #header>
            <span>{{ $t('debug.percentileStats') }}</span>
          </template>
          <div class="percentile-content">
            <div class="percentile-grid">
            <div class="percentile-item">
              <div class="percentile-label">P50</div>
              <div class="percentile-value">{{ delayStats.p50 }}<span class="unit">ms</span></div>
            </div>
            <div class="percentile-item">
              <div class="percentile-label">P90</div>
              <div class="percentile-value">{{ delayStats.p90 }}<span class="unit">ms</span></div>
            </div>
            <div class="percentile-item">
              <div class="percentile-label">P95</div>
              <div class="percentile-value">{{ delayStats.p95 }}<span class="unit">ms</span></div>
            </div>
            <div class="percentile-item">
              <div class="percentile-label">P99</div>
              <div class="percentile-value">{{ delayStats.p99 }}<span class="unit">ms</span></div>
            </div>
          </div>
          <div class="percentile-avg">
            <span class="avg-label">{{ $t('debug.avgDelay') }}</span>
            <span class="avg-value">{{ delayStats.avgDelay }}<span class="unit">ms</span></span>
          </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 请求日志列表 -->
    <el-card shadow="hover" class="log-table-card">
      <template #header>
        <div class="card-header">
          <span>{{ $t('debug.requestLogs') }}</span>
          <div class="header-actions">
            <el-button size="small" @click="fetchLogs" :loading="logLoading" circle>
              <el-icon><Refresh /></el-icon>
            </el-button>
            <el-switch
              v-model="autoRefresh"
              :active-text="$t('debug.autoRefresh')"
              size="small"
              @change="toggleAutoRefresh"
            />
          </div>
        </div>
      </template>

      <!-- 筛选栏 -->
      <div class="filter-bar">
        <el-input
          v-model="filters.path"
          :placeholder="$t('debug.filterPath')"
          clearable
          size="small"
          style="width:180px"
          @clear="fetchLogs"
          @keyup.enter="fetchLogs"
        />
        <el-select
          v-model="filters.method"
          :placeholder="$t('debug.filterMethod')"
          clearable
          size="small"
          style="width:100px"
          @change="fetchLogs"
          @clear="fetchLogs"
        >
          <el-option label="GET" value="GET" />
          <el-option label="POST" value="POST" />
          <el-option label="PUT" value="PUT" />
          <el-option label="DELETE" value="DELETE" />
          <el-option label="PATCH" value="PATCH" />
        </el-select>
        <el-select
          v-model="filters.statusCode"
          :placeholder="$t('debug.filterStatus')"
          clearable
          size="small"
          style="width:100px"
          @change="fetchLogs"
          @clear="fetchLogs"
        >
          <el-option label="200" :value="200" />
          <el-option label="201" :value="201" />
          <el-option label="400" :value="400" />
          <el-option label="404" :value="404" />
          <el-option label="500" :value="500" />
        </el-select>
        <el-button size="small" type="primary" @click="fetchLogs">
          <el-icon><Search /></el-icon>
        </el-button>
        <el-button size="small" @click="resetFilters">
          {{ $t('common.reset') }}
        </el-button>
      </div>

      <!-- 日志表格 -->
      <el-table
        :data="logList"
        v-loading="logLoading"
        stripe
        size="small"
        class="log-table"
        @sort-change="handleSortChange"
      >
        <el-table-column prop="id" label="ID" width="60" sortable="custom" />
        <el-table-column prop="method" :label="$t('debug.method')" width="80">
          <template #default="{ row }">
            <el-tag :type="methodTagType(row.method)" size="small" effect="dark">
              {{ row.method }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" :label="$t('debug.path')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="statusCode" :label="$t('debug.statusCode')" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.statusCode)" size="small" effect="plain">
              {{ row.statusCode }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="responseTime" :label="$t('debug.responseTime')" width="100" sortable="custom" align="right">
          <template #default="{ row }">
            <span :class="delayClass(row.responseTime)">{{ row.responseTime ?? '-' }}<span v-if="row.responseTime != null"> ms</span></span>
          </template>
        </el-table-column>
        <el-table-column prop="requestIp" :label="$t('debug.requestIp')" width="140" show-overflow-tooltip />
        <el-table-column prop="requestTime" :label="$t('debug.requestTime')" width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.requestTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="projectName" :label="$t('debug.project')" width="120" show-overflow-tooltip />
        <el-table-column prop="apiName" :label="$t('debug.apiName')" min-width="140" show-overflow-tooltip />
      </el-table>

      <!-- 分页 -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          small
          @size-change="fetchLogs"
          @current-change="fetchLogs"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { getRequestLogs, getDelayDistribution, getRequestOverview } from '@/api/requestLog'
import { Search, Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'

const { t } = useI18n()

// ========== 概览数据 ==========
const overview = reactive({
  totalRequests: '--',
  avgDelay: '--',
  errorRate: '--',
  maxDelay: '--'
})

const fetchOverview = async () => {
  try {
    const res = await getRequestOverview({ minutes: 5 })
    if (res.code === 200 && res.data) {
      overview.totalRequests = res.data.totalRequests ?? 0
      overview.avgDelay = res.data.avgDelay ?? 0
      overview.errorRate = res.data.errorRate ?? 0
      overview.maxDelay = res.data.maxDelay ?? 0
    }
  } catch (e) {
    console.error('获取概览失败:', e)
  }
}

// ========== 延迟分布 ==========
const delayMinutes = ref(60)
const delayStats = reactive({
  p50: '--', p90: '--', p95: '--', p99: '--', avgDelay: '--'
})
const delayChart = ref(null)
let delayChartInstance = null

const fetchDelayDistribution = async () => {
  try {
    const res = await getDelayDistribution({ minutes: delayMinutes.value })
    if (res.code === 200 && res.data) {
      delayStats.p50 = res.data.p50 ?? '--'
      delayStats.p90 = res.data.p90 ?? '--'
      delayStats.p95 = res.data.p95 ?? '--'
      delayStats.p99 = res.data.p99 ?? '--'
      delayStats.avgDelay = res.data.avgDelay ?? '--'
      renderDelayChart(res.data)
    }
  } catch (e) {
    console.error('获取延迟分布失败:', e)
  }
}

const renderDelayChart = (data) => {
  if (!delayChart.value) return
  if (!delayChartInstance) {
    delayChartInstance = echarts.init(delayChart.value)
  }

  const labels = data.labels || []
  const values = data.values || []

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const p = params[0]
        return `${p.axisValue}<br/>${t('debug.requestCount')}: <strong>${p.value}</strong>`
      }
    },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '8px', containLabel: true },
    xAxis: {
      type: 'category',
      data: labels,
      axisLabel: { fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      name: t('debug.requestCount'),
      minInterval: 1
    },
    series: [{
      name: t('debug.requestCount'),
      type: 'bar',
      data: values,
      barWidth: '60%',
      itemStyle: {
        borderRadius: [4, 4, 0, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#667eea' },
          { offset: 1, color: '#764ba2' }
        ])
      },
      emphasis: {
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#f093fb' },
            { offset: 1, color: '#f5576c' }
          ])
        }
      }
    }],
    title: labels.length === 0 ? {
      text: t('statistics.noData') || '暂无数据',
      left: 'center',
      top: 'center',
      textStyle: { color: '#999', fontSize: 14, fontWeight: 'normal' }
    } : undefined
  }
  delayChartInstance.setOption(option, true)
}

// ========== 请求日志列表 ==========
const logLoading = ref(false)
const logList = ref([])
const autoRefresh = ref(false)
let autoRefreshTimer = null

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const filters = reactive({
  path: '',
  method: '',
  statusCode: null
})

const fetchLogs = async () => {
  logLoading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.size
    }
    if (filters.path) params.path = filters.path
    if (filters.method) params.method = filters.method
    if (filters.statusCode) params.statusCode = filters.statusCode

    const res = await getRequestLogs(params)
    if (res.code === 200 && res.data) {
      logList.value = res.data.list || []
      pagination.total = res.data.total || 0
    }
  } catch (e) {
    console.error('获取请求日志失败:', e)
  } finally {
    logLoading.value = false
  }
}

const resetFilters = () => {
  filters.path = ''
  filters.method = ''
  filters.statusCode = null
  pagination.page = 1
  fetchLogs()
}

const handleSortChange = ({ prop, order }) => {
  // 排序在 fetchLogs 请求时由后端处理，此处预留
}

const toggleAutoRefresh = (val) => {
  if (val) {
    autoRefreshTimer = setInterval(() => {
      fetchLogs()
      fetchOverview()
    }, 5000)
  } else {
    if (autoRefreshTimer) {
      clearInterval(autoRefreshTimer)
      autoRefreshTimer = null
    }
  }
}

// ========== 工具函数 ==========
const methodTagType = (method) => {
  const map = { GET: 'success', POST: 'primary', PUT: 'warning', DELETE: 'danger', PATCH: 'info' }
  return map[method] || 'info'
}

const statusTagType = (code) => {
  if (code >= 200 && code < 300) return 'success'
  if (code >= 300 && code < 400) return 'warning'
  if (code >= 400 && code < 500) return 'danger'
  if (code >= 500) return 'danger'
  return 'info'
}

const delayClass = (delay) => {
  if (delay == null) return ''
  if (delay < 100) return 'delay-fast'
  if (delay < 500) return 'delay-normal'
  if (delay < 1000) return 'delay-slow'
  return 'delay-very-slow'
}

const formatTime = (timeStr) => {
  if (!timeStr) return '-'
  // 截取前19位 (yyyy-MM-dd HH:mm:ss)
  return timeStr.length > 19 ? timeStr.substring(0, 19) : timeStr
}

// ========== 生命周期 ==========
const handleResize = () => {
  delayChartInstance?.resize()
}

onMounted(async () => {
  await nextTick()
  fetchOverview()
  fetchDelayDistribution()
  fetchLogs()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  delayChartInstance?.dispose()
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
})
</script>

<style scoped>
.debug-panel {
  padding: 16px;
}

/* 概览卡片 */
.overview-row {
  margin-bottom: 16px;
}

.overview-card {
  text-align: center;
}

.overview-card :deep(.el-card__body) {
  padding: 16px 12px;
}

.overview-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.overview-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
}

.overview-value .unit {
  font-size: 13px;
  font-weight: 400;
  margin-left: 2px;
  color: #909399;
}

.overview-value.primary { color: #409EFF; }
.overview-value.warning { color: #E6A23C; }
.overview-value.success { color: #67C23A; }
.overview-value.danger { color: #F56C6C; }
.overview-value.info { color: #909399; }

/* 图表行 */
.chart-row {
  margin-bottom: 16px;
}

.chart-row > :deep(.el-col) {
  display: flex;
  flex-direction: column;
}

.chart-card {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chart-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chart-container {
  width: 100%;
  flex: 1;
  min-height: 280px;
}

/* 百分位统计 */
.percentile-card .percentile-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.percentile-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 20px;
}

.percentile-item {
  background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf1 100%);
  border-radius: 8px;
  padding: 16px;
  text-align: center;
}

.percentile-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 4px;
  font-weight: 500;
}

.percentile-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
}

.percentile-value .unit {
  font-size: 12px;
  font-weight: 400;
  color: #909399;
  margin-left: 2px;
}

.percentile-avg {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 8px;
  color: #fff;
}

.avg-label {
  font-size: 14px;
  opacity: 0.9;
}

.avg-value {
  font-size: 22px;
  font-weight: 700;
}

.avg-value .unit {
  font-size: 12px;
  font-weight: 400;
  opacity: 0.8;
  margin-left: 2px;
}

/* 日志表格 */
.log-table-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filter-bar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.log-table {
  width: 100%;
}

.time-text {
  font-size: 12px;
  color: #606266;
  font-family: 'Courier New', monospace;
}

.delay-fast { color: #67C23A; font-weight: 500; }
.delay-normal { color: #409EFF; }
.delay-slow { color: #E6A23C; font-weight: 500; }
.delay-very-slow { color: #F56C6C; font-weight: 600; }

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .debug-panel {
    padding: 8px;
  }

  .overview-row {
    margin-bottom: 8px;
  }

  .overview-card :deep(.el-card__body) {
    padding: 10px 8px;
  }

  .overview-value {
    font-size: 22px;
  }

  .chart-row {
    margin-bottom: 8px;
  }

  .chart-container {
    min-height: 220px;
    height: auto;
  }

  .filter-bar {
    gap: 6px;
  }

  .filter-bar .el-input,
  .filter-bar .el-select {
    flex: 1;
    min-width: 0;
  }

  .filter-bar .el-input {
    width: auto !important;
  }

  .filter-bar .el-select {
    width: auto !important;
  }

  .log-table :deep(.el-table__body-wrapper) {
    overflow-x: auto;
  }

  .percentile-value {
    font-size: 20px;
  }
}
</style>
