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
        {{ $t('aiService.addProvider') }}
      </el-button>
      <el-button @click="loadData" :loading="loading">
        <el-icon style="margin-right:4px"><Refresh /></el-icon>
        {{ $t('common.refresh') }}
      </el-button>
    </div>

    <el-table :data="providers" v-loading="loading" stripe border style="width:100%">
      <el-table-column prop="id" :label="$t('aiService.id')" width="70" />
      <el-table-column prop="name" :label="$t('aiService.providerName')" min-width="140" />
      <el-table-column prop="code" :label="$t('aiService.code')" width="120" />
      <el-table-column prop="baseUrl" :label="$t('aiService.baseUrl')" min-width="220" show-overflow-tooltip />
      <el-table-column prop="apiType" :label="$t('aiService.apiType')" width="100" />
      <el-table-column prop="authType" :label="$t('aiService.authType')" width="100" />
      <el-table-column :label="$t('aiService.status')" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status ? 'success' : 'info'" size="small" effect="plain" round>
            {{ row.status ? $t('ai.enabled') : $t('ai.disabled') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('aiService.actions')" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="editProvider(row)">
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
    <el-dialog v-model="dialogVisible" :title="editing ? $t('aiService.editProvider') : $t('aiService.addProvider')" width="580px" destroy-on-close :close-on-click-modal="false" align-center>
      <el-form :model="form" label-width="100px">
        <el-form-item :label="$t('aiService.apiType')" required>
          <el-select v-model="form.apiType" style="width:100%" @change="onApiTypeChange" :disabled="!!editing">
            <el-option label="OpenAI" value="OPENAI" />
            <el-option label="vLLM" value="VLLM" />
            <el-option label="Ollama" value="OLLAMA" />
            <el-option label="Custom" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <!-- 服务商预设/自定义选择（所有类型都支持） -->
        <el-form-item :label="$t('aiService.presetProvider')">
          <el-select
            v-model="selectedPreset"
            style="width:100%"
            :placeholder="$t('aiService.presetProviderPlaceholder')"
            filterable
            allow-create
            clearable
            @change="onPresetChange"
          >
            <el-option
              v-for="p in currentPresets"
              :key="p.value"
              :label="p.label"
              :value="p.value"
            />
          </el-select>
          <div class="preset-hint">{{ $t('aiService.presetProviderHint') }}</div>
        </el-form-item>
        <el-form-item :label="$t('aiService.providerName')" required>
          <el-input v-model="form.name" :placeholder="$t('aiService.providerNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('aiService.code')" required>
          <el-input v-model="form.code" :placeholder="$t('aiService.codePlaceholder')" :disabled="!!editing" />
        </el-form-item>
        <el-form-item :label="$t('aiService.baseUrl')" required>
          <el-input v-model="form.baseUrl" :placeholder="$t('aiService.baseUrlPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('aiService.authType')" required>
          <el-select v-model="form.authType" style="width:100%">
            <el-option label="API Key" value="API_KEY" />
            <el-option label="Bearer" value="BEARER" />
            <el-option label="None" value="NONE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('ai.apiKey')">
          <el-input v-model="form.apiKey" type="password" show-password :placeholder="$t('aiService.apiKeyPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('aiService.status')">
          <el-switch v-model="form.status" :active-text="$t('ai.enabled')" :inactive-text="$t('ai.disabled')" />
        </el-form-item>
        <el-form-item :label="$t('aiService.description')">
          <el-input v-model="form.description" type="textarea" :rows="3" />
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
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Edit, Delete } from '@element-plus/icons-vue'
import { listProviders, createProvider, updateProvider, deleteProvider } from '@/api/aiService'

const loading = ref(false)
const saving = ref(false)
const providers = ref([])
const dialogVisible = ref(false)
const editing = ref(null)
const selectedPreset = ref(null)

// 预设服务商列表 - 按 apiType 分组
const presetsByType = {
  OPENAI: [
    { label: 'OpenAI', value: 'openai', name: 'OpenAI', code: 'openai', baseUrl: 'https://api.openai.com/v1' },
    { label: 'Azure OpenAI', value: 'azure', name: 'Azure OpenAI', code: 'azure', baseUrl: 'https://{resource}.openai.azure.com/openai/deployments/{deployment}' },
    { label: 'DeepSeek', value: 'deepseek', name: 'DeepSeek', code: 'deepseek', baseUrl: 'https://api.deepseek.com/v1' },
    { label: 'Anthropic Claude', value: 'anthropic', name: 'Anthropic Claude', code: 'anthropic', baseUrl: 'https://api.anthropic.com/v1' },
    { label: 'Google Gemini', value: 'gemini', name: 'Google Gemini', code: 'gemini', baseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai' },
    { label: '智谱 AI (GLM)', value: 'zhipu', name: '智谱 AI', code: 'zhipu', baseUrl: 'https://open.bigmodel.cn/api/paas/v4' },
    { label: '通义千问 (Qwen)', value: 'qwen', name: '通义千问', code: 'qwen', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1' },
    { label: '百度文心 (ERNIE)', value: 'ernie', name: '百度文心', code: 'ernie', baseUrl: 'https://qianfan.baidubce.com/v2' },
    { label: '豆包 (火山引擎)', value: 'volcengine', name: '豆包 (火山引擎)', code: 'volcengine', baseUrl: 'https://ark.cn-beijing.volces.com/api/v3' },
    { label: '月之暗面 (Moonshot)', value: 'moonshot', name: '月之暗面', code: 'moonshot', baseUrl: 'https://api.moonshot.cn/v1' },
    { label: '零一万物 (Yi)', value: 'yi', name: '零一万物', code: 'yi', baseUrl: 'https://api.lingyiwanwu.com/v1' },
    { label: '百川智能 (Baichuan)', value: 'baichuan', name: '百川智能', code: 'baichuan', baseUrl: 'https://api.baichuan-ai.com/v1' },
    { label: 'MiniMax', value: 'minimax', name: 'MiniMax', code: 'minimax', baseUrl: 'https://api.minimax.chat/v1' },
    { label: 'StepFun (阶跃星辰)', value: 'stepfun', name: '阶跃星辰', code: 'stepfun', baseUrl: 'https://api.stepfun.com/v1' },
    { label: 'Groq', value: 'groq', name: 'Groq', code: 'groq', baseUrl: 'https://api.groq.com/openai/v1' },
    { label: 'Together AI', value: 'together', name: 'Together AI', code: 'together', baseUrl: 'https://api.together.xyz/v1' },
    { label: 'Mistral AI', value: 'mistral', name: 'Mistral AI', code: 'mistral', baseUrl: 'https://api.mistral.ai/v1' },
    { label: 'Cohere', value: 'cohere', name: 'Cohere', code: 'cohere', baseUrl: 'https://api.cohere.com/v1' },
    { label: 'Perplexity', value: 'perplexity', name: 'Perplexity', code: 'perplexity', baseUrl: 'https://api.perplexity.ai' },
    { label: 'xAI Grok', value: 'xai', name: 'xAI Grok', code: 'xai', baseUrl: 'https://api.x.ai/v1' },
    { label: 'MiMo (小米)', value: 'mimo', name: 'MiMo', code: 'mimo', baseUrl: 'https://api.xiaomimimo.com/v1' },
  ],
  VLLM: [
    { label: 'vLLM (本地默认)', value: 'vllm-local', name: 'vLLM 本地', code: 'vllm-local', baseUrl: 'http://localhost:8000/v1' },
    { label: 'vLLM (Docker)', value: 'vllm-docker', name: 'vLLM Docker', code: 'vllm-docker', baseUrl: 'http://vllm:8000/v1' },
    { label: 'SGLang', value: 'sglang', name: 'SGLang', code: 'sglang', baseUrl: 'http://localhost:30000/v1' },
    { label: 'LMDeploy', value: 'lmdeploy', name: 'LMDeploy', code: 'lmdeploy', baseUrl: 'http://localhost:23333/v1' },
    { label: 'LocalAI', value: 'localai', name: 'LocalAI', code: 'localai', baseUrl: 'http://localhost:8080/v1' },
    { label: 'Text Generation Inference', value: 'tgi', name: 'TGI', code: 'tgi', baseUrl: 'http://localhost:8080/v1' },
    { label: 'Xinference', value: 'xinference', name: 'Xinference', code: 'xinference', baseUrl: 'http://localhost:9997/v1' },
    { label: 'FastChat', value: 'fastchat', name: 'FastChat', code: 'fastchat', baseUrl: 'http://localhost:8000/v1' },
  ],
  OLLAMA: [
    { label: 'Ollama (本地默认)', value: 'ollama-local', name: 'Ollama 本地', code: 'ollama-local', baseUrl: 'http://localhost:11434/v1' },
    { label: 'Ollama (Docker)', value: 'ollama-docker', name: 'Ollama Docker', code: 'ollama-docker', baseUrl: 'http://ollama:11434/v1' },
    { label: 'Ollama (远程)', value: 'ollama-remote', name: 'Ollama 远程', code: 'ollama-remote', baseUrl: 'http://192.168.1.100:11434/v1' },
    { label: 'Ollama (GPU服务器)', value: 'ollama-gpu', name: 'Ollama GPU', code: 'ollama-gpu', baseUrl: 'http://gpu-server:11434/v1' },
  ],
  CUSTOM: [
    { label: '自定义 (OpenAI 协议)', value: 'custom-openai', name: '自定义服务', code: 'custom-openai', baseUrl: 'https://your-api.com/v1' },
    { label: '自定义 (通用 HTTP)', value: 'custom-http', name: '自定义 HTTP', code: 'custom-http', baseUrl: 'https://your-api.com' },
  ]
}

// 当前 apiType 对应的预设列表
const currentPresets = computed(() => {
  return presetsByType[form.value.apiType] || []
})

const form = ref({
  name: '', code: '', baseUrl: '', apiType: 'OPENAI', authType: 'API_KEY',
  apiKey: '', status: true, description: ''
})

function resetForm() {
  form.value = {
    name: '', code: '', baseUrl: '', apiType: 'OPENAI', authType: 'API_KEY',
    apiKey: '', status: true, description: ''
  }
  selectedPreset.value = null
}

// API 类型切换时，重置预设选择并清空表单
function onApiTypeChange() {
  selectedPreset.value = null
  // 切换类型时清空名称、编码、地址，让用户重新选择预设或自行填写
  form.value.name = ''
  form.value.code = ''
  form.value.baseUrl = ''
}

// 预设服务商选择
function onPresetChange(val) {
  if (!val) {
    // 清空选择，不自动填充
    return
  }
  const preset = currentPresets.value.find(p => p.value === val)
  if (preset) {
    // 选择预设，自动填充
    form.value.name = preset.name
    form.value.code = preset.code
    form.value.baseUrl = preset.baseUrl
  } else {
    // 自定义输入（allow-create），把输入值作为名称和编码
    form.value.name = val
    form.value.code = val.toLowerCase().replace(/\s+/g, '-')
    form.value.baseUrl = ''
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await listProviders()
    if (res.code === 200) providers.value = res.data || []
  } catch (e) {
    ElMessage.error('加载服务商失败')
  } finally {
    loading.value = false
  }
}

function showAddDialog() {
  editing.value = null
  resetForm()
  dialogVisible.value = true
}

function editProvider(row) {
  editing.value = row
  selectedPreset.value = null
  form.value = {
    name: row.name, code: row.code, baseUrl: row.baseUrl,
    apiType: row.apiType, authType: row.authType, apiKey: '',
    status: row.status, description: row.description || ''
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.name || !form.value.code || !form.value.baseUrl) {
    ElMessage.warning('请填写必要信息')
    return
  }
  saving.value = true
  try {
    let res
    if (editing.value) {
      res = await updateProvider(editing.value.id, form.value)
    } else {
      res = await createProvider(form.value)
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
    const res = await deleteProvider(id)
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
.preset-hint { font-size: 12px; color: #909399; margin-top: 4px; line-height: 1.5; }
</style>
