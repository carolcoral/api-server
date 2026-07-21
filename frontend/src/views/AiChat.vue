<!--
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
-->

<template>
  <div class="ai-chat-page">
    <!-- 头部 -->
    <div class="chat-header">
      <div class="chat-header-left">
        <div class="header-icon">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            <path d="M8 9h8" stroke-width="1.5" />
            <path d="M8 13h5" stroke-width="1.5" />
          </svg>
        </div>
        <h2>{{ $t('nav.aiChat') }}</h2>
      </div>
      <div class="chat-header-center">
        <!-- AI 设置/模型切换下拉 -->
        <template v-if="enabledConfigs.length > 0">
          <div class="ai-selector-wrapper">
            <el-select
              v-model="selectedConfigId"
              :placeholder="$t('ai.switchAi')"
              size="small"
              class="ai-config-select"
              @change="onConfigChange"
            >
              <template #prefix>
                <el-icon class="select-prefix-icon"><Setting /></el-icon>
              </template>
              <el-option
                v-for="cfg in enabledConfigs"
                :key="cfg.id"
                :label="cfg.providerName"
                :value="cfg.id"
              >
                <div class="ai-config-option">
                  <span class="ai-config-name">{{ cfg.providerName }}</span>
                  <el-tag v-if="cfg.isDefault" size="small" type="primary" effect="light" class="ai-config-default-tag">
                    {{ $t('ai.defaultConfig') }}
                  </el-tag>
                </div>
              </el-option>
            </el-select>
          </div>
          <div class="ai-model-selector-wrapper">
            <el-select
              v-if="currentModels.length > 0"
              v-model="selectedModel"
              :placeholder="$t('ai.selectModel')"
              size="small"
              class="ai-model-select"
              @change="onModelChange"
            >
              <template #prefix>
                <el-icon class="select-prefix-icon"><Box /></el-icon>
              </template>
              <el-option
                v-for="m in currentModels"
                :key="m"
                :label="m"
                :value="m"
              />
            </el-select>
          </div>
        </template>
        <el-tag v-else size="small" type="warning" effect="plain">{{ $t('ai.noProvider') }}</el-tag>
      </div>
      <div class="chat-header-right">
        <el-button size="small" text @click="clearMemory" :disabled="messages.length === 0" type="warning">
          <el-icon><Delete /></el-icon>
          {{ $t('aiChat.clearMemory') }}
        </el-button>
        <el-button size="small" text @click="clearChat" :disabled="messages.length === 0">
          <el-icon><Delete /></el-icon>
          {{ $t('aiChat.clearChat') }}
        </el-button>
      </div>
    </div>

    <!-- 消息列表 -->
    <div class="chat-messages" ref="messagesRef" @click="handleCodeCopyClick">
      <!-- 空状态 -->
      <div v-if="messages.length === 0" class="chat-empty">
        <div class="empty-icon">
          <svg viewBox="0 0 24 24" width="56" height="56" fill="none" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 2l1.5 5.5L19 9l-5.5 1.5L12 16l-1.5-5.5L5 9l5.5-1.5z"/>
            <path d="M18 16l.75 2.25L21 19l-2.25.75L18 22l-.75-2.25L15 19l2.25-.75z"/>
            <path d="M6 18l.5 1.5L8 20l-1.5.5L6 22l-.5-1.5L4 20l1.5-.5z"/>
          </svg>
        </div>
        <h3>{{ $t('aiChat.welcomeTitle') }}</h3>
        <p>{{ $t('aiChat.welcomeDesc') }}</p>
        <div class="suggestion-chips">
          <div
            v-for="(q, idx) in suggestions"
            :key="idx"
            class="suggestion-chip"
            @click="sendMessage(q)"
          >
            <el-icon :size="14"><ChatDotRound /></el-icon>
            <span>{{ q }}</span>
          </div>
        </div>
      </div>

      <!-- 消息 -->
      <div
        v-for="(msg, idx) in messages"
        :key="idx"
        class="chat-message"
        :class="{ 'message-user': msg.role === 'user', 'message-assistant': msg.role === 'assistant' }"
      >
        <div class="message-avatar">
          <template v-if="msg.role === 'user'">
            <el-avatar :size="32" :src="userStore.userAvatar" />
          </template>
          <template v-else>
            <div class="ai-avatar">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 2l1.5 5.5L19 9l-5.5 1.5L12 16l-1.5-5.5L5 9l5.5-1.5z"/>
                <path d="M18 16l.75 2.25L21 19l-2.25.75L18 22l-.75-2.25L15 19l2.25-.75z"/>
                <path d="M6 18l.5 1.5L8 20l-1.5.5L6 22l-.5-1.5L4 20l1.5-.5z"/>
              </svg>
            </div>
          </template>
        </div>
        <div class="message-body">
          <div class="message-content" v-html="renderMarkdown(msg.content)"></div>
          <div class="message-actions" v-if="msg.role === 'assistant'">
            <el-button size="small" text @click="copyContent(msg.content)">
              <el-icon><CopyDocument /></el-icon>
            </el-button>
          </div>
        </div>
      </div>

      <!-- 加载动画 -->
      <div v-if="loading" class="chat-message message-assistant">
        <div class="message-avatar">
          <div class="ai-avatar">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 2l1.5 5.5L19 9l-5.5 1.5L12 16l-1.5-5.5L5 9l5.5-1.5z"/>
              <path d="M18 16l.75 2.25L21 19l-2.25.75L18 22l-.75-2.25L15 19l2.25-.75z"/>
              <path d="M6 18l.5 1.5L8 20l-1.5.5L6 22l-.5-1.5L4 20l1.5-.5z"/>
            </svg>
          </div>
        </div>
        <div class="message-body">
          <div class="typing-indicator">
            <span></span><span></span><span></span>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="chat-input-area">
      <div class="chat-input-wrapper">
        <textarea
          v-model="inputText"
          class="chat-textarea"
          :placeholder="$t('aiChat.inputPlaceholder')"
          :disabled="loading"
          rows="1"
          ref="textareaRef"
          @input="autoResize"
          @keydown.enter.exact.prevent="sendMessage(inputText)"
        ></textarea>
        <div class="chat-input-footer">
          <span class="input-hint">{{ $t('aiChat.enterHint') }}，Shift+Enter 换行</span>
          <el-button
            type="primary"
            :disabled="!inputText.trim() || loading"
            :loading="loading"
            size="small"
            @click="sendMessage(inputText)"
          >
            <el-icon v-if="!loading"><Position /></el-icon>
            <span>{{ loading ? $t('aiChat.thinking') : '' }}</span>
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark-dimmed.css'
import mermaid from 'mermaid'
import {
  Delete, CopyDocument, Position, ChatDotRound, Setting, Box
} from '@element-plus/icons-vue'

const { t } = useI18n()
const userStore = useUserStore()

const canClearMemory = computed(() => userStore.hasPermission('ai-chat:view') || userStore.isAdmin)

// ============ highlight.js 配置 ============
marked.setOptions({
  // 不在 marked 阶段高亮，统一在 wrapCodeBlocks 中手动调用 hljs，
  // 避免高亮后的 HTML 在后续包装时被二次转义导致主题颜色丢失。
})

// ============ Mermaid 配置 ============
mermaid.initialize({
  startOnLoad: false,
  theme: 'default',
  securityLevel: 'loose',
  fontFamily: 'inherit',
  flowchart: { useMaxWidth: true, htmlLabels: true },
  sequence: { useMaxWidth: true },
  gantt: { useMaxWidth: true }
})

// 已渲染过的 mermaid 代码缓存（避免重复渲染）
const mermaidRenderedCache = new Set()

// ============ 对话历史 ============
const CHAT_STORAGE_PREFIX = 'ai_chat_history_'

const messages = ref([])
const inputText = ref('')
const loading = ref(false)
const messagesRef = ref(null)
const textareaRef = ref(null)
const modelName = ref('')

const suggestions = ref([])

// AI 设置/模型选择
const enabledConfigs = ref([])
const selectedConfigId = ref(null)
const selectedModel = ref('')

const currentConfig = ref(null)

const currentModels = computed(() => {
  if (!currentConfig.value) return []
  const modelsStr = currentConfig.value.models
  if (!modelsStr) return []
  return modelsStr.split(',').map(m => m.trim()).filter(m => m)
})

const getStorageKey = () => {
  const uid = userStore.userId || userStore.username || 'unknown'
  return CHAT_STORAGE_PREFIX + uid
}

const saveHistory = () => {
  try {
    const key = getStorageKey()
    localStorage.setItem(key, JSON.stringify(messages.value))
  } catch (e) {
    // ignore
  }
}

const loadHistory = () => {
  try {
    const key = getStorageKey()
    const raw = localStorage.getItem(key)
    if (raw) {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) {
        messages.value = parsed
      }
    }
  } catch (e) {
    // ignore
  }
}

// ============ Markdown 渲染（含代码高亮 + Mermaid 检测） ============
const escapeHtml = (str) => {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

  // 处理代码块：添加 header（语言标签 + 复制按钮）+ 行号
  const wrapCodeBlocks = (html) => {
    return html.replace(
      /<pre><code(?:\s+class="language-(\w+)")?>([\s\S]*?)<\/code><\/pre>/g,
      (match, lang, code) => {
        // 反解 marked 已经转义的 HTML 实体，得到原始源码
        const rawCode = code
          .replace(/&lt;/g, '<')
          .replace(/&gt;/g, '>')
          .replace(/&amp;/g, '&')
          .replace(/&quot;/g, '"')
          .replace(/&#39;/g, "'")

        // 如果 marked 已经返回 highlight.js 高亮 HTML（含 <span class="hljs-...">），直接保留；
        // 否则手动调用 hljs 进行高亮，确保 github-dark-dimmed 主题颜色生效。
        let highlightedCode = ''
        let langLabel = lang
        const isAlreadyHighlighted = code.includes('<span')
        if (isAlreadyHighlighted) {
          // marked 的 highlight 回调已返回 hljs 高亮 HTML，但 HTML 实体被转义了，需要还原
          highlightedCode = code
            .replace(/&lt;/g, '<')
            .replace(/&gt;/g, '>')
            .replace(/&amp;/g, '&')
            .replace(/&quot;/g, '"')
            .replace(/&#39;/g, "'")
        } else {
          if (lang && hljs.getLanguage(lang)) {
            try {
              highlightedCode = hljs.highlight(rawCode, { language: lang }).value
            } catch {
              highlightedCode = hljs.highlightAuto(rawCode).value
            }
          } else {
            try {
              const auto = hljs.highlightAuto(rawCode)
              highlightedCode = auto.value
              langLabel = auto.language
            } catch {
              highlightedCode = escapeHtml(rawCode)
            }
          }
        }
        if (!langLabel) {
          const auto = hljs.highlightAuto(rawCode)
          langLabel = auto.language || 'code'
        }

        const encodedCode = encodeURIComponent(rawCode)

        if (lang === 'mermaid') {
          return `<div class="mermaid-block" data-mermaid="${encodedCode}">${escapeHtml(rawCode)}</div>`
        }

        // 生成行号
        const lines = rawCode.split('\n')
        const lineCount = lines.length
        let lineNumbersHtml = ''
        for (let i = 1; i <= lineCount; i++) {
          lineNumbersHtml += `<span>${i}</span>`
        }

        return `
          <div class="code-block-wrapper">
            <div class="code-block-header">
              <span class="code-lang-label">${langLabel}</span>
              <button class="code-copy-btn" data-code="${encodedCode}">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                  <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                </svg>
                <span>${t('aiChat.copyCode')}</span>
              </button>
            </div>
            <div class="code-block-body">
              <div class="code-line-numbers">${lineNumbersHtml}</div>
              <pre class="code-pre"><code class="hljs">${highlightedCode}</code></pre>
            </div>
          </div>`
      }
    )
  }

const renderMarkdown = (text) => {
  if (!text) return ''
  try {
    let html = marked(text)
    html = wrapCodeBlocks(html)
    return html
  } catch {
    return text
  }
}

// ============ Mermaid 异步渲染 ============
const renderMermaidBlocks = async () => {
  await nextTick()
  if (!messagesRef.value) return

  const mermaidDivs = messagesRef.value.querySelectorAll('.mermaid-block[data-mermaid]')
  for (const div of mermaidDivs) {
    const code = decodeURIComponent(div.dataset.mermaid)
    // 避免重复渲染同一个 mermaid 块
    const cacheKey = div.dataset.mermaid
    if (mermaidRenderedCache.has(cacheKey)) continue

    try {
      const { svg } = await mermaid.render(`mermaid-${Math.random().toString(36).slice(2, 9)}`, code)
      const container = document.createElement('div')
      container.className = 'mermaid-rendered'
      container.innerHTML = svg
      div.replaceWith(container)
      mermaidRenderedCache.add(cacheKey)
    } catch (e) {
      // Mermaid 语法错误时保留原始代码，添加错误提示
      div.className = 'mermaid-block mermaid-error'
      div.insertAdjacentHTML('beforebegin',
        '<div class="mermaid-error-hint">⚠ Mermaid 语法错误，请检查图表代码</div>')
    }
  }
}

// ============ 代码块复制按钮（事件委托） ============
const handleCodeCopyClick = async (event) => {
  const btn = event.target.closest('.code-copy-btn')
  if (!btn) return

  const encodedCode = btn.dataset.code
  if (!encodedCode) return

  try {
    const code = decodeURIComponent(encodedCode)
    await navigator.clipboard.writeText(code)
    // 短暂显示"已复制"
    const span = btn.querySelector('span')
    if (span) {
      const originalText = span.textContent
      span.textContent = t('aiChat.copied')
      setTimeout(() => {
        span.textContent = originalText
      }, 1500)
    }
  } catch {
    // 兼容旧浏览器：使用 fallback
    const textarea = document.createElement('textarea')
    textarea.value = decodeURIComponent(encodedCode)
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    try {
      document.execCommand('copy')
      const span = btn.querySelector('span')
      if (span) {
        const originalText = span.textContent
        span.textContent = t('aiChat.copied')
        setTimeout(() => { span.textContent = originalText }, 1500)
      }
    } catch {
      ElMessage.error(t('aiChat.copyFailed'))
    }
    document.body.removeChild(textarea)
  }
}

// ============ 滚动 ============
const doScrollToBottom = async () => {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

// 滚动并渲染 Mermaid
const scrollToBottom = async () => {
  await doScrollToBottom()
  // 延迟一点确保 DOM 完全更新
  setTimeout(() => renderMermaidBlocks(), 50)
}

// ============ 复制 ============
const copyContent = async (text) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(t('aiChat.copySuccess'))
  } catch {
    ElMessage.error(t('aiChat.copyFailed'))
  }
}

// ============ textarea ============
const autoResize = () => {
  if (textareaRef.value) {
    textareaRef.value.style.height = 'auto'
    textareaRef.value.style.height = Math.min(textareaRef.value.scrollHeight, 120) + 'px'
  }
}

// ============ API ============
const fetchSuggestions = async () => {
  try {
    const response = await request.get('/ai/chat-suggestions')
    if (response.code === 200 && response.data && Array.isArray(response.data)) {
      suggestions.value = response.data
    }
  } catch {
    suggestions.value = [
      t('aiChat.suggest1'),
      t('aiChat.suggest2'),
      t('aiChat.suggest3'),
      t('aiChat.suggest4')
    ]
  }
}

const fetchModelName = async () => {
  try {
    const response = await request.get('/ai-config/enabled')
    if (response.code === 200 && response.data) {
      modelName.value = response.data.defaultModel || ''
    }
  } catch {
    // ignore
  }
}

const fetchEnabledConfigs = async () => {
  try {
    const response = await request.get('/ai-config/enabled-list')
    if (response.code === 200 && response.data) {
      enabledConfigs.value = response.data || []
      // 自动选择默认配置
      const defaultCfg = enabledConfigs.value.find(c => c.isDefault) || enabledConfigs.value[0]
      if (defaultCfg && !selectedConfigId.value) {
        selectedConfigId.value = defaultCfg.id
        currentConfig.value = defaultCfg
        modelName.value = defaultCfg.defaultModel || ''
        selectedModel.value = defaultCfg.defaultModel || ''
      }
    }
  } catch {
    // ignore
  }
}

const onConfigChange = (configId) => {
  const cfg = enabledConfigs.value.find(c => c.id === configId)
  if (cfg) {
    currentConfig.value = cfg
    modelName.value = cfg.defaultModel || ''
    selectedModel.value = cfg.defaultModel || ''
  }
}

const onModelChange = (model) => {
  if (model) {
    modelName.value = model
  }
}

// ============ 发送消息 ============
const sendMessage = async (text) => {
  const content = typeof text === 'string' ? text.trim() : inputText.value.trim()
  if (!content || loading.value) return

  inputText.value = ''
  if (textareaRef.value) {
    textareaRef.value.style.height = 'auto'
  }

  messages.value.push({ role: 'user', content })
  saveHistory()
  await scrollToBottom()

  loading.value = true

  const assistantIdx = messages.value.length
  messages.value.push({ role: 'assistant', content: '' })
  await scrollToBottom()

  try {
    const reqMessages = messages.value
      .slice(0, assistantIdx)
      .map(m => ({ role: m.role, content: m.content }))

    const token = userStore.token
    const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

    // 构建请求体，包含 configId 和 model
    const requestBody = { messages: reqMessages }
    if (selectedConfigId.value) {
      requestBody.configId = selectedConfigId.value
    }
    if (selectedModel.value) {
      requestBody.model = selectedModel.value
    }

    const response = await fetch(`${baseURL}/ai/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': token ? `Bearer ${token}` : ''
      },
      body: JSON.stringify(requestBody)
    })

    if (!response.ok) {
      const errorText = await response.text()
      let errorMsg = t('aiChat.networkError')
      const errorMatch = errorText.match(/\[ERROR\]\s*(.+)/)
      if (errorMatch) {
        errorMsg = errorMatch[1]
      } else if (response.status === 403) {
        errorMsg = '没有权限访问'
      }
      throw new Error(errorMsg)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      let hasContent = false
      for (const line of lines) {
        // 兼容 data: 和 data: 两种格式（Spring SseEmitter 默认不带空格）
        if (line.startsWith('data:')) {
          const data = line.substring(5).trim()
          if (data === '[DONE]') continue
          if (data.startsWith('[ERROR]')) {
            const errMsg = data.substring(8).trim()
            throw new Error(errMsg || t('aiChat.error'))
          }
          try {
            const parsed = JSON.parse(data)
            // 处理服务商错误响应（如 429 错误）
            if (parsed?.error?.message) {
              throw new Error(parsed.error.message)
            }
            const delta = parsed?.choices?.[0]?.delta?.content
            if (delta) {
              messages.value[assistantIdx].content += delta
              hasContent = true
            }
          } catch (e) {
            // 如果是业务错误，继续抛出；JSON 解析失败则忽略
            if (e instanceof SyntaxError) {
              continue
            }
            throw e
          }
        }
      }
      // 每收到一批数据后滚动一次，避免逐 delta 滚动导致 UI 卡顿和渲染延迟
      if (hasContent) {
        await scrollToBottom()
      }
    }

    if (buffer.startsWith('data:')) {
      const data = buffer.substring(5).trim()
      if (data !== '[DONE]' && !data.startsWith('[ERROR]')) {
        try {
          const parsed = JSON.parse(data)
          // 处理服务商错误响应
          if (parsed?.error?.message) {
            throw new Error(parsed.error.message)
          }
          const delta = parsed?.choices?.[0]?.delta?.content
          if (delta) {
            messages.value[assistantIdx].content += delta
            await scrollToBottom()
          }
        } catch (e) {
          if (!(e instanceof SyntaxError)) throw e
        }
      }
    }

    if (!messages.value[assistantIdx].content) {
      messages.value[assistantIdx].content = t('aiChat.emptyReply')
    }
    saveHistory()
    // 渲染 Mermaid 图表
    await renderMermaidBlocks()
  } catch (error) {
    console.error('AI 流式对话失败:', error)
    if (messages.value[assistantIdx] && !messages.value[assistantIdx].content) {
      messages.value.splice(assistantIdx, 1)
    }
    ElMessage.error(error.message || t('aiChat.networkError'))
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}

// ============ 清空对话 ============
const clearChat = () => {
  messages.value = []
  inputText.value = ''
  mermaidRenderedCache.clear()
  if (textareaRef.value) {
    textareaRef.value.style.height = 'auto'
  }
  try {
    localStorage.removeItem(getStorageKey())
  } catch (e) {}
}

// ============ 清除记忆 ============
const clearMemory = () => {
  ElMessageBox.confirm(
    t('aiChat.clearMemoryConfirm'),
    t('aiChat.clearMemoryTitle'),
    {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    }
  ).then(() => {
    messages.value = []
    inputText.value = ''
    mermaidRenderedCache.clear()
    if (textareaRef.value) {
      textareaRef.value.style.height = 'auto'
    }
    try {
      localStorage.removeItem(getStorageKey())
    } catch (e) {}
    ElMessage.success(t('aiChat.clearMemorySuccess'))
  }).catch(() => {
    // 取消
  })
}

// ============ 监听 ============
watch(() => messages.value.length, () => {
  scrollToBottom()
  // 当新消息添加完成后，渲染 Mermaid 图表
  nextTick(() => renderMermaidBlocks())
})

watch(() => messages.value, () => {
  // 当消息内容更新时（流式追加），也尝试渲染 Mermaid
  renderMermaidBlocks()
}, { deep: true })

onMounted(async () => {
  await fetchEnabledConfigs()
  fetchModelName()
  fetchSuggestions()
  loadHistory()
  if (messages.value.length > 0) {
    await scrollToBottom()
    await renderMermaidBlocks()
  }
})

onBeforeUnmount(() => {
  if (messages.value.length > 0) {
    saveHistory()
  }
})
</script>

<style scoped>
.ai-chat-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  max-width: 800px;
  margin: 0 auto;
}

/* 头部 */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 10px;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
}

.chat-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #303133;
  flex-shrink: 0;
}

.chat-header-center {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  justify-content: flex-start;
  padding-left: 12px;
  min-width: 0;
}

.chat-header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.header-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.chat-header-left h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
}

.ai-selector-wrapper,
.ai-model-selector-wrapper {
  position: relative;
}

.ai-config-select,
.ai-model-select {
  width: 170px;
}

.ai-model-select {
  margin-left: 0;
}

.ai-config-select :deep(.el-input__wrapper),
.ai-model-select :deep(.el-input__wrapper) {
  border-radius: 8px;
  box-shadow: 0 0 0 1px #e4e7ed inset;
  background: #f5f7fa;
  transition: all 0.2s ease;
}

.ai-config-select :deep(.el-input__wrapper.is-focus),
.ai-model-select :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #409eff inset !important;
  background: #fff;
}

.ai-config-select:hover :deep(.el-input__wrapper),
.ai-model-select:hover :deep(.el-input__wrapper) {
  background: #fff;
  box-shadow: 0 0 0 1px #d0d7de inset;
}

.ai-config-select :deep(.el-input__inner),
.ai-model-select :deep(.el-input__inner) {
  font-weight: 500;
  color: #303133;
}

.ai-config-select :deep(.el-input__prefix-inner),
.ai-model-select :deep(.el-input__prefix-inner) {
  color: #909399;
}

.select-prefix-icon {
  margin-right: 2px;
}

.ai-config-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
}

.ai-config-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.ai-config-default-tag {
  flex-shrink: 0;
  height: 18px;
  padding: 0 6px;
  font-size: 11px;
}

/* 消息区域 */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px 0;
  scroll-behavior: smooth;
}

.chat-messages::-webkit-scrollbar {
  width: 5px;
}

.chat-messages::-webkit-scrollbar-thumb {
  background: #dcdfe6;
  border-radius: 3px;
}

/* 空状态 */
.chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 280px;
  text-align: center;
}

.empty-icon {
  color: #a0a4b0;
  margin-bottom: 16px;
  opacity: 0.5;
}

.chat-empty h3 {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.chat-empty p {
  margin: 0 0 20px;
  font-size: 13px;
  color: #909399;
  max-width: 360px;
  line-height: 1.6;
}

.suggestion-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  max-width: 520px;
}

.suggestion-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.suggestion-chip:hover {
  background: #ecf5ff;
  border-color: #b3d8ff;
  color: #409EFF;
}

/* 消息项 */
.chat-message {
  display: flex;
  gap: 10px;
  padding: 10px 16px;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

.message-assistant {
  background: #f7f8fa;
  border-radius: 10px;
  margin: 2px 0;
}

.message-avatar {
  flex-shrink: 0;
  padding-top: 2px;
}

.ai-avatar {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.message-body {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.message-content {
  font-size: 14px;
  line-height: 1.75;
  color: #303133;
  word-break: break-word;
}

/* Markdown 样式 */
.message-content :deep(h1),
.message-content :deep(h2),
.message-content :deep(h3),
.message-content :deep(h4) {
  margin: 14px 0 6px;
  font-weight: 600;
  line-height: 1.4;
}

.message-content :deep(h1) { font-size: 19px; }
.message-content :deep(h2) { font-size: 16px; }
.message-content :deep(h3) { font-size: 14px; }

.message-content :deep(p) {
  margin: 4px 0;
}

.message-content :deep(p:first-child) {
  margin-top: 0;
}

.message-content :deep(p:last-child) {
  margin-bottom: 0;
}

.message-content :deep(ul),
.message-content :deep(ol) {
  padding-left: 20px;
  margin: 4px 0;
}

.message-content :deep(li) {
  margin: 2px 0;
}

.message-content :deep(code) {
  background: #e8eaed;
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 13px;
  font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
  color: #d63384;
}

/* 覆盖 code-block-wrapper 内的 inline code 样式 */
.message-content :deep(.code-block-body code) {
  background: none;
  color: inherit;
  padding: 0;
  font-size: 13px;
  font-family: inherit;
}

.message-content :deep(blockquote) {
  border-left: 3px solid #667eea;
  padding: 2px 0 2px 12px;
  margin: 6px 0;
  color: #606266;
  background: rgba(102, 126, 234, 0.04);
  border-radius: 0 4px 4px 0;
}

.message-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 6px 0;
  font-size: 13px;
}

.message-content :deep(table th),
.message-content :deep(table td) {
  border: 1px solid #e4e7ed;
  padding: 6px 12px;
  text-align: left;
}

.message-content :deep(table th) {
  background: #f5f7fa;
  font-weight: 600;
}

.message-content :deep(hr) {
  border: none;
  border-top: 1px solid #e4e7ed;
  margin: 12px 0;
}

.message-content :deep(a) {
  color: #409EFF;
  text-decoration: none;
}

.message-content :deep(a:hover) {
  text-decoration: underline;
}

/* ============ 代码块样式（含行号 + 复制按钮） ============ */
.message-content :deep(.code-block-wrapper) {
  margin: 10px 0;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e4e7ed;
}

.message-content :deep(.code-block-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: #f0f2f5;
  border-bottom: 1px solid #e4e7ed;
  font-size: 12px;
}

.message-content :deep(.code-lang-label) {
  color: #909399;
  font-weight: 500;
  text-transform: lowercase;
}

.message-content :deep(.code-copy-btn) {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 3px 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  color: #606266;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  line-height: 1;
}

.message-content :deep(.code-copy-btn:hover) {
  border-color: #409EFF;
  color: #409EFF;
}

.message-content :deep(.code-copy-btn:active) {
  background: #ecf5ff;
}

.message-content :deep(.code-block-body) {
  display: flex;
  background: #22272e;
}

.message-content :deep(.code-line-numbers) {
  display: flex;
  flex-direction: column;
  padding: 14px 0;
  background: #1c2128;
  color: #768390;
  font-size: 13px;
  font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
  line-height: 1.6;
  text-align: right;
  user-select: none;
  flex-shrink: 0;
  min-width: 40px;
}

.message-content :deep(.code-line-numbers span) {
  padding: 0 10px;
}

.message-content :deep(.code-pre) {
  margin: 0;
  padding: 14px 16px;
  overflow-x: auto;
  flex: 1;
  min-width: 0;
}

.message-content :deep(.code-pre code) {
  background: none;
  color: #adbac7;
  padding: 0;
  font-size: 13px;
  font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
  line-height: 1.6;
  white-space: pre;
}

/* ============ Mermaid 图表样式 ============ */
.message-content :deep(.mermaid-rendered) {
  margin: 10px 0;
  padding: 16px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow-x: auto;
  text-align: center;
}

.message-content :deep(.mermaid-rendered svg) {
  max-width: 100%;
  height: auto;
}

.message-content :deep(.mermaid-block) {
  display: none;
}

.message-content :deep(.mermaid-error) {
  display: block;
  padding: 14px 16px;
  background: #fef0f0;
  border: 1px solid #fde2e2;
  border-radius: 8px;
  margin: 10px 0;
  font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
  font-size: 13px;
  color: #f56c6c;
  white-space: pre-wrap;
  overflow-x: auto;
}

.message-content :deep(.mermaid-error-hint) {
  color: #e6a23c;
  font-size: 12px;
  padding: 4px 0;
  font-style: italic;
}

.message-actions {
  margin-top: 6px;
  opacity: 0;
  transition: opacity 0.2s;
}

.chat-message:hover .message-actions {
  opacity: 1;
}

/* 打字动画 */
.typing-indicator {
  display: flex;
  gap: 5px;
  padding: 6px 0;
}

.typing-indicator span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #b0b8c8;
  animation: typing 1.4s infinite ease-in-out;
}

.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }

@keyframes typing {
  0%, 80%, 100% { transform: scale(0.5); opacity: 0.3; }
  40% { transform: scale(1); opacity: 1; }
}

/* 输入区域 */
.chat-input-area {
  flex-shrink: 0;
  padding: 10px 0 0;
  border-top: 1px solid #ebeef5;
}

.chat-input-wrapper {
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  padding: 10px 12px;
  transition: all 0.2s ease;
}

.chat-input-wrapper:focus-within {
  border-color: #409EFF;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.08);
}

.chat-textarea {
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  line-height: 1.6;
  color: #303133;
  resize: none;
  font-family: inherit;
  overflow-y: auto;
  min-height: 24px;
}

.chat-textarea::placeholder {
  color: #c0c4cc;
}

.chat-textarea:disabled {
  opacity: 0.6;
}

.chat-input-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 6px;
  margin-top: 2px;
  border-top: 1px solid transparent;
}

.chat-input-wrapper:focus-within .chat-input-footer {
  border-top-color: #ebeef5;
}

.input-hint {
  font-size: 11px;
  color: #c0c4cc;
}

/* 响应式 */
@media (max-width: 768px) {
  .ai-chat-page {
    max-width: none;
    height: 100%;
  }

  .chat-message {
    padding: 10px 12px;
  }

  .suggestion-chip {
    font-size: 12px;
    padding: 6px 12px;
  }

  .chat-header-right {
    gap: 2px;
  }

  .message-content :deep(.code-line-numbers) {
    min-width: 32px;
  }

  .message-content :deep(.code-pre) {
    padding: 14px 10px;
  }
}
</style>
