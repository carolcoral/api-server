<template>
  <div class="ops-monitor">
    <!-- 页面头部 - 渐变横幅 -->
    <div class="page-hero">
      <div class="hero-icon">
        <svg viewBox="0 0 48 48" width="48" height="48" fill="none">
          <defs>
            <linearGradient id="opsHeroGrad" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stop-color="#667eea"/>
              <stop offset="100%" stop-color="#764ba2"/>
            </linearGradient>
          </defs>
          <rect x="8" y="8" width="32" height="32" rx="8" stroke="url(#opsHeroGrad)" stroke-width="2.5" fill="none"/>
          <path d="M14 24h4l3-8 5 12 3-4h5" stroke="url(#opsHeroGrad)" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
          <circle cx="36" cy="14" r="3" stroke="url(#opsHeroGrad)" stroke-width="2" fill="none"/>
        </svg>
      </div>
      <div class="hero-text">
        <h2>{{ $t('ops.title') }}</h2>
        <p>{{ $t('ops.description') }}</p>
      </div>
    </div>

    <div class="content-wrapper">
      <!-- 状态概览卡片 -->
      <div class="config-card status-overview">
        <div class="card-header">
          <h3>
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" class="card-icon">
              <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.8"/>
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" stroke="currentColor" stroke-width="1.8" fill="none"/>
            </svg>
            {{ $t('ops.overallStatus') }}
          </h3>
          <el-button size="small" @click="refreshHealth" :loading="healthLoading">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>
        <div class="card-body">
          <div class="status-grid">
            <div
              v-for="item in statusCards"
              :key="item.key"
              class="status-card"
              :class="item.status"
            >
              <div class="status-indicator">
                <span class="status-dot"></span>
                <span class="status-text">{{ item.label }}</span>
              </div>
              <div class="status-value">{{ item.value }}</div>
              <div class="status-desc" v-if="item.desc">{{ item.desc }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 健康检查详情 -->
      <div class="config-card">
        <div class="card-header">
          <h3>
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" class="card-icon">
              <path d="M22 12h-4l-3 9L9 3l-3 9H2" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
            </svg>
            {{ $t('ops.healthStatus') }}
          </h3>
          <el-button size="small" @click="refreshHealth" :loading="healthLoading">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>
        <div class="card-body">
          <div v-if="healthLoading" class="loading-placeholder">
            <el-icon class="is-loading" :size="24"><Loading /></el-icon>
          </div>
          <div v-else-if="healthData" class="health-list">
            <div
              v-for="(detail, name) in healthComponents"
              :key="name"
              class="health-card"
              :class="{ up: detail.status === 'UP', warn: detail.status === 'WARN', down: detail.status === 'DOWN' || detail.status === 'OUT_OF_SERVICE' }"
            >
              <div class="health-card-header">
                <div class="health-card-title">
                  <el-icon class="health-icon" :style="{ color: getComponentMeta(name).color }">
                    <component :is="getComponentMeta(name).icon" />
                  </el-icon>
                  <span class="health-title-text">{{ getComponentMeta(name).label }}</span>
                </div>
                <el-tag
                  :type="detail.status === 'UP' ? 'success' : detail.status === 'WARN' ? 'warning' : 'danger'"
                  effect="dark"
                  size="small"
                  class="health-status-tag"
                >
                  <el-icon v-if="detail.status === 'UP'" class="status-tag-icon"><CircleCheck /></el-icon>
                  <el-icon v-else-if="detail.status === 'WARN'" class="status-tag-icon"><Warning /></el-icon>
                  <el-icon v-else class="status-tag-icon"><CircleClose /></el-icon>
                  <span>{{ detail.status }}</span>
                </el-tag>
              </div>
              <div class="health-card-body">
                <template v-if="name === 'diskSpace' && detail.details">
                  <div class="disk-progress-row">
                    <div class="disk-progress-label">
                      <span>{{ $t('ops.diskUsed') }}</span>
                      <span class="disk-progress-value">{{ getDiskUsedPercent(detail.details) }}%</span>
                    </div>
                    <el-progress
                      :percentage="getDiskUsedPercent(detail.details)"
                      :status="detail.status === 'UP' ? 'success' : 'warning'"
                      :stroke-width="10"
                      :show-text="false"
                    />
                  </div>
                </template>
                <div class="health-detail-grid" v-if="detail.details">
                  <div v-for="(v, k) in detail.details" :key="k" class="health-detail-row">
                    <span class="detail-label">{{ formatDetailLabel(name, k) }}</span>
                    <span class="detail-value" :title="formatDetailValue(name, k, v)">{{ formatDetailValue(name, k, v) }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 数据备份与恢复 -->
      <div class="config-card">
        <div class="card-header">
          <h3>
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" class="card-icon">
              <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
            </svg>
            {{ $t('ops.backupRestore') }}
          </h3>
        </div>
        <div class="card-body">
          <!-- 备份信息预览 -->
          <div v-if="backupInfo" class="backup-info">
            <div class="info-label">{{ $t('ops.dataOverview') }}</div>
            <div class="info-table">
              <div v-for="(count, table) in backupInfo.rowCounts" :key="table" class="info-row">
                <span class="table-name">{{ table }}</span>
                <span class="table-count">{{ count >= 0 ? count : 'N/A' }}</span>
              </div>
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="backup-actions">
            <el-button type="primary" @click="handleExport" :loading="exportLoading" :disabled="!canExportBackup">
              <el-icon><Download /></el-icon>
              {{ $t('ops.oneClickBackup') }}
            </el-button>

            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              :on-change="handleFileSelect"
              accept=".json"
            >
              <el-button type="warning" :disabled="!selectedFile || !canRestoreBackup">
                <el-icon><Upload /></el-icon>
                {{ $t('ops.selectRestoreFile') }}
              </el-button>
            </el-upload>

            <el-popconfirm
              :title="$t('ops.restoreConfirm')"
              @confirm="handleRestore"
            >
              <template #reference>
                <el-button type="danger" :disabled="!selectedFile || !canRestoreBackup" :loading="restoreLoading">
                  <el-icon><RefreshLeft /></el-icon>
                  {{ $t('ops.restore') }}
                </el-button>
              </template>
            </el-popconfirm>
          </div>

          <!-- 恢复结果 -->
          <div v-if="restoreResult" class="restore-result" :class="{ success: restoreResult.success, error: !restoreResult.success }">
            <div v-if="restoreResult.success">
              <p>{{ $t('ops.restoreSuccess') }}: {{ restoreResult.tablesRestored }} {{ $t('ops.tables') }}, {{ restoreResult.totalRowsRestored }} {{ $t('ops.rows') }}</p>
            </div>
            <div v-else>
              <p>{{ $t('ops.restoreFailed') }}: {{ restoreResult.errorMessage }}</p>
            </div>
            <div v-if="restoreResult.details">
              <div v-for="(d, i) in restoreResult.details" :key="i" class="detail-line">{{ d }}</div>
            </div>
          </div>

          <!-- 恢复模式选择 -->
          <div class="restore-mode" v-if="selectedFile">
            <span>{{ $t('ops.restoreMode') }}:</span>
            <el-radio-group v-model="restoreMode" size="small">
              <el-radio-button value="merge">{{ $t('ops.merge') }}</el-radio-button>
              <el-radio-button value="replace">{{ $t('ops.replace') }}</el-radio-button>
            </el-radio-group>
          </div>
        </div>
      </div>

      <!-- Prometheus 指标 -->
      <div class="config-card">
        <div class="card-header">
          <h3>
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" class="card-icon">
              <path d="M3 3v18h18" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
              <path d="M18 17V9M13 17V5M8 17v-3" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
            </svg>
            {{ $t('ops.prometheusMetrics') }}
          </h3>
          <div class="header-actions">
            <el-radio-group v-model="metricsView" size="small">
              <el-radio-button value="table">{{ $t('ops.tableView') }}</el-radio-button>
              <el-radio-button value="text">{{ $t('ops.textView') }}</el-radio-button>
            </el-radio-group>
            <el-radio-group v-show="metricsView === 'table'" v-model="metricsMode" size="small">
              <el-radio-button value="mock">{{ $t('ops.mockMetrics') }}</el-radio-button>
              <el-radio-button value="all">{{ $t('ops.allMetrics') }}</el-radio-button>
            </el-radio-group>
            <el-button size="small" @click="refreshMetrics" :loading="metricsLoading">
              <el-icon><Refresh /></el-icon>
            </el-button>
            <el-button size="small" @click="copyMetricsUrl">
              <el-icon><Link /></el-icon>
              {{ $t('ops.copyUrl') }}
            </el-button>
          </div>
        </div>
        <div class="card-body">
          <div class="metrics-info">
            <el-alert type="info" :closable="false" show-icon class="metrics-url-alert">
              <template #title>
                {{ $t('ops.prometheusUrl') }}:
                <el-tag type="info" effect="plain">{{ prometheusUrl }}</el-tag>
              </template>
            </el-alert>
            <div v-if="metricsLoading" class="loading-placeholder">
              <el-icon class="is-loading" :size="24"><Loading /></el-icon>
            </div>
            <template v-else-if="metricsPreview">
              <div v-if="metricsView === 'text'" class="metrics-preview">
                <pre>{{ metricsPreview }}</pre>
              </div>
              <div v-else class="metrics-table-wrapper">
                <div class="metrics-toolbar">
                  <el-input
                    v-model="metricsSearch"
                    :placeholder="$t('ops.searchMetrics')"
                    size="small"
                    clearable
                    :prefix-icon="Search"
                    class="metrics-search"
                  />
                  <el-tag type="info" size="small">{{ filteredMetrics.length }} {{ $t('ops.metricName') }}</el-tag>
                </div>
                <el-table :data="filteredMetrics" size="small" stripe height="360" class="metrics-table">
                  <el-table-column prop="name" :label="$t('ops.metricName')" min-width="220" show-overflow-tooltip />
                  <el-table-column prop="type" :label="$t('ops.metricType')" width="100" />
                  <el-table-column :label="$t('ops.metricValue')" min-width="200">
                    <template #default="{ row }">
                      <div v-for="(sample, idx) in row.samples.slice(0, 2)" :key="idx" class="metric-sample">
                        <el-tag size="small" type="info" v-if="sample.labels">{{ sample.labels }}</el-tag>
                        <span class="metric-value">{{ sample.value }}</span>
                      </div>
                      <div v-if="row.samples.length > 2" class="metric-more">+{{ row.samples.length - 2 }}</div>
                    </template>
                  </el-table-column>
                  <el-table-column prop="help" :label="$t('ops.metricHelp')" min-width="260" show-overflow-tooltip />
                </el-table>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Upload, Refresh, RefreshLeft, Loading, Link, Search, CircleCheck, CircleClose, Warning, Monitor, DataLine, Coin, Folder } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import systemOpsApi from '@/api/systemOps'

const { t } = useI18n()
const userStore = useUserStore()

const canExportBackup = computed(() => userStore.hasPermission('ops:backup'))
const canRestoreBackup = computed(() => userStore.hasPermission('ops:restore'))

// 健康检查
const healthLoading = ref(false)
const healthData = ref(null)

const healthComponents = computed(() => {
  if (!healthData.value || !healthData.value.components) return {}
  const filtered = {}
  for (const [key, value] of Object.entries(healthData.value.components)) {
    if (key !== 'ping') {
      filtered[key] = value
    }
  }
  return filtered
})

const iconMap = {
  DataLine,
  Coin,
  Folder,
  Monitor,
  CircleCheck
}

function getComponentMeta(name) {
  const meta = {
    database: { icon: iconMap.DataLine, label: t('ops.database'), color: '#409eff' },
    db: { icon: iconMap.Coin, label: t('ops.connectionPool'), color: '#67c23a' },
    diskSpace: { icon: iconMap.Folder, label: t('ops.diskSpace'), color: '#e6a23c' },
    mockServiceHealth: { icon: iconMap.Monitor, label: t('ops.mockService'), color: '#9254de' },
    ping: { icon: iconMap.CircleCheck, label: 'Ping', color: '#67c23a' }
  }
  return meta[name] || { icon: iconMap.CircleCheck, label: name, color: '#909399' }
}

function toBytes(value) {
  if (value === undefined || value === null || value === '') return null
  if (typeof value === 'number') return value
  if (typeof value === 'string') {
    const num = Number(value)
    if (!isNaN(num)) return num
    const match = value.trim().match(/^(\d+(?:\.\d+)?)\s*([KMGTPE]?B)$/i)
    if (match) {
      const n = parseFloat(match[1])
      const unit = match[2].toUpperCase()
      const units = { 'B': 0, 'KB': 1, 'MB': 2, 'GB': 3, 'TB': 4, 'PB': 5 }
      return n * Math.pow(1024, units[unit] || 0)
    }
  }
  return null
}

function formatBytes(value) {
  const bytes = toBytes(value)
  if (bytes === null || isNaN(bytes)) return String(value)
  const size = Number(bytes)
  if (size === 0) return '0 B'
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']
  const i = Math.floor(Math.log(size) / Math.log(1024))
  return parseFloat((size / Math.pow(1024, i)).toFixed(2)) + ' ' + sizes[i]
}

function formatGB(value) {
  const bytes = toBytes(value)
  if (bytes === null || isNaN(bytes)) return String(value)
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}

function formatDetailLabel(componentName, key) {
  const labelMap = {
    type: t('ops.dbType'),
    version: t('ops.dbVersion'),
    users: t('ops.userCount'),
    projects: t('ops.projectCount'),
    apis: t('ops.apiCount'),
    requestLogs: t('ops.requestLogCount'),
    database: t('ops.dbProduct'),
    validationQuery: t('ops.validationQuery'),
    total: t('ops.totalSpace'),
    free: t('ops.freeSpace'),
    threshold: t('ops.threshold'),
    path: t('ops.path'),
    exists: t('ops.exists'),
    totalApis: t('ops.totalApis'),
    enabledApis: t('ops.enabledApis'),
    lastCheck: t('ops.lastCheck')
  }
  return labelMap[key] || key
}

function formatDetailValue(componentName, key, value) {
  if (value === undefined || value === null) return '-'
  const byteKeys = ['total', 'free', 'threshold']
  if (byteKeys.includes(key)) return formatBytes(value)
  if (typeof value === 'boolean') return value ? t('ops.yes') : t('ops.no')
  return String(value)
}

function getDiskUsedPercent(details) {
  if (!details || !details.total || !details.free) return 0
  const total = toBytes(details.total)
  const free = toBytes(details.free)
  if (!total || total <= 0) return 0
  const percent = Math.round(((total - free) / total) * 100)
  return Math.min(100, Math.max(0, percent))
}

const statusCards = computed(() => {
  const cards = [
    { key: 'overall', label: t('ops.overallStatus'), status: 'unknown', value: '--', desc: '' }
  ]
  if (healthData.value) {
    cards[0].status = healthData.value.status === 'UP' ? 'up' : 'down'
    cards[0].value = healthData.value.status || 'UNKNOWN'
  }
  // 从健康详情中提取各组件状态
  const comps = healthComponents.value
  if (comps.database || comps.db) {
    const detail = (comps.database || comps.db).details || {}
    cards.push({
      key: 'db', label: t('ops.database'), status: (comps.database || comps.db).status === 'UP' ? 'up' : 'down',
      value: (comps.database || comps.db).status, desc: detail.type || ''
    })
  }
  if (comps.diskSpace) {
    const detail = comps.diskSpace.details || {}
    const free = formatGB(detail.free)
    const total = formatGB(detail.total)
    cards.push({
      key: 'disk', label: t('ops.diskSpace'), status: comps.diskSpace.status === 'UP' ? 'up' : 'warn',
      value: comps.diskSpace.status, desc: `${free} / ${total}`
    })
  }
  if (comps.mockServiceHealth) {
    cards.push({
      key: 'mock', label: t('ops.mockService'), status: comps.mockServiceHealth.status === 'UP' ? 'up' : 'down',
      value: comps.mockServiceHealth.status, desc: comps.mockServiceHealth.details ? `${comps.mockServiceHealth.details.totalApis} APIs` : ''
    })
  }
  return cards
})

// 备份
const backupInfo = ref(null)
const exportLoading = ref(false)
const restoreLoading = ref(false)
const selectedFile = ref(null)
const selectedFileRaw = ref(null)
const restoreResult = ref(null)
const restoreMode = ref('merge')

// Prometheus
const metricsLoading = ref(false)
const metricsPreview = ref('')
const metricsMode = ref('mock')
const metricsView = ref('table')
const metricsSearch = ref('')
const prometheusUrl = computed(() => window.location.origin + '/actuator/prometheus')

function parsePrometheusMetrics(text) {
  if (!text) return []
  const lines = text.split('\n')
  const metrics = []
  const map = new Map()

  for (const raw of lines) {
    const line = raw.trim()
    if (!line) continue

    if (line.startsWith('# HELP ')) {
      const parts = line.split(' ')
      const name = parts[2]
      const help = parts.slice(3).join(' ')
      if (!map.has(name)) {
        const m = { name, type: '', help: '', samples: [] }
        map.set(name, m)
        metrics.push(m)
      }
      map.get(name).help = help
    } else if (line.startsWith('# TYPE ')) {
      const parts = line.split(' ')
      const name = parts[2]
      const type = parts[3] || ''
      if (!map.has(name)) {
        const m = { name, type, help: '', samples: [] }
        map.set(name, m)
        metrics.push(m)
      } else {
        map.get(name).type = type
      }
    } else if (!line.startsWith('#')) {
      const match = line.match(/^([^{]+?)(\{.*?\})?\s+([^\s]+)$/)
      if (match) {
        const name = match[1].trim()
        const labels = match[2] || ''
        const value = match[3]
        if (!map.has(name)) {
          const m = { name, type: '', help: '', samples: [] }
          map.set(name, m)
          metrics.push(m)
        }
        map.get(name).samples.push({ labels, value })
      }
    }
  }

  return metrics
}

const parsedMetrics = computed(() => parsePrometheusMetrics(metricsPreview.value))

const filteredMetrics = computed(() => {
  let list = parsedMetrics.value
  if (metricsMode.value === 'mock') {
    list = list.filter(m => m.name.startsWith('mock_'))
  }
  if (metricsSearch.value) {
    const kw = metricsSearch.value.toLowerCase()
    list = list.filter(m =>
      m.name.toLowerCase().includes(kw) ||
      m.help.toLowerCase().includes(kw)
    )
  }
  return list
})

async function refreshHealth(silent = false) {
  if (!silent) healthLoading.value = true
  try {
    const res = await systemOpsApi.getHealth()
    healthData.value = res.data || res
  } catch (e) {
    if (!silent) ElMessage.error(t('ops.healthFetchFailed'))
  } finally {
    if (!silent) healthLoading.value = false
  }
}

async function loadBackupInfo() {
  try {
    const res = await systemOpsApi.getBackupInfo()
    backupInfo.value = res.data || res
  } catch (e) {
    // 忽略
  }
}

async function handleExport() {
  exportLoading.value = true
  try {
    const res = await systemOpsApi.exportBackup()
    const blob = res.data || res
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    const now = new Date()
    const ts = now.getFullYear() +
      String(now.getMonth() + 1).padStart(2, '0') +
      String(now.getDate()).padStart(2, '0') + '_' +
      String(now.getHours()).padStart(2, '0') +
      String(now.getMinutes()).padStart(2, '0') +
      String(now.getSeconds()).padStart(2, '0')
    link.download = `api-server-backup-${ts}.json`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    ElMessage.success(t('ops.backupSuccess'))
  } catch (e) {
    ElMessage.error(t('ops.backupFailed'))
  } finally {
    exportLoading.value = false
  }
}

function handleFileSelect(file) {
  selectedFile.value = file.name
  selectedFileRaw.value = file.raw
}

async function handleRestore() {
  if (!selectedFileRaw.value) return
  restoreLoading.value = true
  restoreResult.value = null
  try {
    const formData = new FormData()
    formData.append('file', selectedFileRaw.value)
    formData.append('mode', restoreMode.value)
    const res = await systemOpsApi.restoreBackup(formData)
    restoreResult.value = res.data || res
    if (restoreResult.value.success) {
      ElMessage.success(t('ops.restoreSuccess'))
      loadBackupInfo()
    } else {
      ElMessage.error(t('ops.restoreFailed'))
    }
  } catch (e) {
    ElMessage.error(t('ops.restoreFailed'))
  } finally {
    restoreLoading.value = false
  }
}

async function refreshMetrics() {
  metricsLoading.value = true
  try {
    const res = await systemOpsApi.getPrometheusMetrics()
    metricsPreview.value = typeof res === 'string' ? res : (res.data || '')
  } catch (e) {
    metricsPreview.value = ''
  } finally {
    metricsLoading.value = false
  }
}

function copyMetricsUrl() {
  navigator.clipboard.writeText(prometheusUrl.value).then(() => {
    ElMessage.success(t('ops.urlCopied'))
  })
}

let healthInterval = null

onMounted(() => {
  refreshHealth()
  loadBackupInfo()
  refreshMetrics()

  healthInterval = setInterval(() => {
    refreshHealth(true)
  }, 5000)
})

onUnmounted(() => {
  if (healthInterval) {
    clearInterval(healthInterval)
    healthInterval = null
  }
})
</script>

<style scoped>
.ops-monitor {
  padding: 0;
  max-width: 1200px;
  margin: 0 auto;
}

/* ========== 页面头部横幅 ========== */
.page-hero {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 28px 32px;
  margin-bottom: 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 14px;
  box-shadow: 0 4px 24px rgba(102, 126, 234, 0.25);
  position: relative;
  overflow: hidden;
}

.page-hero::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -10%;
  width: 200px;
  height: 200px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.06);
  pointer-events: none;
}

.page-hero::after {
  content: '';
  position: absolute;
  bottom: -30%;
  left: 60%;
  width: 140px;
  height: 140px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.04);
  pointer-events: none;
}

.hero-icon {
  flex-shrink: 0;
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 14px;
  backdrop-filter: blur(4px);
  z-index: 1;
}

.hero-text {
  z-index: 1;
}

.hero-text h2 {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.5px;
}

.hero-text p {
  margin: 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.78);
}

/* ========== 内容区 ========== */
.content-wrapper {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ========== 配置卡片 ========== */
.config-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #ebeef5;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.card-header {
  padding: 16px 24px;
  background: #fafbfc;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-icon {
  color: #667eea;
  flex-shrink: 0;
}

.card-body {
  padding: 16px 24px;
}

/* ========== 状态概览 ========== */
.status-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.status-card {
  text-align: center;
  padding: 16px;
  border-radius: 10px;
  background: #f5f7fa;
  transition: all 0.25s ease;
}

.status-card:hover {
  background: #eef1f6;
  transform: translateY(-1px);
}

.status-card.up {
  background: #f0f9eb;
}

.status-card.down {
  background: #fef0f0;
}

.status-card.warn {
  background: #fdf6ec;
}

.status-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-bottom: 8px;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}

.status-card.up .status-dot {
  background: #67c23a;
  box-shadow: 0 0 6px rgba(103, 194, 58, 0.5);
}

.status-card.down .status-dot {
  background: #f56c6c;
  box-shadow: 0 0 6px rgba(245, 108, 108, 0.5);
}

.status-card.warn .status-dot {
  background: #e6a23c;
  box-shadow: 0 0 6px rgba(230, 162, 60, 0.5);
}

.status-card.unknown .status-dot {
  background: #909399;
}

.status-text {
  font-size: 14px;
  color: #606266;
}

.status-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}

.status-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.loading-placeholder {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 120px;
  color: #909399;
}

.health-list {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

@media (max-width: 1200px) {
  .health-list {
    grid-template-columns: repeat(3, 1fr);
  }
  .status-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 992px) {
  .health-list {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .health-list,
  .status-grid {
    grid-template-columns: 1fr;
  }

  .status-value {
    font-size: 22px;
  }

  .backup-actions {
    flex-direction: column;
  }

  .metrics-preview {
    max-height: 200px;
    font-size: 11px;
  }

  .card-header {
    flex-wrap: wrap;
    gap: 8px;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-end;
  }
}

.health-card {
  background: #ffffff;
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  padding: 14px 16px;
  transition: all 0.25s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.health-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}

.health-card.up {
  border-left: 4px solid #67c23a;
}

.health-card.warn {
  border-left: 4px solid #e6a23c;
}

.health-card.down {
  border-left: 4px solid #f56c6c;
}

.health-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  gap: 8px;
}

.health-card-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.health-icon {
  font-size: 22px;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
}

.health-title-text {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.health-status-tag {
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
}

.health-status-tag .status-tag-icon {
  margin-right: 4px;
  font-size: 12px;
  display: inline-flex;
  align-items: center;
}

.health-card-body {
  padding-left: 32px;
}

.health-detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 8px 16px;
}

.health-detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 10px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 13px;
}

.detail-label {
  color: #606266;
  font-weight: 500;
  margin-right: 12px;
  flex-shrink: 0;
}

.detail-value {
  color: #303133;
  font-weight: 600;
  font-family: 'Menlo', 'Consolas', monospace;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 220px;
}

.disk-progress-row {
  margin-bottom: 12px;
  padding: 8px 10px;
  background: #f5f7fa;
  border-radius: 6px;
}

.disk-progress-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
  color: #606266;
}

.disk-progress-value {
  font-weight: 600;
  color: #303133;
  font-family: 'Menlo', 'Consolas', monospace;
}

.backup-info {
  margin-bottom: 16px;
}

.info-label {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
  color: #606266;
}

.info-table {
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  padding: 6px 12px;
  font-size: 13px;
  border-bottom: 1px solid #f5f7fa;
}

.info-row:last-child {
  border-bottom: none;
}

.table-name {
  color: #606266;
  font-family: monospace;
}

.table-count {
  color: #409eff;
  font-weight: 600;
}

.backup-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.restore-mode {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #606266;
}

.restore-result {
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 6px;
  font-size: 13px;
}

.restore-result.success {
  background: #f0f9eb;
  border: 1px solid #e1f3d8;
  color: #67c23a;
}

.restore-result.error {
  background: #fef0f0;
  border: 1px solid #fde2e2;
  color: #f56c6c;
}

.detail-line {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.metrics-info {
  margin-top: 8px;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.metrics-preview {
  margin-top: 12px;
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 8px;
  padding: 14px;
  max-height: 300px;
  overflow: auto;
  font-family: 'Menlo', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.6;
}

.metrics-preview pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.header-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

.metrics-url-alert {
  margin-bottom: 12px;
  border-radius: 8px;
}

.metrics-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.metrics-search {
  width: 240px;
}

.metrics-table-wrapper {
  margin-top: 4px;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.metrics-table {
  border-radius: 8px;
  overflow: hidden;
  flex: 1;
}

.metric-sample {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  font-size: 12px;
}

.metric-value {
  font-family: 'Menlo', 'Consolas', monospace;
  color: #409eff;
  font-weight: 600;
}

.metric-more {
  font-size: 12px;
  color: #909399;
  padding-left: 4px;
}
</style>
