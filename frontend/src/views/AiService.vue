<!--
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
-->

<template>
  <div class="ai-service">
    <!-- 页面头部 - 渐变横幅 -->
    <div class="page-hero">
      <div class="hero-icon">
        <svg viewBox="0 0 48 48" width="48" height="48" fill="none">
          <defs>
            <linearGradient id="aisHeroGrad" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stop-color="#667eea"/>
              <stop offset="100%" stop-color="#764ba2"/>
            </linearGradient>
          </defs>
          <path d="M24 4L6 14v20L24 44l18-10V14L24 4z" stroke="url(#aisHeroGrad)" stroke-width="2.5" fill="none"/>
          <circle cx="24" cy="22" r="4" stroke="url(#aisHeroGrad)" stroke-width="2" fill="none"/>
          <path d="M16 30l8-4 8 4" stroke="url(#aisHeroGrad)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>
      <div class="hero-text">
        <h2>{{ $t('aiService.title') }}</h2>
        <p>{{ $t('aiService.description') }}</p>
      </div>
      <div class="hero-stat">
        <div class="hero-stat-value">{{ totalCalls }}</div>
        <div class="hero-stat-label">{{ $t('aiService.totalCalls') }}</div>
      </div>
    </div>

    <div class="content-wrapper" v-loading="pageLoading">
      <!-- 统计卡片 -->
      <el-row :gutter="16" class="stats-row">
        <el-col :xs="12" :sm="6" v-for="stat in statsCards" :key="stat.key">
          <div class="stat-card" :class="stat.color">
            <div class="stat-icon" v-html="stat.icon"></div>
            <div class="stat-info">
              <span class="stat-value">{{ stat.value }}</span>
              <span class="stat-label">{{ stat.label }}</span>
            </div>
          </div>
        </el-col>
      </el-row>

      <!-- Tab 切换 -->
      <el-card shadow="never" class="main-card">
        <el-tabs v-model="activeTab" @tab-change="onTabChange">
          <el-tab-pane :label="$t('aiService.providers')" name="providers">
            <ProvidersPanel ref="providersRef" />
          </el-tab-pane>
          <el-tab-pane :label="$t('aiService.models')" name="models">
            <ModelsPanel ref="modelsRef" @stats-changed="refreshStats" />
          </el-tab-pane>
          <el-tab-pane :label="$t('aiService.subscriptions')" name="subscriptions">
            <SubscriptionsPanel ref="subscriptionsRef" />
          </el-tab-pane>
          <el-tab-pane :label="$t('aiService.quotas')" name="quotas">
            <QuotasPanel ref="quotasRef" />
          </el-tab-pane>
          <el-tab-pane :label="$t('aiService.apiKeys')" name="apiKeys">
            <ApiKeysPanel ref="apiKeysRef" />
          </el-tab-pane>
          <el-tab-pane :label="$t('aiService.usageLogs')" name="usageLogs">
            <UsageLogsPanel ref="usageLogsRef" />
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, markRaw } from 'vue'
import { useI18n } from 'vue-i18n'
import { getAiStatistics } from '@/api/aiService'
import ProvidersPanel from './aiService/ProvidersPanel.vue'
import ModelsPanel from './aiService/ModelsPanel.vue'
import SubscriptionsPanel from './aiService/SubscriptionsPanel.vue'
import QuotasPanel from './aiService/QuotasPanel.vue'
import ApiKeysPanel from './aiService/ApiKeysPanel.vue'
import UsageLogsPanel from './aiService/UsageLogsPanel.vue'

const { t } = useI18n()

const activeTab = ref('providers')
const pageLoading = ref(false)
const totalCalls = ref(0)

// 子面板引用
const providersRef = ref(null)
const modelsRef = ref(null)
const subscriptionsRef = ref(null)
const quotasRef = ref(null)
const apiKeysRef = ref(null)
const usageLogsRef = ref(null)

const panelRefs = {
  providers: providersRef,
  models: modelsRef,
  subscriptions: subscriptionsRef,
  quotas: quotasRef,
  apiKeys: apiKeysRef,
  usageLogs: usageLogsRef
}

function onTabChange(name) {
  const panel = panelRefs[name]
  if (panel?.value?.loadData) {
    panel.value.loadData()
  }
}

// 统计
const statsCards = ref([
  { key: 'models', value: 0, label: '', color: 'purple', icon: '<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/></svg>' },
  { key: 'subscriptions', value: 0, label: '', color: 'blue', icon: '<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>' },
  { key: 'apiKeys', value: 0, label: '', color: 'green', icon: '<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4"/></svg>' },
  { key: 'todayCalls', value: 0, label: '', color: 'orange', icon: '<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.5"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>' }
])

async function loadStats() {
  try {
    const res = await getAiStatistics()
    if (res.code === 200 && res.data) {
      const d = res.data
      statsCards.value[0].value = d.totalModels || 0
      statsCards.value[1].value = d.totalSubscriptions || 0
      statsCards.value[2].value = d.totalApiKeys || 0
      statsCards.value[3].value = d.todayCalls || 0
      totalCalls.value = d.totalCalls || 0
      statsCards.value[0].label = t('aiService.totalModels')
      statsCards.value[1].label = t('aiService.totalSubscriptions')
      statsCards.value[2].label = t('aiService.totalApiKeys')
      statsCards.value[3].label = t('aiService.todayCalls')
    }
  } catch (e) {
    console.error('获取统计失败', e)
  }
}

// 定时刷新
let statsTimer = null
const STATS_REFRESH_INTERVAL = 30000 // 30 秒

function startStatsTimer() {
  stopStatsTimer()
  statsTimer = setInterval(() => {
    loadStats()
  }, STATS_REFRESH_INTERVAL)
}

function stopStatsTimer() {
  if (statsTimer) {
    clearInterval(statsTimer)
    statsTimer = null
  }
}

// 暴露给子组件调用，用于新增/删除模型后同步刷新
function refreshStats() {
  loadStats()
}

onMounted(async () => {
  pageLoading.value = true
  await loadStats()
  pageLoading.value = false
  startStatsTimer()
})

onUnmounted(() => {
  stopStatsTimer()
})
</script>

<style scoped>
.ai-service {
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

.hero-text { z-index: 1; flex: 1; }

.hero-stat {
  z-index: 1;
  text-align: right;
  padding: 10px 20px;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 12px;
  backdrop-filter: blur(4px);
  min-width: 120px;
}

.hero-stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #fff;
  line-height: 1.2;
}

.hero-stat-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.78);
  margin-top: 2px;
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

/* ========== 统计卡片 ========== */
.stats-row {
  margin-bottom: 0;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #ebeef5;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
  transition: all 0.2s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0,0,0,0.08);
}

.stat-icon {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  flex-shrink: 0;
}

.stat-card.purple .stat-icon { background: #f3f0ff; color: #7c3aed; }
.stat-card.blue .stat-icon { background: #eff6ff; color: #3b82f6; }
.stat-card.green .stat-icon { background: #ecfdf5; color: #10b981; }
.stat-card.orange .stat-icon { background: #fff7ed; color: #f97316; }

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

/* ========== 主卡片 ========== */
.main-card {
  border-radius: 12px;
}

.main-card :deep(.el-card__body) {
  padding: 0 20px 20px;
}

.main-card :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.main-card :deep(.el-tabs__nav-wrap) {
  padding: 0 4px;
}
</style>
