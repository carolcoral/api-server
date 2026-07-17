<!--
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
-->

<template>
  <div class="ai-subscription-page">
    <div class="page-header">
      <h2>{{ $t('aiSubscription.title') }}</h2>
      <p class="page-desc">{{ $t('aiSubscription.description') }}</p>
    </div>

    <!-- 我的订阅 -->
    <el-card class="section-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ $t('aiSubscription.mySubscriptions') }}</span>
          <el-button v-if="canSubscribe" type="primary" @click="openSubscribeDialog">
            {{ $t('aiSubscription.subscribeService') }}
          </el-button>
        </div>
      </template>
      <el-table :data="subscriptions" v-loading="subsLoading" empty-text="暂无订阅">
        <el-table-column prop="id" :label="$t('aiService.id')" width="70" />
        <el-table-column prop="providerName" :label="$t('aiService.provider')" width="150" show-overflow-tooltip />
        <el-table-column prop="displayName" :label="$t('aiService.model')" width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="url-cell">
              <span>{{ row.displayName || row.modelName }}</span>
              <el-button size="small" text type="primary" @click="copyKey(row.modelName)">
                <el-icon><CopyDocument /></el-icon>
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="$t('aiService.apiUrl')" min-width="360">
          <template #default="{ row }">
            <div class="url-cell">
              <code class="url-code">{{ getSystemProxyUrl(row) }}</code>
              <el-button size="small" text type="primary" @click="copyUrl(getSystemProxyUrl(row))">
                <el-icon><CopyDocument /></el-icon>
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="$t('aiService.price')" width="100" align="center">
          <template #default="{ row }">
            <div class="price-cell">
              <span v-if="row.inputPrice != null">${{ row.inputPrice }}</span>
              <span v-else class="no-data">—</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="$t('aiService.maxTokens')" width="100" align="center">
          <template #default="{ row }">{{ row.maxTokens || '—' }}</template>
        </el-table-column>
        <el-table-column prop="priority" :label="$t('aiService.priority')" width="80" align="center" />
        <el-table-column prop="weight" :label="$t('aiService.weight')" width="80" align="center" />
        <el-table-column :label="$t('aiSubscription.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status ? 'success' : 'danger'" size="small">
              {{ row.status ? $t('aiSubscription.subscribed') : $t('aiSubscription.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="$t('aiSubscription.subscribeTime')" width="160" />
        <el-table-column v-if="canSubscribe" :label="$t('aiService.actions')" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-popconfirm
              :title="$t('aiSubscription.unsubscribeConfirm')"
              @confirm="handleUnsubscribe(row)"
            >
              <template #reference>
                <el-button type="danger" link size="small">{{ $t('aiSubscription.unsubscribe') }}</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 我的 API Keys -->
    <el-card class="section-card" shadow="never" style="margin-top: 20px">
      <template #header>
        <div class="card-header">
          <span>{{ $t('aiSubscription.myApiKeys') }}</span>
          <el-button v-if="canManageKey" type="primary" @click="showCreateKeyDialog = true">
            {{ $t('aiSubscription.createKey') }}
          </el-button>
        </div>
      </template>
      <el-table :data="apiKeys" v-loading="keysLoading" empty-text="暂无 API Key">
        <el-table-column prop="id" :label="$t('aiService.id')" width="70" />
        <el-table-column prop="keyName" :label="$t('aiService.keyName')" width="160" />
        <el-table-column prop="apiKey" :label="$t('aiService.apiKey')" min-width="240">
          <template #default="{ row }">
            <div class="key-cell">
              <code class="key-mask">{{ maskKey(row.apiKey) }}</code>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="lastUsed" :label="$t('aiService.lastUsed')" width="160">
          <template #default="{ row }">
            {{ row.lastUsed || '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('aiService.tokenUsed')" width="120" align="right">
          <template #default="{ row }">{{ formatTokenNumber(row.totalTokensUsed) }}</template>
        </el-table-column>
        <el-table-column prop="createTime" :label="$t('aiSubscription.createTime')" width="160" />
        <el-table-column v-if="canManageKey" :label="$t('aiService.actions')" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-popconfirm
              :title="$t('aiSubscription.deleteKeyConfirm')"
              @confirm="handleDeleteKey(row)"
            >
              <template #reference>
                <el-button type="danger" link size="small">{{ $t('common.delete') }}</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 订阅对话框 -->
    <el-dialog v-model="showSubscribeDialog" :title="$t('aiSubscription.subscribeService')" width="560px" destroy-on-close>
      <el-form :model="subscribeForm" label-width="100px">
        <el-form-item :label="$t('aiService.provider')" required>
          <el-select
            v-model="subscribeForm.providerId"
            :placeholder="$t('aiService.selectProvider')"
            style="width: 100%"
            @change="onProviderChange"
          >
            <el-option
              v-for="p in providers"
              :key="p.id"
              :label="p.name"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('aiService.model')" required>
          <el-select
            v-model="subscribeForm.modelId"
            :placeholder="$t('aiService.selectModel')"
            style="width: 100%"
            :disabled="!subscribeForm.providerId || modelsLoading"
            @change="onModelChange"
          >
            <el-option
              v-for="m in availableModels"
              :key="m.id"
              :label="m.displayName || m.modelName"
              :value="m.id"
              :disabled="subscribedModelIds.includes(m.id)"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <!-- 模型信息展示 -->
      <div v-if="selectedModelInfo" class="model-info-card">
        <el-descriptions border :column="2" size="small">
          <el-descriptions-item :label="$t('aiService.modelName')">
            <el-tag type="success" size="small">{{ selectedModelInfo.modelName }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('aiService.displayName')">
            {{ selectedModelInfo.displayName || '—' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('aiService.inputPrice')">
            <span v-if="selectedModelInfo.inputPrice != null">${{ selectedModelInfo.inputPrice }}</span>
            <span v-else class="no-data">—</span>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('aiService.outputPrice')">
            <span v-if="selectedModelInfo.outputPrice != null">${{ selectedModelInfo.outputPrice }}</span>
            <span v-else class="no-data">—</span>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('aiService.maxTokens')">
            {{ selectedModelInfo.maxTokens || '—' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('aiSubscription.subscriberCount')">
            <el-tag type="info" size="small">{{ selectedModelInfo.subscriberCount || 0 }} 人</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('aiService.health')">
            <el-tag :type="selectedModelInfo.healthStatus === 'online' ? 'success' : 'warning'" size="small">
              {{ selectedModelInfo.healthStatus === 'online' ? $t('aiService.online') : $t('aiService.offline') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('aiService.stream')">
            {{ selectedModelInfo.supportsStream ? '✓' : '✗' }}
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <el-empty v-if="subscribeForm.providerId && availableModels.length === 0 && !modelsLoading" :description="$t('aiSubscription.noModels')" :image-size="60" />

      <template #footer>
        <el-button @click="showSubscribeDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          :disabled="!subscribeForm.modelId || subscribedModelIds.includes(subscribeForm.modelId)"
          :loading="subscribing"
          @click="handleSubscribe"
        >
          {{ subscribedModelIds.includes(subscribeForm.modelId) ? $t('aiSubscription.subscribed') : $t('aiSubscription.subscribe') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 创建 API Key 对话框 -->
    <el-dialog v-model="showCreateKeyDialog" :title="$t('aiSubscription.createKey')" width="520px" destroy-on-close>
      <el-form :model="keyForm" label-width="120px">
        <el-form-item :label="$t('aiService.keyName')">
          <el-input v-model="keyForm.keyName" :placeholder="$t('aiService.keyNamePlaceholder')" maxlength="50" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateKeyDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creatingKey" @click="handleCreateKey">
          {{ $t('aiService.generate') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 生成的 API Key 展示对话框 -->
    <el-dialog v-model="showGeneratedKey" :title="$t('aiService.generatedKey')" width="520px" :close-on-click-modal="false">
      <el-alert :title="$t('aiService.keyWarning')" type="warning" show-icon :closable="false" style="margin-bottom: 16px" />
      <div class="generated-key-box">
        <div class="key-display">
          <code>{{ generatedKey }}</code>
        </div>
        <el-button type="primary" @click="copyKey(generatedKey)">
          <el-icon style="margin-right:4px"><CopyDocument /></el-icon>
          {{ $t('aiService.copyKey') }}
        </el-button>
      </div>
      <template #footer>
        <el-button type="primary" @click="showGeneratedKey = false">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { CopyDocument } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import {
  listAvailableProviders,
  listAvailableModels,
  listMySubscriptions,
  subscribeModel,
  unsubscribeModel,
  listMyApiKeys,
  createMyApiKey,
  deleteMyApiKey
} from '@/api/aiUser'

const { t } = useI18n()
const userStore = useUserStore()

// ========== 权限控制 ==========
const canSubscribe = computed(() => userStore.hasPermission('ai-subscription:subscribe'))
const canManageKey = computed(() => userStore.hasPermission('ai-subscription:key-manage'))

// ========== 我的订阅 ==========
const subscriptions = ref([])
const subsLoading = ref(false)

const fetchSubscriptions = async () => {
  subsLoading.value = true
  try {
    const res = await listMySubscriptions()
    if (res.code === 200) {
      subscriptions.value = res.data || []
    }
  } catch (e) {
    console.error('获取订阅列表失败', e)
  } finally {
    subsLoading.value = false
  }
}

const handleUnsubscribe = async (row) => {
  try {
    const res = await unsubscribeModel(row.id)
    if (res.code === 200) {
      ElMessage.success(t('aiSubscription.unsubscribeSuccess'))
      fetchSubscriptions()
    } else {
      ElMessage.error(res.message || t('common.error'))
    }
  } catch (e) {
    ElMessage.error(t('common.error'))
  }
}

// ========== 订阅对话框 ==========
const showSubscribeDialog = ref(false)
const providers = ref([])
const providersLoading = ref(false)
const availableModels = ref([])
const modelsLoading = ref(false)
const subscribing = ref(false)
const selectedModelInfo = ref(null)

const subscribeForm = reactive({
  providerId: null,
  modelId: null
})

const subscribedModelIds = computed(() => {
  return subscriptions.value.map(s => s.modelId)
})

const fetchProviders = async () => {
  providersLoading.value = true
  try {
    const res = await listAvailableProviders()
    if (res.code === 200) {
      providers.value = res.data || []
    }
  } catch (e) {
    console.error('获取服务商列表失败', e)
  } finally {
    providersLoading.value = false
  }
}

const openSubscribeDialog = () => {
  subscribeForm.providerId = null
  subscribeForm.modelId = null
  selectedModelInfo.value = null
  availableModels.value = []
  showSubscribeDialog.value = true
}

const onProviderChange = async (providerId) => {
  subscribeForm.modelId = null
  selectedModelInfo.value = null
  if (!providerId) {
    availableModels.value = []
    return
  }
  modelsLoading.value = true
  try {
    const res = await listAvailableModels(providerId)
    if (res.code === 200) {
      availableModels.value = res.data || []
    }
  } catch (e) {
    console.error('获取模型列表失败', e)
  } finally {
    modelsLoading.value = false
  }
}

const onModelChange = (modelId) => {
  if (!modelId) {
    selectedModelInfo.value = null
    return
  }
  const model = availableModels.value.find(m => m.id === modelId)
  selectedModelInfo.value = model || null
}

const handleSubscribe = async () => {
  if (!subscribeForm.modelId) {
    ElMessage.warning('请先选择一个模型')
    return
  }
  if (subscribedModelIds.value.includes(subscribeForm.modelId)) {
    ElMessage.warning('您已订阅此模型')
    return
  }
  subscribing.value = true
  try {
    const res = await subscribeModel(subscribeForm.modelId)
    if (res.code === 200) {
      ElMessage.success(t('aiSubscription.subscribeSuccess'))
      showSubscribeDialog.value = false
      fetchSubscriptions()
    } else {
      ElMessage.error(res.message || t('common.error'))
    }
  } catch (e) {
    ElMessage.error(t('common.error'))
  } finally {
    subscribing.value = false
  }
}

// ========== API Keys ==========
const apiKeys = ref([])
const keysLoading = ref(false)
const showCreateKeyDialog = ref(false)
const showGeneratedKey = ref(false)
const creatingKey = ref(false)
const generatedKey = ref('')
const keyForm = reactive({
  keyName: ''
})

const fetchApiKeys = async () => {
  keysLoading.value = true
  try {
    const res = await listMyApiKeys()
    if (res.code === 200) {
      apiKeys.value = res.data || []
    }
  } catch (e) {
    console.error('获取API Key列表失败', e)
  } finally {
    keysLoading.value = false
  }
}

const handleCreateKey = async () => {
  creatingKey.value = true
  try {
    const res = await createMyApiKey(keyForm.keyName || 'Default')
    if (res.code === 200) {
      generatedKey.value = res.data.apiKey
      showCreateKeyDialog.value = false
      showGeneratedKey.value = true
      keyForm.keyName = ''
      fetchApiKeys()
    } else {
      ElMessage.error(res.message || t('common.error'))
    }
  } catch (e) {
    ElMessage.error(t('common.error'))
  } finally {
    creatingKey.value = false
  }
}

const handleDeleteKey = async (row) => {
  try {
    const res = await deleteMyApiKey(row.id)
    if (res.code === 200) {
      ElMessage.success(t('aiSubscription.deleteKeySuccess'))
      fetchApiKeys()
    } else {
      ElMessage.error(res.message || t('common.error'))
    }
  } catch (e) {
    ElMessage.error(t('common.error'))
  }
}

const maskKey = (key) => {
  if (!key || key.length <= 16) return key
  return key.substring(0, 8) + '****' + key.substring(key.length - 8)
}

const copyKey = async (key) => {
  try {
    await navigator.clipboard.writeText(key)
    ElMessage.success(t('api.copySuccess'))
  } catch {
    ElMessage.error(t('api.copyFailed'))
  }
}

const getSystemProxyUrl = (row) => {
  const origin = window.location.origin
  return `${origin}/api/ai/v1/chat/completions`
}

const formatTokenNumber = (n) => {
  if (n == null) return '—'
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
  if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K'
  return String(n)
}

const copyUrl = async (url) => {
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success(t('api.copySuccess'))
  } catch {
    ElMessage.error(t('api.copyFailed'))
  }
}

// ========== 生命周期 ==========
onMounted(() => {
  fetchSubscriptions()
  fetchApiKeys()
  fetchProviders()
})
</script>

<style scoped>
.ai-subscription-page {
  padding: 4px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 8px 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.page-desc {
  margin: 0;
  font-size: 14px;
  color: #909399;
}

.section-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.url-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.url-code {
  font-size: 12px;
  color: #606266;
  background: #f5f7fa;
  padding: 4px 10px;
  border-radius: 4px;
  word-break: break-all;
  font-family: 'Courier New', monospace;
  letter-spacing: 0.5px;
}

.price-cell {
  font-size: 13px;
  color: #606266;
}

.no-data {
  color: #c0c4cc;
}

.key-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.key-mask {
  font-size: 12px;
  background: #f5f7fa;
  padding: 4px 10px;
  border-radius: 4px;
  color: #606266;
  font-family: 'Courier New', monospace;
  letter-spacing: 0.5px;
}

.model-info-card {
  margin-top: 8px;
  padding: 8px 0;
}

.generated-key-box {
  text-align: center;
  padding: 8px 0;
}

.key-display {
  background: #f0f9eb;
  border: 1px solid #b7eb8f;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
  word-break: break-all;
}

.key-display code {
  font-size: 14px;
  color: #67c23a;
  font-family: 'Courier New', monospace;
}
</style>
