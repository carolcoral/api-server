<!--
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
-->

<template>
  <div class="provider-settings">
    <!-- 页面头部 - 渐变横幅 -->
    <div class="page-hero">
      <div class="hero-icon">
        <svg viewBox="0 0 48 48" width="48" height="48" fill="none">
          <defs>
            <linearGradient id="heroGrad" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stop-color="#667eea"/>
              <stop offset="100%" stop-color="#764ba2"/>
            </linearGradient>
          </defs>
          <rect x="6" y="6" width="36" height="36" rx="8" stroke="url(#heroGrad)" stroke-width="2.5" fill="none"/>
          <circle cx="24" cy="24" r="5" stroke="url(#heroGrad)" stroke-width="2" fill="none"/>
          <path d="M24 19v-5M24 34v-5M19 24h-5M34 24h-5M16.5 16.5l-3.5-3.5M31.5 16.5l3.5-3.5M16.5 31.5l-3.5 3.5M31.5 31.5l3.5 3.5" stroke="url(#heroGrad)" stroke-width="2" stroke-linecap="round"/>
        </svg>
      </div>
      <div class="hero-text">
        <h2>{{ $t('ai.title') }}</h2>
        <p>{{ $t('ai.description') }}</p>
      </div>
    </div>

    <div class="content-wrapper" v-loading="loading">
      <!-- 已启用的 AI 设置列表 -->
      <div class="config-card">
        <div class="card-header">
          <h3>
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" class="card-icon">
              <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.8"/>
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" stroke="currentColor" stroke-width="1.8" fill="none"/>
            </svg>
            {{ $t('ai.aiSettings') }}
          </h3>
          <el-button type="primary" size="small" @click="showAddDialog" v-if="canCreate">
            <el-icon style="margin-right: 4px"><Plus /></el-icon>
            {{ $t('ai.addConfig') }}
          </el-button>
        </div>
        <div class="card-body">
          <!-- 无配置时 -->
          <div v-if="savedConfigs.length === 0" class="empty-state">
            <p>{{ $t('ai.noProvider') }}</p>
          </div>

          <!-- 配置列表 -->
          <div v-else class="config-list">
            <div
              v-for="config in savedConfigs"
              :key="config.id"
              class="config-item"
              :class="{ 'is-enabled': config.enabled, 'is-default': config.isDefault }"
            >
              <div class="config-item-header">
                <div class="config-item-title">
                  <span class="config-provider-name">{{ config.providerName }}</span>
                  <el-tag v-if="config.enabled" size="small" type="success" effect="plain" round>
                    {{ $t('ai.enabled') }}
                  </el-tag>
                  <el-tag v-else size="small" type="info" effect="plain" round>
                    {{ $t('ai.disabled') }}
                  </el-tag>
                  <el-tag v-if="config.isDefault" size="small" type="warning" effect="plain" round>
                    {{ $t('ai.defaultConfig') }}
                  </el-tag>
                </div>
                <div class="config-item-actions">
                  <el-button
                    v-if="config.enabled && !config.isDefault && canSetDefault"
                    size="small"
                    type="warning"
                    plain
                    @click="setDefault(config.id)"
                    :loading="settingDefault === config.id"
                  >
                    {{ $t('ai.setDefault') }}
                  </el-button>
                  <el-button
                    v-if="canEdit"
                    size="small"
                    @click="editConfig(config)"
                  >
                    <el-icon><Edit /></el-icon>
                  </el-button>
                  <el-button
                    v-if="!config.enabled && canToggle"
                    size="small"
                    type="success"
                    plain
                    @click="toggleEnabled(config)"
                    :loading="togglingId === config.id"
                  >
                    {{ $t('ai.enable') }}
                  </el-button>
                  <el-button
                    v-if="config.enabled && canToggle"
                    size="small"
                    type="warning"
                    plain
                    @click="toggleEnabled(config)"
                    :loading="togglingId === config.id"
                  >
                    {{ $t('ai.disable') }}
                  </el-button>
                  <el-popconfirm
                    v-if="canDelete"
                    :title="$t('ai.deleteConfigConfirm')"
                    @confirm="deleteConfig(config.id)"
                  >
                    <template #reference>
                      <el-button size="small" type="danger" plain>
                        <el-icon><Delete /></el-icon>
                      </el-button>
                    </template>
                  </el-popconfirm>
                </div>
              </div>
              <div class="config-item-details">
                <div class="detail-row">
                  <span class="detail-label">{{ $t('ai.defaultModel') }}</span>
                  <el-tag size="small" type="primary" effect="plain">{{ config.defaultModel || '—' }}</el-tag>
                </div>
                <div class="detail-row" v-if="config.models">
                  <span class="detail-label">{{ $t('ai.models') }}</span>
                  <span class="detail-value">
                    <el-tag
                      v-for="(m, idx) in parseModels(config.models)"
                      :key="idx"
                      size="small"
                      effect="plain"
                      style="margin-right: 4px"
                    >{{ m }}</el-tag>
                  </span>
                </div>
                <div class="detail-row">
                  <span class="detail-label">{{ $t('ai.apiUrl') }}</span>
                  <code>{{ config.apiUrl || '—' }}</code>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 编辑/新增对话框 -->
      <el-dialog
        v-model="dialogVisible"
        :title="editingConfig?.id ? $t('ai.configTitle', { name: editingConfig?.providerName || '' }) : $t('ai.addConfig')"
        width="680px"
        destroy-on-close
        :close-on-click-modal="false"
        class="ai-config-dialog"
        align-center
      >
        <div class="dialog-body">
          <el-form :model="form" label-width="120px" class="config-form" label-position="right">
            <!-- 基础信息 -->
            <div class="dialog-section">
              <div class="section-title">
                <span class="section-icon">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M12 20h9"/>
                    <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/>
                  </svg>
                </span>
                <span>{{ $t('ai.basicInfo') }}</span>
              </div>
              <el-form-item :label="$t('ai.selectProvider')" required>
                <el-select
                  v-model="form.provider"
                  :placeholder="$t('ai.selectHint')"
                  style="width: 100%"
                  @change="selectProvider"
                  :disabled="!!editingConfig?.id"
                >
                  <el-option
                    v-for="p in providerList"
                    :key="p.key"
                    :label="p.name"
                    :value="p.key"
                  >
                    <span>{{ p.name }}</span>
                    <el-tag
                      v-if="p.key === 'custom'"
                      size="small"
                      type="warning"
                      effect="plain"
                      style="margin-left: 8px"
                    >{{ $t('ai.customTag') }}</el-tag>
                    <el-tag
                      v-else-if="isPreset(p.key)"
                      size="small"
                      type="primary"
                      effect="plain"
                      style="margin-left: 8px"
                    >{{ $t('ai.preset') }}</el-tag>
                  </el-option>
                </el-select>
              </el-form-item>

              <el-form-item :label="$t('ai.providerName')" required>
                <el-input v-model="form.providerName" :placeholder="$t('ai.providerNamePlaceholder')" />
              </el-form-item>
            </div>

            <!-- API 配置 -->
            <div class="dialog-section">
              <div class="section-title">
                <span class="section-icon">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/>
                    <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>
                  </svg>
                </span>
                <span>{{ $t('ai.apiConfig') }}</span>
              </div>
              <el-form-item :label="$t('ai.apiUrl')" required>
                <el-input v-model="form.apiUrl" :placeholder="$t('ai.apiUrlPlaceholder')" />
                <div class="form-hint">{{ $t('ai.apiUrlHint') }}</div>
              </el-form-item>

              <el-form-item :label="$t('ai.apiKey')" required>
                <el-input
                  v-model="form.apiKey"
                  type="password"
                  show-password
                  :placeholder="$t('ai.apiKeyPlaceholder')"
                />
              </el-form-item>
            </div>

            <!-- 模型配置 -->
            <div class="dialog-section">
              <div class="section-title">
                <span class="section-icon">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
                    <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
                    <line x1="12" y1="22.08" x2="12" y2="12"/>
                  </svg>
                </span>
                <span>{{ $t('ai.modelConfig') }}</span>
              </div>
              <el-form-item :label="$t('ai.defaultModel')">
                <el-select
                  v-model="form.defaultModel"
                  filterable
                  allow-create
                  default-first-option
                  clearable
                  :reserve-keyword="false"
                  :placeholder="$t('ai.modelPlaceholder')"
                  style="width: 100%"
                  @change="onDefaultModelChange"
                >
                  <el-option
                    v-for="model in form.models"
                    :key="model"
                    :label="model"
                    :value="model"
                  />
                </el-select>
              </el-form-item>

              <!-- 支持多模型 -->
              <el-form-item :label="$t('ai.models')">
                <el-select
                  v-model="form.models"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  collapse-tags
                  collapse-tags-tooltip
                  :max-collapse-tags="5"
                  clearable
                  :reserve-keyword="false"
                  :placeholder="$t('ai.modelsPlaceholder')"
                  style="width: 100%"
                >
                  <el-option
                    v-for="model in currentModelOptions"
                    :key="model"
                    :label="model"
                    :value="model"
                  />
                </el-select>
                <div class="form-hint">{{ $t('ai.modelsHint') }}</div>
              </el-form-item>
            </div>

            <!-- 高级参数 -->
            <div class="dialog-section">
              <div class="section-title">
                <span class="section-icon">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="12" cy="12" r="3"/>
                    <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
                  </svg>
                </span>
                <span>{{ $t('ai.advancedSettings') }}</span>
              </div>
              <el-form-item :label="$t('ai.timeout')">
                <el-input-number v-model="form.timeout" :min="30" :max="600" :step="30" />
                <span class="timeout-unit">秒</span>
                <div class="form-hint">{{ $t('ai.timeoutHint') }}</div>
              </el-form-item>

              <el-form-item :label="$t('ai.maxTokens')">
                <el-input-number v-model="form.maxTokens" :min="1" :max="131072" :step="256" />
              </el-form-item>

              <el-form-item :label="$t('ai.temperature')">
                <el-slider v-model="form.temperature" :min="0" :max="2" :step="0.1" show-input />
              </el-form-item>
            </div>
          </el-form>
        </div>

        <template #footer>
          <div class="dialog-footer">
            <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
            <el-button
              v-if="canTest"
              @click="testConnectivity"
              :loading="testing"
              plain
            >
              <el-icon style="margin-right: 4px"><Link /></el-icon>
              {{ $t('ai.testConnectivity') }}
            </el-button>
            <el-button type="primary" @click="saveConfig" :loading="saving">
              <el-icon style="margin-right: 4px"><Check /></el-icon>
              {{ $t('common.save') }}
            </el-button>
          </div>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Link, Check, Plus, Edit, Delete } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'

const { t } = useI18n()
const userStore = useUserStore()

const canCreate = computed(() => userStore.hasPermission('ai-settings:create'))
const canEdit = computed(() => userStore.hasPermission('ai-settings:edit'))
const canDelete = computed(() => userStore.hasPermission('ai-settings:delete'))
const canToggle = computed(() => userStore.hasPermission('ai-settings:toggle'))
const canSetDefault = computed(() => userStore.hasPermission('ai-settings:set-default'))
const canTest = computed(() => userStore.hasPermission('ai-settings:test'))

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const dialogVisible = ref(false)
const togglingId = ref(null)
const settingDefault = ref(null)

const providerList = ref([])
const savedConfigs = ref([])
const editingConfig = ref(null)

// 预设服务商的常用模型选项
const providerModelsMap = {
  openai: ['gpt-4o', 'gpt-4o-mini', 'gpt-4-turbo', 'gpt-4', 'gpt-3.5-turbo'],
  azure: ['gpt-4o', 'gpt-4', 'gpt-3.5-turbo'],
  google: ['gemini-2.5-flash', 'gemini-2.5-pro', 'gemini-1.5-pro'],
  anthropic: ['claude-sonnet-4-20250514', 'claude-opus-4-20250514', 'claude-3-5-sonnet'],
  deepseek: ['deepseek-chat', 'deepseek-reasoner', 'deepseek-coder'],
  qwen: ['qwen-plus', 'qwen-turbo', 'qwen-max', 'qwen-coder-plus'],
  zhipu: ['glm-4-plus', 'glm-4', 'glm-3-turbo'],
  moonshot: ['moonshot-v1-8k', 'moonshot-v1-32k', 'moonshot-v1-128k'],
  baichuan: ['Baichuan4', 'Baichuan3-Turbo', 'Baichuan2-53B'],
  minimax: ['abab6.5s-chat', 'abab6.5-chat', 'abab6-chat'],
  xiaomi: ['mimo-pro', 'mimo-vision'],
  bytedance: ['doubao-pro-256k', 'doubao-pro-4k', 'doubao-lite-4k', 'doubao-vision-pro'],
  custom: []
}

const currentModelOptions = computed(() => {
  if (!form.value.provider) return []
  const presets = new Set(providerModelsMap[form.value.provider] || [])
  form.value.models.forEach(m => m && presets.add(m))
  return Array.from(presets)
})

// 默认模型变更：自定义输入时自动加入支持模型列表
function onDefaultModelChange(val) {
  if (typeof val === 'string' && val && !form.value.models.includes(val)) {
    form.value.models.push(val)
  }
}

const form = ref({
  provider: '',
  providerName: '',
  apiUrl: '',
  apiKey: '',
  defaultModel: '',
  models: [],
  maxTokens: 4096,
  temperature: 0.7,
  timeout: 120
})

function parseModels(modelsStr) {
  if (!modelsStr) return []
  return modelsStr.split(',').map(m => m.trim()).filter(m => m)
}

function isPreset(key) {
  return key !== 'custom'
}

// 加载预设服务商列表
async function loadPresetProviders() {
  try {
    const res = await request.get('/ai-config/preset-providers')
    if (res.code === 200) {
      const map = res.data
      providerList.value = Object.keys(map).map(key => ({
        key,
        name: map[key].name,
        apiUrl: map[key].apiUrl,
        defaultModel: map[key].defaultModel,
        website: map[key].website || ''
      }))
    }
  } catch (e) {
    console.error('加载预设服务商失败', e)
  }
}

// 加载已保存的配置
async function loadConfigs() {
  try {
    const res = await request.get('/ai-config')
    if (res.code === 200) {
      savedConfigs.value = res.data || []
      const enabled = savedConfigs.value.find(c => c.enabled && c.isDefault) || savedConfigs.value.find(c => c.enabled)
      if (enabled && enabled.timeout) {
        localStorage.setItem('aiTimeout', enabled.timeout * 1000)
      }
    }
  } catch (e) {
    console.error('加载AI配置失败', e)
  }
}

// 新增配置
function showAddDialog() {
  editingConfig.value = null
  form.value = {
    provider: '',
    providerName: '',
    apiUrl: '',
    apiKey: '',
    defaultModel: '',
    models: [],
    maxTokens: 4096,
    temperature: 0.7,
    timeout: 120
  }
  dialogVisible.value = true
}

// 编辑配置
function editConfig(config) {
  editingConfig.value = config
  form.value = {
    id: config.id,
    provider: config.provider,
    providerName: config.providerName,
    apiUrl: config.apiUrl,
    apiKey: config.apiKey,
    defaultModel: config.defaultModel || '',
    models: config.models ? config.models.split(',').map(m => m.trim()).filter(m => m) : [],
    maxTokens: config.maxTokens || 4096,
    temperature: config.temperature || 0.7,
    timeout: config.timeout || 120
  }
  dialogVisible.value = true
}

// 选择服务商
function selectProvider(key) {
  const preset = providerList.value.find(p => p.key === key)
  if (preset) {
    form.value.providerName = preset.name
    form.value.apiUrl = preset.apiUrl
    form.value.defaultModel = preset.defaultModel
    // 默认模型只填充到默认模型字段，支持模型由用户自行选择/输入
  }
}

// 保存配置
async function saveConfig() {
  if (!form.value.provider || !form.value.apiUrl || !form.value.apiKey) {
    ElMessage.warning(t('ai.validation'))
    return
  }

  saving.value = true
  try {
    const payload = {
      ...form.value,
      models: form.value.models.join(','),
      enabled: editingConfig.value?.enabled || false
    }
    const res = await request.post('/ai-config', payload)
    if (res.code === 200) {
      const timeout = form.value.timeout || 120
      localStorage.setItem('aiTimeout', timeout * 1000)
      ElMessage.success(t('common.success'))
      dialogVisible.value = false
      await loadConfigs()
    }
  } catch (e) {
    ElMessage.error(t('common.error'))
  } finally {
    saving.value = false
  }
}

// 切换启用
async function toggleEnabled(config) {
  togglingId.value = config.id
  try {
    const res = await request.put(`/ai-config/${config.id}/toggle`)
    if (res.code === 200) {
      ElMessage.success(res.data.enabled ? t('ai.enabled') : t('ai.disabled'))
      await loadConfigs()
    }
  } catch (e) {
    ElMessage.error(t('common.error'))
  } finally {
    togglingId.value = null
  }
}

// 设置默认
async function setDefault(id) {
  settingDefault.value = id
  try {
    const res = await request.put(`/ai-config/${id}/set-default`)
    if (res.code === 200) {
      ElMessage.success(t('ai.defaultSet'))
      await loadConfigs()
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || t('common.error'))
  } finally {
    settingDefault.value = null
  }
}

// 删除配置
async function deleteConfig(id) {
  try {
    const res = await request.delete(`/ai-config/${id}`)
    if (res.code === 200) {
      ElMessage.success(t('common.success'))
      await loadConfigs()
    }
  } catch (e) {
    ElMessage.error(t('common.error'))
  }
}

// 连通性验证
async function testConnectivity() {
  if (!form.value.apiUrl || !form.value.apiKey) {
    ElMessage.warning(t('ai.validation'))
    return
  }

  testing.value = true
  try {
    const res = await request.post('/ai-config/test-connectivity', {
      apiUrl: form.value.apiUrl,
      apiKey: form.value.apiKey,
      defaultModel: form.value.defaultModel
    })
    if (res.code === 200 && res.data) {
      if (res.data.success) {
        ElMessageBox.alert(
          t('ai.testLatency', { latency: res.data.latency, model: res.data.model }),
          t('ai.testPassed'),
          { confirmButtonText: t('common.confirm'), type: 'success' }
        )
      } else {
        ElMessageBox.alert(
          res.data.error || t('ai.testFailed'),
          t('ai.testFailed'),
          { confirmButtonText: t('common.confirm'), type: 'error' }
        )
      }
    }
  } catch (e) {
    const errorMsg = e?.response?.data?.message || e?.message || t('ai.testFailed')
    ElMessageBox.alert(errorMsg, t('ai.testFailed'), { confirmButtonText: t('common.confirm'), type: 'error' })
  } finally {
    testing.value = false
  }
}

onMounted(async () => {
  loading.value = true
  await Promise.all([loadPresetProviders(), loadConfigs()])
  loading.value = false
})
</script>

<style scoped>
.provider-settings {
  padding: 0;
  max-width: 900px;
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

.empty-state {
  text-align: center;
  padding: 40px 0;
  color: #c0c4cc;
}

/* ========== 配置列表 ========== */
.config-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.config-item {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 16px 20px;
  transition: all 0.2s ease;
}

.config-item.is-enabled {
  border-color: #b7eb8f;
  background: #fcfff5;
}

.config-item.is-default {
  border-color: #e6a23c;
  background: #fef9f0;
}

.config-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.config-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.config-item-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.config-provider-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.config-item-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.config-item-details {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  padding-top: 12px;
  border-top: 1px dashed #e4e7ed;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-label {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}

.detail-value {
  font-size: 13px;
  color: #606266;
}

.detail-row code {
  font-size: 12px;
  background: #f5f7fa;
  padding: 2px 8px;
  border-radius: 4px;
  color: #606266;
  border: 1px solid #e4e7ed;
  word-break: break-all;
}

/* ========== 配置表单 ========== */
.config-form {
  max-width: 100%;
}

.config-form :deep(.el-form-item__label) {
  font-weight: 500;
  color: #606266;
}

.form-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
  line-height: 1.5;
}

.timeout-unit {
  margin-left: 8px;
  color: #606266;
  font-size: 13px;
}



/* ========== 对话框美化 ========== */
.ai-config-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: 20px 24px 16px;
  border-bottom: 1px solid #f0f0f0;
  background: linear-gradient(135deg, #fafbff 0%, #f5f7ff 100%);
  border-radius: 12px 12px 0 0;
}

.ai-config-dialog :deep(.el-dialog__title) {
  font-weight: 700;
  font-size: 17px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-config-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.ai-config-dialog :deep(.el-dialog__footer) {
  padding: 16px 24px;
  border-top: 1px solid #f0f0f0;
  background: #fafbfc;
}

.dialog-body {
  padding: 24px;
  max-height: 70vh;
  overflow-y: auto;
}

.dialog-section {
  background: #fafbfc;
  border-radius: 10px;
  padding: 18px 20px 6px;
  margin-bottom: 16px;
  border: 1px solid #eef0f5;
}

.dialog-section:last-child {
  margin-bottom: 0;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  padding-bottom: 10px;
  border-bottom: 1px dashed #e4e7ed;
}

.section-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 7px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  flex-shrink: 0;
}

.section-icon svg {
  stroke-width: 2.2;
}

.config-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.config-form :deep(.el-input__wrapper),
.config-form :deep(.el-textarea__inner),
.config-form :deep(.el-select) {
  border-radius: 8px;
}

.config-form :deep(.el-slider) {
  padding-left: 6px;
  margin-right: 12px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* ========== 分割线覆盖 ========== */
.config-card :deep(.el-divider--horizontal) {
  margin: 16px 0 20px;
}
</style>
