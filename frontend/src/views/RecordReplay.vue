<!--
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
-->

<template>
  <div class="record-replay">
    <!-- 页面头部 - 渐变横幅 -->
    <div class="page-hero">
      <div class="hero-icon">
        <svg viewBox="0 0 48 48" width="48" height="48" fill="none">
          <defs>
            <linearGradient id="heroGradRR" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stop-color="#667eea"/>
              <stop offset="100%" stop-color="#764ba2"/>
            </linearGradient>
          </defs>
          <rect x="8" y="4" width="32" height="40" rx="6" stroke="url(#heroGradRR)" stroke-width="2.5" fill="none"/>
          <circle cx="24" cy="24" r="6" stroke="url(#heroGradRR)" stroke-width="2" fill="none"/>
          <polygon points="21,21 30,24 21,27" fill="url(#heroGradRR)"/>
          <line x1="24" y1="18" x2="24" y2="12" stroke="url(#heroGradRR)" stroke-width="2" stroke-linecap="round"/>
          <line x1="24" y1="36" x2="24" y2="30" stroke="url(#heroGradRR)" stroke-width="2" stroke-linecap="round"/>
          <line x1="18" y1="24" x2="12" y2="24" stroke="url(#heroGradRR)" stroke-width="2" stroke-linecap="round"/>
          <line x1="36" y1="24" x2="30" y2="24" stroke="url(#heroGradRR)" stroke-width="2" stroke-linecap="round"/>
        </svg>
      </div>
      <div class="hero-text">
        <h2>{{ $t('recordReplay.title') }}</h2>
        <p>{{ $t('recordReplay.description') }}</p>
      </div>
    </div>

    <!-- ========== 1. HTTP 请求发起区域 ========== -->
    <div class="content-wrapper">
      <el-card class="request-card" shadow="never">
        <template #header>
          <div class="card-header">
            <h3>
              <el-icon><Promotion /></el-icon>
              {{ $t('recordReplay.sendRequest') }}
            </h3>
          </div>
        </template>

      <el-form :model="requestForm" label-width="80px" label-position="left" size="default">
        <!-- 请求URL和方法 -->
        <el-row :gutter="20" align="middle">
          <el-col :xs="24" :sm="6" :md="5" :lg="4">
            <el-form-item :label="$t('recordReplay.method')" prop="method" class="compact-form-item">
              <el-select v-model="requestForm.method" style="width: 100%; min-width: 100px">
                <el-option v-for="m in methods" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="18" :md="19" :lg="20">
            <el-form-item :label="$t('recordReplay.url')" prop="url" class="compact-form-item">
              <el-input v-model="requestForm.url" :placeholder="$t('recordReplay.urlPlaceholder')" clearable>
                <template #append>
                  <el-button :icon="Promotion" :loading="sending" @click="handleSendRequest" type="primary">
                    {{ $t('recordReplay.send') }}
                  </el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 请求头（可折叠） -->
        <el-collapse v-model="activeCollapse" class="request-collapse">
          <el-collapse-item :title="$t('recordReplay.requestHeaders') + ' (' + requestHeaders.length + ')'" name="headers">
            <div class="kv-editor">
              <div v-for="(item, index) in requestHeaders" :key="index" class="kv-row">
                <el-input v-model="item.key" :placeholder="$t('recordReplay.headerKey')" size="small" />
                <el-input v-model="item.value" :placeholder="$t('recordReplay.headerValue')" size="small" />
                <el-button type="danger" :icon="Delete" circle size="small" @click="removeHeader(index)" />
              </div>
              <el-button type="primary" link size="small" @click="addHeader">
                <el-icon><Plus /></el-icon>
                {{ $t('recordReplay.addHeader') }}
              </el-button>
            </div>
          </el-collapse-item>

          <!-- 查询参数（可折叠） -->
          <el-collapse-item :title="$t('recordReplay.queryParams') + ' (' + requestParams.length + ')'" name="params">
            <div class="kv-editor">
              <div v-for="(item, index) in requestParams" :key="index" class="kv-row">
                <el-input v-model="item.key" :placeholder="$t('recordReplay.paramKey')" size="small" />
                <el-input v-model="item.value" :placeholder="$t('recordReplay.paramValue')" size="small" />
                <el-button type="danger" :icon="Delete" circle size="small" @click="removeParam(index)" />
              </div>
              <el-button type="primary" link size="small" @click="addParam">
                <el-icon><Plus /></el-icon>
                {{ $t('recordReplay.addParam') }}
              </el-button>
            </div>
          </el-collapse-item>

          <!-- 请求体（可折叠） -->
          <el-collapse-item :title="$t('recordReplay.requestBody')" name="body" v-if="showRequestBody">
            <div class="body-toolbar">
              <span class="body-toolbar-label">{{ $t('recordReplay.contentType') }}</span>
              <el-select v-model="requestForm.contentType" size="small" style="width: 240px">
                <el-option label="application/json" value="application/json" />
                <el-option label="application/x-www-form-urlencoded" value="application/x-www-form-urlencoded" />
                <el-option label="text/plain" value="text/plain" />
                <el-option label="text/xml" value="text/xml" />
              </el-select>
            </div>
            <el-input
              v-model="requestForm.body"
              type="textarea"
              :rows="8"
              :placeholder="$t('recordReplay.bodyPlaceholder')"
              class="code-textarea"
            />
          </el-collapse-item>
        </el-collapse>
      </el-form>

      <!-- 响应结果展示 -->
      <div v-if="responseResult" class="response-section">
        <el-divider />
        <div class="response-header">
          <h4>{{ $t('recordReplay.responseResult') }}</h4>
          <div class="response-meta">
            <el-tag :type="statusTagType(responseResult.statusCode)" size="default" effect="dark">
              {{ responseResult.statusCode }}
            </el-tag>
            <span class="response-time">{{ responseResult.responseTime }}ms</span>
          </div>
        </div>
        <div class="response-subtitle">{{ $t('recordReplay.responseHeaders') }}</div>
        <el-input
          type="textarea"
          :rows="4"
          :model-value="formatJson(responseResult.responseHeaders)"
          readonly
          class="code-textarea"
          style="margin-bottom: 12px"
        />
        <div class="response-subtitle">{{ $t('recordReplay.responseBody') }}</div>
        <el-input
          type="textarea"
          :rows="12"
          :model-value="formatJson(responseResult.responseBody)"
          readonly
          class="code-textarea"
        />
      </div>
      </el-card>

    <!-- ========== 2. 录制列表区域 ========== -->
      <el-card class="table-card" shadow="never">
        <template #header>
          <div class="card-header">
            <h3>
              <el-icon><Document /></el-icon>
              {{ $t('recordReplay.recordList') }}
            </h3>
            <span class="total-hint">{{ $t('recordReplay.totalRecords', { count: total }) }}</span>
          </div>
        </template>

      <!-- 筛选栏 -->
      <el-form :inline="true" :model="filterForm" size="default" class="filter-form">
        <el-form-item :label="$t('recordReplay.path')">
          <el-input v-model="filterForm.path" :placeholder="$t('recordReplay.pathPlaceholder')" clearable style="width: 220px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item :label="$t('recordReplay.method')">
          <el-select v-model="filterForm.method" :placeholder="$t('recordReplay.selectMethod')" clearable style="width: 120px">
            <el-option v-for="m in methods" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">{{ $t('common.search') }}</el-button>
          <el-button :icon="RefreshRight" @click="handleReset">{{ $t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table
        v-loading="loading"
        :data="recordList"
        border
        stripe
        style="width: 100%"
        :header-cell-style="{ background: '#f5f7fa' }"
        max-height="500"
      >
        <el-table-column prop="method" :label="$t('recordReplay.method')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="methodTagType(row.method)" size="small" effect="dark">{{ row.method }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" :label="$t('recordReplay.path')" min-width="280" show-overflow-tooltip />
        <el-table-column prop="statusCode" :label="$t('recordReplay.statusCode')" width="100" align="center">
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
        <el-table-column prop="requestTime" :label="$t('recordReplay.requestTime')" width="180" show-overflow-tooltip />
        <el-table-column :label="$t('permission.role.actions')" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleViewDetail(row)">
              {{ $t('recordReplay.viewDetail') }}
            </el-button>
            <el-button type="success" link :icon="RefreshRight" @click="handleOpenReplay(row)">
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
    </div>

    <!-- ========== 详情对话框 ========== -->
    <el-dialog v-model="detailVisible" :title="$t('recordReplay.recordDetail')" width="820px" top="5vh" class="detail-dialog" destroy-on-close>
      <template v-if="detailData">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item :label="$t('recordReplay.method')">
            <el-tag :type="methodTagType(detailData.method)" size="small" effect="dark">{{ detailData.method }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('recordReplay.statusCode')">
            <el-tag :type="statusTagType(detailData.statusCode)" size="small">{{ detailData.statusCode }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('recordReplay.path')" :span="2">{{ detailData.path }}</el-descriptions-item>
          <el-descriptions-item :label="$t('recordReplay.requestTime')">{{ detailData.requestTime }}</el-descriptions-item>
          <el-descriptions-item :label="$t('recordReplay.responseTime')">{{ detailData.responseTime }}ms</el-descriptions-item>
          <el-descriptions-item :label="'IP'">{{ detailData.requestIp || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="'Content-Type'">{{ detailData.responseContentType || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs type="border-card" class="detail-tabs" v-model="detailTab">
          <el-tab-pane label="Request Headers" name="reqHeaders">
            <el-input type="textarea" :rows="8" :model-value="formatJson(detailData.requestHeaders)" readonly class="code-textarea" />
          </el-tab-pane>
          <el-tab-pane label="Query Params" name="queryParams" v-if="detailData.queryParams">
            <el-input type="textarea" :rows="8" :model-value="formatJson(detailData.queryParams)" readonly class="code-textarea" />
          </el-tab-pane>
          <el-tab-pane label="Request Body" name="reqBody">
            <el-input type="textarea" :rows="8" :model-value="formatJson(detailData.requestBody)" readonly class="code-textarea" />
          </el-tab-pane>
          <el-tab-pane label="Response Body" name="resBody">
            <el-input type="textarea" :rows="12" :model-value="formatJson(detailData.responseBody)" readonly class="code-textarea" />
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-dialog>

    <!-- ========== 回放保存对话框 ========== -->
    <el-dialog v-model="replayVisible" :title="$t('recordReplay.replaySave')" width="650px" top="8vh" @close="resetReplayForm" class="replay-dialog" destroy-on-close>
      <el-form ref="replayFormRef" :model="replayForm" :rules="replayRules" label-width="120px" size="default">
        <el-form-item :label="$t('recordReplay.selectTargetProject')" prop="targetProjectId">
          <el-select v-model="replayForm.targetProjectId" :placeholder="$t('recordReplay.selectProject')" style="width: 100%" filterable>
            <el-option v-for="p in accessibleProjects" :key="p.id" :label="p.name + ' (' + p.code + ')'" :value="p.id" />
          </el-select>
        </el-form-item>

        <el-form-item :label="$t('recordReplay.apiPath')" prop="apiPath">
          <el-input v-model="replayForm.apiPath" :placeholder="$t('recordReplay.apiPathPlaceholder')">
          <template #append>
            <el-button :icon="RefreshRight" @click="autoExtractPath">{{ $t('recordReplay.autoExtract') }}</el-button>
          </template>
          </el-input>
          <div class="form-tip">{{ $t('recordReplay.apiPathTip') }}</div>
        </el-form-item>

        <el-form-item :label="$t('recordReplay.apiName')" prop="apiName">
          <el-input v-model="replayForm.apiName" :placeholder="$t('recordReplay.apiNamePlaceholder')" />
        </el-form-item>

        <el-form-item :label="$t('recordReplay.description')" prop="description">
          <el-input
            v-model="replayForm.description"
            type="textarea"
            :rows="3"
            :placeholder="$t('recordReplay.descriptionPlaceholder')"
          />
          <el-button type="primary" link size="small" :loading="generatingDesc" @click="handleGenerateDesc" style="margin-top: 6px">
            <el-icon><MagicStick /></el-icon>
            {{ $t('recordReplay.aiGenerateDesc') }}
          </el-button>
        </el-form-item>

        <!-- 预览录制的请求信息 -->
        <el-divider />
        <div class="preview-title">录制请求预览</div>
        <el-descriptions :column="2" border size="small" v-if="replayingRecord">
          <el-descriptions-item :label="$t('recordReplay.method')" :span="1">
            <el-tag :type="methodTagType(replayingRecord.method)" size="small" effect="dark">{{ replayingRecord.method }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('recordReplay.statusCode')" :span="1">
            <el-tag :type="statusTagType(replayingRecord.statusCode)" size="small">{{ replayingRecord.statusCode }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('recordReplay.path')" :span="2">{{ replayingRecord.path }}</el-descriptions-item>
        </el-descriptions>
      </el-form>

      <template #footer>
        <el-button @click="replayVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="replaying" :icon="Check" @click="handleConfirmReplay">
          {{ $t('recordReplay.confirmReplay') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Promotion, Delete, Plus, MagicStick, Search, RefreshRight, View, Check, Document } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { generateApiDescriptionStream } from '@/api/ai'

const { t } = useI18n()

const methods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH']

// ========== 请求发起 ==========
const activeCollapse = ref(['headers'])
const sending = ref(false)
const responseResult = ref(null)

const requestForm = reactive({
  url: '',
  method: 'GET',
  body: '',
  contentType: 'application/json'
})

const requestHeaders = ref([{ key: '', value: '' }])
const requestParams = ref([])

const showRequestBody = computed(() => {
  return ['POST', 'PUT', 'PATCH'].includes(requestForm.method)
})

// 监听 URL 变化，自动解析查询参数
watch(() => requestForm.url, (newUrl) => {
  if (!newUrl || !newUrl.trim()) {
    requestParams.value = []
    return
  }
  const url = newUrl.trim()
  const qIdx = url.indexOf('?')
  if (qIdx < 0 || qIdx === url.length - 1) return
  const queryString = url.substring(qIdx + 1)
  const params = []
  const searchParams = new URLSearchParams(queryString)
  for (const [key, value] of searchParams.entries()) {
    params.push({ key, value })
  }
  if (params.length > 0) {
    requestParams.value = params
  }
})

const autoExtractPath = () => {
  if (!replayingRecord.value) return
  replayForm.apiPath = extractPathFromUrl(replayingRecord.value.path)
}

const addHeader = () => requestHeaders.value.push({ key: '', value: '' })
const removeHeader = (index) => {
  if (requestHeaders.value.length > 1) requestHeaders.value.splice(index, 1)
}
const addParam = () => requestParams.value.push({ key: '', value: '' })
const removeParam = (index) => requestParams.value.splice(index, 1)

const buildHeadersMap = () => {
  const map = {}
  requestHeaders.value.forEach(h => {
    if (h.key && h.key.trim()) map[h.key.trim()] = h.value || ''
  })
  return map
}

const buildParamsMap = () => {
  const map = {}
  requestParams.value.forEach(p => {
    if (p.key && p.key.trim()) map[p.key.trim()] = p.value || ''
  })
  return map
}

const handleSendRequest = async () => {
  if (!requestForm.url || !requestForm.url.trim()) {
    ElMessage.warning(t('recordReplay.urlRequired'))
    return
  }
  sending.value = true
  responseResult.value = null
  try {
    // 拆分 URL 的路径和查询参数，查询参数合并到 queryParams
    const rawUrl = requestForm.url.trim()
    let baseUrl = rawUrl
    let urlQueryParams = {}
    const qIdx = rawUrl.indexOf('?')
    if (qIdx >= 0) {
      baseUrl = rawUrl.substring(0, qIdx)
      const queryString = rawUrl.substring(qIdx + 1)
      new URLSearchParams(queryString).forEach((value, key) => {
        urlQueryParams[key] = value
      })
    }
    // 合并：URL 中的参数优先（用户可能手动修改了 params 面板）
    const mergedParams = { ...buildParamsMap(), ...urlQueryParams }
    const payload = {
      url: baseUrl,
      method: requestForm.method,
      headers: buildHeadersMap(),
      queryParams: mergedParams,
      body: requestForm.body || '',
      contentType: requestForm.contentType
    }
    const resp = await request.post('/http-proxy/send', payload)
    if (resp.code === 200) {
      responseResult.value = resp.data
      ElMessage.success(t('recordReplay.sendSuccess'))
      // 刷新录制列表
      currentPage.value = 1
      fetchRecords()
    } else {
      ElMessage.error(resp.message || t('recordReplay.sendFailed'))
    }
  } catch (error) {
    console.error('发送请求失败:', error)
    ElMessage.error(t('recordReplay.sendFailed'))
  } finally {
    sending.value = false
  }
}

// ========== 录制列表 ==========
const loading = ref(false)
const recordList = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

const filterForm = reactive({
  path: '',
  method: ''
})

const fetchRecords = async () => {
  loading.value = true
  try {
    const params = { page: currentPage.value, size: pageSize.value }
    if (filterForm.path) params.path = filterForm.path
    if (filterForm.method) params.method = filterForm.method
    const resp = await request.get('/request-records/list', { params })
    if (resp.code === 200) {
      recordList.value = resp.data?.list || []
      total.value = resp.data?.total || 0
    }
  } catch (error) {
    console.error('获取录制记录失败:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchRecords()
}

const handleReset = () => {
  filterForm.path = ''
  filterForm.method = ''
  currentPage.value = 1
  fetchRecords()
}

// ========== 详情 ==========
const detailVisible = ref(false)
const detailData = ref(null)
const detailTab = ref('resBody')

const handleViewDetail = async (row) => {
  try {
    const resp = await request.get(`/request-records/${row.id}`)
    if (resp.code === 200) {
      detailData.value = resp.data
      detailVisible.value = true
    } else {
      ElMessage.error(resp.message || t('recordReplay.fetchDetailFailed'))
    }
  } catch (error) {
    console.error('获取详情失败:', error)
    ElMessage.error(t('recordReplay.fetchDetailFailed'))
  }
}

// ========== 回放保存 ==========
const replayVisible = ref(false)
const replaying = ref(false)
const replayingRecord = ref(null)
const replayFormRef = ref(null)
const accessibleProjects = ref([])

const replayForm = reactive({
  targetProjectId: null,
  apiPath: '',
  apiName: '',
  description: ''
})

const replayRules = computed(() => ({
  targetProjectId: [{ required: true, message: t('recordReplay.selectProject'), trigger: 'change' }],
  apiPath: [{ required: true, message: t('recordReplay.apiPathRequired'), trigger: 'blur' }],
  apiName: [{ required: true, message: t('recordReplay.apiNameRequired'), trigger: 'blur' }]
}))

// 从完整URL提取路径
const extractPathFromUrl = (url) => {
  if (!url) return '/'
  try {
    // 处理已经是纯路径的情况
    if (url.startsWith('/')) {
      // 分离路径和查询参数
      const qIdx = url.indexOf('?')
      return qIdx >= 0 ? url.substring(0, qIdx) : url
    }
    // 处理完整URL
    const withoutProtocol = url.replace(/^https?:\/\//, '')
    const slashIdx = withoutProtocol.indexOf('/')
    if (slashIdx >= 0) {
      const pathAndQuery = withoutProtocol.substring(slashIdx)
      const qIdx = pathAndQuery.indexOf('?')
      return qIdx >= 0 ? pathAndQuery.substring(0, qIdx) : pathAndQuery
    }
    return '/'
  } catch {
    return url.startsWith('/') ? url : '/' + url
  }
}

const handleOpenReplay = (row) => {
  replayingRecord.value = row
  replayForm.targetProjectId = null
  replayForm.apiPath = extractPathFromUrl(row.path)
  replayForm.apiName = extractPathFromUrl(row.path)
  replayForm.description = ''
  replayVisible.value = true
}

const resetReplayForm = () => {
  replayFormRef.value?.resetFields()
  replayingRecord.value = null
}

const fetchAccessibleProjects = async () => {
  try {
    const resp = await request.get('/projects/accessible/all')
    if (resp.code === 200) {
      accessibleProjects.value = resp.data || []
    }
  } catch (error) {
    console.error('获取项目列表失败:', error)
  }
}

// AI 生成描述
const generatingDesc = ref(false)
const handleGenerateDesc = async () => {
  if (!replayingRecord.value) return
  generatingDesc.value = true
  try {
    const desc = await generateApiDescriptionStream({
      apiMethod: replayingRecord.value.method,
      apiPath: replayForm.apiPath,
      apiName: replayForm.apiName || replayForm.apiPath
    })
    if (desc) {
      replayForm.description = desc
    }
  } catch (error) {
    console.error('AI生成描述失败:', error)
    ElMessage.warning(t('recordReplay.aiGenerateFailed'))
  } finally {
    generatingDesc.value = false
  }
}

const handleConfirmReplay = async () => {
  if (!replayFormRef.value) return
  try {
    await replayFormRef.value.validate()
  } catch {
    return
  }
  if (!replayingRecord.value) return

  replaying.value = true
  try {
    const payload = {
      targetProjectId: replayForm.targetProjectId,
      apiPath: replayForm.apiPath,
      apiName: replayForm.apiName,
      description: replayForm.description
    }
    const resp = await request.post(`/request-records/${replayingRecord.value.id}/replay`, payload)
    if (resp.code === 200) {
      const action = resp.data?.action === 'create' ? t('recordReplay.created') : t('recordReplay.appended')
      ElMessage.success(t('recordReplay.replaySuccess', { action }))
      replayVisible.value = false
    } else {
      ElMessage.error(resp.message || t('recordReplay.replayFailed'))
    }
  } catch (error) {
    console.error('回放失败:', error)
    ElMessage.error(t('recordReplay.replayFailed'))
  } finally {
    replaying.value = false
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
  if (code >= 400) return 'danger'
  return 'info'
}

const formatJson = (data) => {
  if (!data) return ''
  if (typeof data === 'string') {
    try { return JSON.stringify(JSON.parse(data), null, 2) } catch { return data }
  }
  try { return JSON.stringify(data, null, 2) } catch { return String(data) }
}

// ========== 生命周期 ==========
onMounted(() => {
  fetchRecords()
  fetchAccessibleProjects()
})
</script>

<style scoped>
.record-replay {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

/* ========== 页面头部横幅 ========== */
.page-hero {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 28px 32px;
  margin-bottom: 20px;
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
  gap: 20px;
}

.request-card,
.table-card {
  /* 继承 el-card 默认样式 */
}

.request-card :deep(.el-card__header),
.table-card :deep(.el-card__header) {
  padding: 16px 24px;
  background: #fafbfc;
  border-bottom: 1px solid #f0f0f0;
}

.card-header {
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
  flex: 1;
}

.card-header h3 .el-icon {
  color: #667eea;
  font-size: 18px;
}

.total-hint {
  font-size: 13px;
  color: #909399;
  font-weight: 400;
}

.request-card :deep(.el-card__body),
.table-card :deep(.el-card__body) {
  padding: 20px 24px;
}

.compact-form-item {
  margin-bottom: 0;
}

.compact-form-item :deep(.el-form-item__label) {
  line-height: 32px;
  padding-right: 12px;
  white-space: nowrap;
  text-align: left;
}

.compact-form-item :deep(.el-form-item__content) {
  line-height: 32px;
  min-height: 32px;
}

.request-collapse {
  margin: 16px 0;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
}
.request-collapse :deep(.el-collapse-item__header) {
  font-size: 13px;
  color: #606266;
  background: #fbfbfc;
  padding: 0 16px;
  height: 42px;
  font-weight: 500;
}
.request-collapse :deep(.el-collapse-item__wrap) {
  border: none;
}
.request-collapse :deep(.el-collapse-item__content) {
  padding: 14px 16px;
  background: #fff;
}

.kv-editor {
  padding: 4px 0;
}
.kv-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.kv-row .el-input {
  flex: 1;
}
.kv-row .el-input:first-child {
  max-width: 240px;
}
.kv-row .el-button {
  flex-shrink: 0;
}

.body-toolbar {
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.body-toolbar-label {
  font-size: 13px;
  color: #606266;
  font-weight: 500;
}

.send-bar {
  display: none;
}

.code-textarea :deep(textarea) {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 13px;
  line-height: 1.6;
  background: #fafafa;
}

.response-section {
  margin-top: 20px;
  animation: fadeIn 0.3s ease;
}
.response-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.response-header h4 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.response-subtitle {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin: 0 0 8px 0;
}
.response-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}
.response-time {
  font-size: 13px;
  color: #606266;
  font-weight: 500;
}

.filter-form {
  margin-bottom: 16px;
}

.filter-form :deep(.el-form-item) {
  margin-right: 18px;
  margin-bottom: 0;
}

.filter-form :deep(.el-form-item__label) {
  padding-right: 8px;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
}

.pagination-wrap {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}

.detail-tabs {
  margin-top: 18px;
}

.preview-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

/* 分割线 */
.request-card :deep(.el-divider--horizontal),
.table-card :deep(.el-divider--horizontal) {
  margin: 16px 0 20px;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 768px) {
  .record-replay {
    padding: 0 10px;
  }
  .page-hero {
    border-radius: 0;
    padding: 20px;
  }
  .request-card :deep(.el-card__body),
  .table-card :deep(.el-card__body) {
    padding: 14px 16px;
  }
  .compact-form-item {
    margin-bottom: 14px;
  }
  .kv-row {
    flex-wrap: wrap;
  }
  .kv-row .el-input:first-child {
    max-width: none;
  }
  .kv-row .el-button {
    margin-left: auto;
  }
}
</style>
