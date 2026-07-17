<!--
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
-->

<template>
  <div class="welcome-page">
    <!-- 背景装饰 -->
    <div class="bg-decor">
      <div class="bg-orb orb-1"></div>
      <div class="bg-orb orb-2"></div>
      <div class="bg-grid"></div>
    </div>

    <!-- 导航栏 -->
    <header class="welcome-header">
      <div class="header-inner">
        <div class="header-brand">
          <svg class="brand-icon" viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
            <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
            <line x1="12" y1="22.08" x2="12" y2="12"/>
          </svg>
          <span class="brand-name">API Server</span>
        </div>
        <div class="header-actions">
          <a class="header-nav-link" @click="goChangelog">
            <svg class="nav-link-icon" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="10"/>
              <polyline points="12 6 12 12 16 14"/>
            </svg>
            <span>{{ $t('welcome.ms_changelog') }}</span>
          </a>
          <el-select v-model="currentLocale" size="small" @change="switchLocale" class="locale-select" popper-class="dark-locale-popper">
            <el-option label="中文" value="zh-CN" />
            <el-option label="English" value="en-US" />
            <el-option label="日本語" value="ja-JP" />
          </el-select>
        </div>
      </div>
    </header>

    <!-- 主体 -->
    <main class="welcome-main">
      <!-- Hero 区域 -->
      <section class="hero-section">
        <div class="hero-badge">
          <span class="badge-dot"></span>
          {{ welcomeBadge }}
        </div>
        <h1 class="hero-title">{{ $t('welcome.ms_title') }}</h1>
        <p class="hero-subtitle">{{ $t('welcome.ms_subtitle') }}</p>
        <p class="hero-desc">{{ $t('welcome.ms_description') }}</p>
        <div class="hero-actions">
          <el-button type="primary" size="large" round class="hero-cta" @click="goLogin">
            {{ $t('welcome.ms_cta') }}
            <el-icon class="cta-icon"><ArrowRight /></el-icon>
          </el-button>
        </div>
      </section>

      <!-- 核心数据 -->
      <section class="stats-section">
        <div ref="statsRef" class="stats-row">
          <div class="stat-item" v-for="(stat, idx) in stats" :key="idx">
            <span class="stat-number" :ref="el => statRefs[idx] = el">
              <span class="stat-count">{{ animatedCounts[idx] }}</span>
              <span class="stat-suffix">{{ stat.suffix }}</span>
            </span>
            <span class="stat-label">{{ stat.label }}</span>
          </div>
        </div>
      </section>

      <!-- 特性卡片 9 个 -->
      <section class="features-section">
        <div class="section-header">
          <h2 class="section-title">{{ $t('welcome.ms_featuresTitle') }}</h2>
          <p class="section-subtitle">{{ $t('welcome.ms_featuresSubtitle') }}</p>
        </div>
        <div class="features-grid">
          <div class="feature-card" v-for="(feature, idx) in features" :key="idx"
               :style="{ '--delay': idx * 0.05 + 's', '--i': idx }">
            <div class="feature-icon" :style="{ background: feature.bg }">
              <el-icon :size="20">
                <component :is="feature.icon" />
              </el-icon>
            </div>
            <h3 class="feature-title">{{ $t(feature.titleKey) }}</h3>
            <p class="feature-desc">{{ $t(feature.descKey, feature.descParams || {}) }}</p>
            <div class="feature-glow" :style="{ background: feature.bg }"></div>
          </div>
        </div>
      </section>

      <!-- 快速上手流程 -->
      <section class="workflow-section">
        <div class="section-header">
          <h2 class="section-title">{{ $t('welcome.ms_workflowTitle') }}</h2>
        </div>
        <div class="workflow-steps">
          <div class="workflow-step" v-for="(step, idx) in workflowSteps" :key="idx">
            <div class="step-number">{{ idx + 1 }}</div>
            <div class="step-line" v-if="idx < workflowSteps.length - 1">
              <svg viewBox="0 0 120 24" width="120" height="24">
                <line x1="0" y1="12" x2="100" y2="12" stroke="rgba(102,126,234,0.3)" stroke-width="1.5" stroke-dasharray="4 4"/>
                <polyline points="100,6 112,12 100,18" fill="none" stroke="rgba(102,126,234,0.5)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </div>
            <div class="step-content">
              <h4 class="step-title">{{ step.title }}</h4>
              <p class="step-desc">{{ step.desc }}</p>
            </div>
          </div>
        </div>
      </section>

      <!-- AI 能力矩阵 -->
      <section class="ai-section">
        <div class="section-header">
          <h2 class="section-title">
            <svg class="ai-sparkle" viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 2l1.5 5.5L19 9l-5.5 1.5L12 16l-1.5-5.5L5 9l5.5-1.5z"/>
              <path d="M18 16l.75 2.25L21 19l-2.25.75L18 22l-.75-2.25L15 19l2.25-.75z"/>
              <path d="M6 18l.5 1.5L8 20l-1.5.5L6 22l-.5-1.5L4 20l1.5-.5z"/>
            </svg>
            {{ $t('welcome.ms_aiTitle') }}
          </h2>
          <p class="section-subtitle ai-subtitle">{{ $t('welcome.ms_aiDesc') }}</p>
        </div>
        <div class="ai-caps-grid">
          <div class="ai-cap-card" v-for="(cap, idx) in aiCaps" :key="idx" :style="{ '--idx': idx }">
            <div class="ai-cap-icon">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
            </div>
            <span class="ai-cap-text">{{ cap }}</span>
          </div>
        </div>
      </section>
    </main>

    <!-- 底部 -->
    <footer class="welcome-footer">
      <div class="footer-inner">
        <p class="footer-copy">{{ $t('welcome.ms_footer') }}</p>
        <div class="footer-links">
          <a class="footer-link" @click="goChangelog">{{ $t('welcome.ms_changelog') }}</a>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import request from '@/utils/request'
import {
  ArrowRight,
  Connection,
  EditPen,
  TrendCharts,
  Upload,
  Document,
  UserFilled,
  ChatDotSquare,
  MagicStick,
  Files,
  DataAnalysis,
  DocumentCopy,
  Monitor
} from '@element-plus/icons-vue'

const { t, locale } = useI18n()
const router = useRouter()

const currentLocale = ref(locale.value)
const appVersion = ref('')
const statsRef = ref(null)
const statRefs = ref([])
const animatedCounts = ref([0, 0, 0, 0])

let statsObserved = false

const switchLocale = (val) => {
  locale.value = val
  localStorage.setItem('locale', val)
}

const goLogin = () => router.push('/login')
const goChangelog = () => router.push('/changelog')

const fetchVersion = async () => {
  try {
    const response = await request.get('/system/version')
    if (response.code === 200 && response.data) {
      appVersion.value = response.data.version || ''
    }
  } catch (error) {
    console.error('获取版本号失败:', error)
  }
}

const welcomeBadge = computed(() => {
  const ver = appVersion.value ? 'v' + appVersion.value : ''
  const desc = t('welcome.ms_badge')
  return ver ? ver + ' · ' + desc : desc
})

// ========== 核心数据 ==========
const stats = computed(() => [
  { value: 9, suffix: '', label: t('welcome.ms_statsItem1') },
  { value: 30, suffix: '+', label: t('welcome.ms_statsItem2') },
  { value: 3, suffix: '', label: t('welcome.ms_statsItem3') },
  { value: 12, suffix: '+', label: t('welcome.ms_statsItem4') },
])

const animateStats = () => {
  const targets = stats.value.map(s => s.value)
  const duration = 1600
  const startTime = performance.now()

  const tick = (now) => {
    const elapsed = now - startTime
    const progress = Math.min(elapsed / duration, 1)
    // easeOutCubic
    const eased = 1 - Math.pow(1 - progress, 3)
    animatedCounts.value = targets.map(t => Math.round(t * eased))
    if (progress < 1) {
      requestAnimationFrame(tick)
    }
  }
  requestAnimationFrame(tick)
}

// ========== 特性卡片 ==========
const features = [
  {
    icon: Connection, titleKey: 'welcome.ms_feature1Title', descKey: 'welcome.ms_feature1Desc',
    bg: 'linear-gradient(135deg, #667eea, #764ba2)'
  },
  {
    icon: EditPen, titleKey: 'welcome.ms_feature2Title', descKey: 'welcome.ms_feature2Desc',
    bg: 'linear-gradient(135deg, #f093fb, #f5576c)'
  },
  {
    icon: MagicStick, titleKey: 'welcome.ms_feature3Title', descKey: 'welcome.ms_feature3Desc',
    descParams: { open: '{{', close: '}}' },
    bg: 'linear-gradient(135deg, #f6d365, #fda085)'
  },
  {
    icon: Upload, titleKey: 'welcome.ms_feature4Title', descKey: 'welcome.ms_feature4Desc',
    bg: 'linear-gradient(135deg, #4facfe, #00f2fe)'
  },
  {
    icon: DocumentCopy, titleKey: 'welcome.ms_feature5Title', descKey: 'welcome.ms_feature5Desc',
    bg: 'linear-gradient(135deg, #43e97b, #38f9d7)'
  },
  {
    icon: Files, titleKey: 'welcome.ms_feature6Title', descKey: 'welcome.ms_feature6Desc',
    bg: 'linear-gradient(135deg, #a18cd1, #fbc2eb)'
  },
  {
    icon: UserFilled, titleKey: 'welcome.ms_feature7Title', descKey: 'welcome.ms_feature7Desc',
    bg: 'linear-gradient(135deg, #ff9a9e, #fecfef)'
  },
  {
    icon: TrendCharts, titleKey: 'welcome.ms_feature8Title', descKey: 'welcome.ms_feature8Desc',
    bg: 'linear-gradient(135deg, #89f7fe, #66a6ff)'
  },
  {
    icon: DataAnalysis, titleKey: 'welcome.ms_feature9Title', descKey: 'welcome.ms_feature9Desc',
    bg: 'linear-gradient(135deg, #fa709a, #fee140)'
  },
]


// ========== 快速上手 ==========
const workflowSteps = computed(() => [
  { title: t('welcome.ms_workflow1Title'), desc: t('welcome.ms_workflow1Desc') },
  { title: t('welcome.ms_workflow2Title'), desc: t('welcome.ms_workflow2Desc') },
  { title: t('welcome.ms_workflow3Title'), desc: t('welcome.ms_workflow3Desc') },
  { title: t('welcome.ms_workflow4Title'), desc: t('welcome.ms_workflow4Desc') },
])

// ========== AI 能力 ==========
const aiCaps = computed(() => [
  t('welcome.ms_aiCap1'), t('welcome.ms_aiCap2'), t('welcome.ms_aiCap3'), t('welcome.ms_aiCap4'),
  t('welcome.ms_aiCap5'), t('welcome.ms_aiCap6'), t('welcome.ms_aiCap7'), t('welcome.ms_aiCap8'),
])

// ========== 生命周期 ==========
onMounted(async () => {
  await fetchVersion()
  await nextTick()

  // IntersectionObserver for stats counter
  if (statsRef.value) {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach(entry => {
          if (entry.isIntersecting && !statsObserved) {
            statsObserved = true
            animateStats()
            observer.disconnect()
          }
        })
      },
      { threshold: 0.3 }
    )
    observer.observe(statsRef.value)
  }
})


</script>

<style scoped>
/* ==================== 基础布局 ==================== */
.welcome-page {
  min-height: 100vh;
  min-height: 100dvh;
  background: linear-gradient(170deg, #0a0a1a 0%, #12122e 25%, #1a1a3e 50%, #16213e 75%, #0f3460 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  overflow-x: hidden;
  position: relative;
}

/* ==================== 背景装饰 ==================== */
.bg-decor {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.08;
  will-change: transform;
}

.orb-1 {
  width: 500px; height: 500px;
  background: radial-gradient(circle, #667eea, transparent 70%);
  top: -150px; right: -100px;
  animation: orbFloat 20s ease-in-out infinite;
}

.orb-2 {
  width: 400px; height: 400px;
  background: radial-gradient(circle, #764ba2, transparent 70%);
  bottom: 10%; left: -120px;
  animation: orbFloat 24s ease-in-out infinite reverse;
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255,255,255,0.015) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.015) 1px, transparent 1px);
  background-size: 64px 64px;
}

@keyframes orbFloat {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(30px, -20px); }
}

/* ==================== 导航栏 ==================== */
.welcome-header {
  position: sticky;
  top: 0;
  z-index: 100;
  backdrop-filter: blur(8px) saturate(180%);
  -webkit-backdrop-filter: blur(8px) saturate(180%);
  background: rgba(10, 10, 26, 0.72);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.header-inner {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 32px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-icon {
  color: #667eea;
  filter: drop-shadow(0 0 6px rgba(102, 126, 234, 0.3));
}

.brand-name {
  font-size: 20px;
  font-weight: 700;
  background: linear-gradient(135deg, #667eea, #e83e8c, #20c997);
  background-size: 200% 200%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: brandShift 10s ease infinite;
}

@keyframes brandShift {
  0%, 100% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 20px;
}

.header-nav-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
  text-decoration: none;
  cursor: pointer;
  white-space: nowrap;
  transition: color 0.3s;
  padding: 4px 0;
  border-bottom: 2px solid transparent;
}

.header-nav-link:hover {
  color: rgba(255, 255, 255, 0.9);
  border-bottom-color: rgba(102, 126, 234, 0.5);
}

.nav-link-icon {
  flex-shrink: 0;
  opacity: 0.8;
}

.header-nav-link:hover .nav-link-icon { opacity: 1; }

.locale-select { width: 110px; }

.locale-select :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.12);
  box-shadow: none;
  transition: background 0.25s, border-color 0.25s;
}

.locale-select :deep(.el-input__wrapper:hover),
.locale-select :deep(.el-input__wrapper.is-focus) {
  background: rgba(255, 255, 255, 0.12);
  border-color: rgba(102, 126, 234, 0.4);
}

.locale-select :deep(.el-input__inner) {
  color: rgba(255, 255, 255, 0.85);
  font-size: 13px;
  text-align: center;
  cursor: pointer;
}

.locale-select :deep(.el-input__suffix),
.locale-select :deep(.el-input .el-input__icon) {
  color: rgba(255, 255, 255, 0.55);
}

/* ==================== 主体 ==================== */
.welcome-main {
  flex: 1;
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  padding-bottom: 40px;
}

/* ==================== Hero ==================== */
.hero-section {
  text-align: center;
  padding: clamp(56px, 9vh, 100px) 24px clamp(32px, 5vh, 56px);
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 22px;
  margin-bottom: 32px;
  border-radius: 24px;
  font-size: 13px;
  font-weight: 600;
  color: #a78bfa;
  background: rgba(167, 139, 250, 0.1);
  border: 1px solid rgba(167, 139, 250, 0.2);
  letter-spacing: 0.3px;
  box-shadow: 0 0 20px rgba(167, 139, 250, 0.08);
}

.badge-dot {
  width: 7px; height: 7px;
  border-radius: 50%;
  background: #a78bfa;
  box-shadow: 0 0 8px #a78bfa;
  animation: pulse 3s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; box-shadow: 0 0 8px #a78bfa; }
  50% { opacity: 0.35; box-shadow: 0 0 2px #a78bfa; }
}

.hero-title {
  font-size: clamp(38px, 6.5vw, 64px);
  font-weight: 800;
  line-height: 1.12;
  margin: 0 0 16px;
  background: linear-gradient(135deg, #fff 0%, #c4b5fd 30%, #818cf8 60%, #fff 85%);
  background-size: 200% 200%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: heroShift 12s ease infinite;
}

@keyframes heroShift {
  0%, 100% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
}

.hero-subtitle {
  font-size: clamp(18px, 2.6vw, 25px);
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
  margin: 0 0 14px;
}

.hero-desc {
  font-size: clamp(14px, 1.7vw, 15px);
  color: rgba(255, 255, 255, 0.45);
  line-height: 1.8;
  margin: 0 auto 44px;
  max-width: 640px;
}

.hero-actions {
  display: flex;
  justify-content: center;
  gap: 14px;
  flex-wrap: wrap;
}

.hero-cta {
  padding: 15px 44px !important;
  font-size: 16px !important;
  font-weight: 600 !important;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%) !important;
  border: none !important;
  box-shadow: 0 4px 28px rgba(102, 126, 234, 0.4);
  transition: transform 0.35s cubic-bezier(0.4, 0, 0.2, 1),
              box-shadow 0.35s ease !important;
}

.hero-cta:hover {
  transform: translateY(-3px) !important;
  box-shadow: 0 8px 40px rgba(102, 126, 234, 0.55) !important;
}

.cta-icon { margin-left: 6px; }

/* ==================== 核心数据 ==================== */
.stats-section {
  padding: 0 24px;
  max-width: 900px;
  margin: 0 auto 24px;
  width: 100%;
  box-sizing: border-box;
}

.stats-row {
  display: flex;
  justify-content: center;
  gap: 40px;
  flex-wrap: wrap;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 20px 28px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.025);
  border: 1px solid rgba(255, 255, 255, 0.05);
  transition: border-color 0.3s, background 0.3s;
  min-width: 120px;
}

.stat-item:hover {
  border-color: rgba(102, 126, 234, 0.2);
  background: rgba(102, 126, 234, 0.04);
}

.stat-number {
  font-size: clamp(32px, 4vw, 42px);
  font-weight: 800;
  background: linear-gradient(135deg, #667eea, #c4b5fd);
  background-size: 200%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  line-height: 1.1;
}

.stat-suffix {
  font-size: 0.6em;
  background: linear-gradient(135deg, #667eea, #a78bfa);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.stat-label {
  font-size: 13px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.45);
  letter-spacing: 0.3px;
}

/* ==================== 通用 Section Header ==================== */
.section-header {
  text-align: center;
  margin-bottom: 44px;
}

.section-title {
  font-size: clamp(24px, 3.5vw, 30px);
  font-weight: 700;
  margin: 0 0 10px;
  color: rgba(255, 255, 255, 0.9);
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.section-subtitle {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.38);
  margin: 0;
  line-height: 1.6;
}

/* ==================== 特性卡片 ==================== */
.features-section {
  padding: 48px 24px 32px;
  max-width: 1280px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.feature-card {
  position: relative;
  background: rgba(255, 255, 255, 0.028);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 18px;
  padding: 26px 18px;
  text-align: center;
  overflow: hidden;
  transition: transform 0.4s cubic-bezier(0.4, 0, 0.2, 1),
              border-color 0.4s ease,
              background 0.4s ease,
              box-shadow 0.4s ease;
  animation: fadeInUp 0.6s ease backwards;
  animation-delay: var(--delay, 0s);
}

.feature-card .feature-glow {
  position: absolute;
  bottom: -40px;
  left: 50%;
  transform: translateX(-50%);
  width: 120px;
  height: 60px;
  border-radius: 50%;
  filter: blur(30px);
  opacity: 0;
  transition: opacity 0.5s ease;
}

.feature-card:hover .feature-glow {
  opacity: 0.25;
}

.feature-card:hover {
  transform: translateY(-8px);
  border-color: rgba(120, 150, 255, 0.3);
  background: rgba(102, 126, 234, 0.06);
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.3),
              0 0 30px rgba(102, 126, 234, 0.06);
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(24px); }
  to { opacity: 1; transform: translateY(0); }
}

.feature-icon {
  width: 44px; height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 14px;
  color: #fff;
  transition: transform 0.35s cubic-bezier(0.4, 0, 0.2, 1),
              box-shadow 0.35s ease;
  position: relative;
  z-index: 1;
}

.feature-card:hover .feature-icon {
  transform: scale(1.1);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
}

.feature-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 6px;
  color: rgba(255, 255, 255, 0.9);
  position: relative;
  z-index: 1;
}

.feature-desc {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
  line-height: 1.6;
  margin: 0;
  position: relative;
  z-index: 1;
}

/* ==================== 技术栈 ==================== */
.tech-stack-section {
  padding: 32px 24px 48px;
  max-width: 1000px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}

.tech-badges {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 12px;
}

.tech-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 18px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.07);
  transition: transform 0.35s cubic-bezier(0.4, 0, 0.2, 1),
              background 0.35s cubic-bezier(0.4, 0, 0.2, 1),
              border-color 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: default;
  opacity: 0;
  animation: fadeInUp 0.4s ease forwards;
  animation-delay: calc(var(--idx, 0) * 0.06s);
}

.tech-badge:hover {
  background: rgba(102, 126, 234, 0.1);
  border-color: rgba(120, 150, 255, 0.35);
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.1);
}

.tech-name {
  font-size: 13px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.8);
}

.tech-ver {
  font-size: 11px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.35);
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.05);
}

/* ==================== 快速上手 ==================== */
.workflow-section {
  padding: 40px 24px 48px;
  max-width: 1100px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}

.workflow-steps {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  gap: 0;
  flex-wrap: wrap;
}

.workflow-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  flex: 1;
  min-width: 200px;
  max-width: 260px;
  padding: 0 16px;
  position: relative;
}

.step-number {
  width: 50px; height: 50px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 700;
  color: #fff;
  margin-bottom: 16px;
  position: relative;
  z-index: 2;
  box-shadow: 0 4px 20px rgba(102, 126, 234, 0.35);
  transition: transform 0.3s, box-shadow 0.3s;
}

.workflow-step:hover .step-number {
  transform: scale(1.08);
  box-shadow: 0 6px 28px rgba(102, 126, 234, 0.5);
}

.step-line {
  position: absolute;
  top: 25px;
  left: calc(50% + 35px);
  width: calc(100% - 70px);
  display: flex;
  align-items: center;
  z-index: 1;
}

.step-content { padding: 0 8px; }

.step-title {
  font-size: 16px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.88);
  margin: 0 0 6px;
}

.step-desc {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.4);
  line-height: 1.6;
  margin: 0;
}

/* ==================== AI 能力矩阵 ==================== */
.ai-section {
  padding: 40px 24px 56px;
  max-width: 1080px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}

.ai-subtitle {
  background: linear-gradient(135deg, rgba(246, 211, 101, 0.9), rgba(253, 160, 133, 0.9));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  font-weight: 500;
}

.ai-sparkle {
  color: #f6d365;
  flex-shrink: 0;
  filter: drop-shadow(0 0 10px rgba(246, 211, 101, 0.5));
  animation: sparklePulse 2s ease-in-out infinite;
}

@keyframes sparklePulse {
  0%, 100% { filter: drop-shadow(0 0 10px rgba(246, 211, 101, 0.5)); }
  50% { filter: drop-shadow(0 0 20px rgba(246, 211, 101, 0.8)); }
}

.ai-caps-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}

.ai-cap-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 14px;
  padding: 18px 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1),
              background 0.3s cubic-bezier(0.4, 0, 0.2, 1),
              border-color 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: default;
  opacity: 0;
  animation: fadeInUp 0.5s ease forwards;
  animation-delay: calc(var(--idx, 0) * 0.06s);
}

.ai-cap-card:hover {
  background: rgba(102, 126, 234, 0.08);
  border-color: rgba(246, 211, 101, 0.35);
  transform: translateY(-3px);
  box-shadow: 0 8px 28px rgba(246, 211, 101, 0.1);
}

.ai-cap-icon {
  width: 34px; height: 34px;
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(246, 211, 101, 0.22), rgba(253, 160, 133, 0.15));
  display: flex;
  align-items: center;
  justify-content: center;
  color: #f6d365;
  flex-shrink: 0;
}

.ai-cap-text {
  font-size: 13px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.78);
  line-height: 1.4;
}

/* ==================== 底部 ==================== */
.welcome-footer {
  position: relative;
  z-index: 1;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  padding: 20px 24px;
}

.footer-inner {
  max-width: 1280px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.footer-copy {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.28);
  margin: 0;
}

.footer-links { display: flex; gap: 16px; }

.footer-link {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.32);
  cursor: pointer;
  text-decoration: none;
  transition: color 0.3s;
}

.footer-link:hover { color: rgba(255, 255, 255, 0.6); }

/* ==================== 响应式 ==================== */
@media (max-width: 1200px) {
  .features-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 900px) {
  .features-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .ai-caps-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .stats-row { gap: 16px; }
  .stat-item { padding: 16px 20px; min-width: 100px; }
}

@media (max-width: 768px) {
  .header-inner { padding: 0 20px; }

  .features-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }

  .workflow-steps {
    flex-direction: column;
    align-items: center;
    gap: 20px;
  }

  .workflow-step {
    max-width: 100%;
    flex-direction: row;
    text-align: left;
    gap: 16px;
    padding: 0;
  }

  .step-number { margin-bottom: 0; flex-shrink: 0; }
  .step-line { display: none; }
  .step-content { padding: 0; }

  .ai-caps-grid { grid-template-columns: repeat(2, 1fr); }
  .hero-actions { flex-direction: column; align-items: center; }
  .hero-cta { width: 100%; max-width: 280px; justify-content: center; }
  .footer-inner { flex-direction: column; text-align: center; }

  .stats-row { gap: 14px; }
  .stat-item { flex: 1 1 40%; min-width: 130px; }
  .stat-number { font-size: 28px; }
}

@media (max-width: 480px) {
  .header-inner { padding: 0 14px; }
  .brand-name { font-size: 17px; }

  .features-grid {
    grid-template-columns: 1fr;
    max-width: 340px;
    margin: 0 auto;
  }

  .ai-caps-grid { grid-template-columns: 1fr; }
  .section-header { margin-bottom: 32px; }
  .hero-section { padding: 44px 20px 36px; }
  .stats-row { gap: 10px; }
  .stat-item { padding: 14px 16px; min-width: 110px; }
  .stat-number { font-size: 26px; }
  .tech-badges { gap: 8px; }
  .tech-badge { padding: 6px 14px; }
}
</style>
