<!--
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
-->

<template>
  <div class="changelog-page">
    <!-- 背景装饰 -->
    <div class="bg-decoration">
      <div class="bg-glow bg-glow-1"></div>
      <div class="bg-glow bg-glow-2"></div>
      <div class="bg-glow bg-glow-3"></div>
    </div>

    <!-- 顶部导航栏 -->
    <header class="changelog-header">
      <div class="header-inner">
        <div class="header-brand" @click="goHome">
          <svg class="brand-icon" viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
            <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
            <line x1="12" y1="22.08" x2="12" y2="12"/>
          </svg>
          <span class="brand-name">API Server</span>
        </div>
        <div class="header-actions">
          <a class="header-nav-link" @click="goHome">
            <svg class="nav-link-icon" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
              <polyline points="9 22 9 12 15 12 15 22"/>
            </svg>
            <span>{{ $t('changelog.backHome') }}</span>
          </a>
          <el-select v-model="currentLocale" size="small" @change="switchLocale" class="locale-select" popper-class="dark-locale-popper">
            <el-option label="中文" value="zh-CN" />
            <el-option label="English" value="en-US" />
            <el-option label="日本語" value="ja-JP" />
          </el-select>
        </div>
      </div>
    </header>

    <!-- 主内容 -->
    <div class="changelog-body">
      <div class="changelog-container">
        <!-- 页面标题 -->
        <div class="page-hero">
          <div class="hero-icon">
            <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="10"/>
              <polyline points="12 6 12 12 16 14"/>
            </svg>
          </div>
          <h1 class="page-title">{{ $t('changelog.title') }}</h1>
          <p class="page-subtitle">{{ $t('changelog.subtitle') }}</p>
        </div>

        <!-- 加载状态 -->
        <div v-if="loading" class="loading-container">
          <el-skeleton :rows="12" animated />
        </div>

        <!-- 错误状态 -->
        <div v-else-if="error" class="error-container">
          <el-empty :description="$t('changelog.loadFailed')" :image-size="120" />
          <el-button type="primary" @click="fetchChangelog" style="margin-top: 16px;">
            {{ $t('common.refresh') }}
          </el-button>
        </div>

        <!-- Markdown 渲染内容 -->
        <div v-else class="changelog-content" v-html="renderedContent"></div>
      </div>
    </div>

    <!-- 底部 -->
    <footer class="changelog-footer">
      <p>{{ $t('welcome.footer') }}</p>
    </footer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { marked } from 'marked'

const { locale } = useI18n()
const router = useRouter()

const currentLocale = ref(locale.value)
const loading = ref(true)
const error = ref(false)
const renderedContent = ref('')

const switchLocale = (val) => {
  locale.value = val
  localStorage.setItem('locale', val)
}

const goHome = () => {
  router.push('/')
}

// 从根路径获取 CHANGELOG.md 内容
const fetchChangelog = async () => {
  loading.value = true
  error.value = false
  try {
    const response = await fetch('/CHANGELOG.md', { cache: 'no-cache' })
    if (!response.ok) throw new Error('Failed to fetch CHANGELOG.md')
    const content = await response.text()
    renderedContent.value = marked(content)
    loading.value = false
  } catch (err) {
    console.error('获取变更历史失败:', err)
    error.value = true
    loading.value = false
  }
}

onMounted(() => {
  fetchChangelog()
})
</script>

<style scoped>
.changelog-page {
  min-height: 100vh;
  background: linear-gradient(160deg, #0c0c1e 0%, #1a1a3e 30%, #16213e 60%, #0f3460 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow-x: hidden;
}

/* 背景装饰光晕 */
.bg-decoration {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

.bg-glow {
  position: absolute;
  border-radius: 50%;
  opacity: 0.12;
}

.bg-glow-1 {
  top: -120px;
  right: -80px;
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(102, 126, 234, 0.6) 0%, transparent 70%);
}

.bg-glow-2 {
  bottom: 10%;
  left: -100px;
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(118, 75, 162, 0.5) 0%, transparent 70%);
}

.bg-glow-3 {
  top: 50%;
  right: 10%;
  width: 350px;
  height: 350px;
  background: radial-gradient(circle, rgba(32, 201, 151, 0.3) 0%, transparent 70%);
}

/* 导航栏 */
.changelog-header {
  position: sticky;
  top: 0;
  z-index: 10;
  backdrop-filter: blur(12px);
  background: rgba(12, 12, 30, 0.75);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.header-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  transition: opacity 0.3s;
}

.header-brand:hover {
  opacity: 0.85;
}

.brand-icon {
  color: #667eea;
}

.brand-name {
  font-size: 20px;
  font-weight: 700;
  background: linear-gradient(135deg, #667eea, #e83e8c, #20c997);
  background-size: 200% 200%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: brandShift 5s ease infinite;
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

.header-nav-link:hover .nav-link-icon {
  opacity: 1;
}

/* 语言选择器 - 暗色主题 */
.locale-select {
  width: 110px;
}

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

.locale-select :deep(.el-input__inner::placeholder) {
  color: rgba(255, 255, 255, 0.4);
}

.locale-select :deep(.el-input__suffix) {
  color: rgba(255, 255, 255, 0.55);
}

.locale-select :deep(.el-input .el-input__icon) {
  color: rgba(255, 255, 255, 0.55);
}

/* 主体 */
.changelog-body {
  flex: 1;
  position: relative;
  z-index: 1;
  padding: 40px 24px 80px;
}

.changelog-container {
  max-width: 900px;
  margin: 0 auto;
}

/* 页面标题 Hero */
.page-hero {
  text-align: center;
  padding: 20px 0 48px;
}

.hero-icon {
  width: 80px;
  height: 80px;
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.2), rgba(32, 201, 151, 0.15));
  border: 1px solid rgba(102, 126, 234, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 24px;
  color: #667eea;
}

.page-title {
  font-size: 40px;
  font-weight: 800;
  margin: 0 0 12px;
  background: linear-gradient(135deg, #fff 0%, #c4b5fd 40%, #818cf8 70%, #fff 100%);
  background-size: 200% 200%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: heroShift 6s ease infinite;
}

@keyframes heroShift {
  0%, 100% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
}

.page-subtitle {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.45);
  margin: 0;
}

/* 加载 & 错误 */
.loading-container {
  padding: 40px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.error-container {
  text-align: center;
  padding: 60px 40px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.error-container :deep(.el-empty__description) {
  color: rgba(255, 255, 255, 0.4);
}

/* ========== Markdown 内容美化 ========== */
.changelog-content {
  padding: 40px 48px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 16px;
  backdrop-filter: blur(8px);
}

/* h1 */
.changelog-content :deep(h1) {
  font-size: 28px;
  font-weight: 700;
  margin: 40px 0 20px;
  padding-bottom: 12px;
  border-bottom: 2px solid rgba(102, 126, 234, 0.3);
  color: #fff;
}

.changelog-content :deep(h1:first-child) {
  margin-top: 0;
}

/* h2 - 版本号标题 */
.changelog-content :deep(h2) {
  font-size: 22px;
  font-weight: 700;
  margin: 36px 0 16px;
  padding: 10px 20px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.12), rgba(118, 75, 162, 0.08));
  border-left: 4px solid #667eea;
  border-radius: 0 8px 8px 0;
  color: #c4b5fd;
  display: inline-block;
}

/* h3 - 分类标题 */
.changelog-content :deep(h3) {
  font-size: 17px;
  font-weight: 600;
  margin: 20px 0 12px;
  color: rgba(255, 255, 255, 0.85);
  display: flex;
  align-items: center;
  gap: 8px;
}

.changelog-content :deep(h3::before) {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #667eea;
  flex-shrink: 0;
}

/* 段落 */
.changelog-content :deep(p) {
  margin: 10px 0;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.65);
}

/* 引用块 - 版本描述 */
.changelog-content :deep(blockquote) {
  border-left: 3px solid rgba(102, 126, 234, 0.5);
  padding: 12px 20px;
  margin: 12px 0 20px;
  background: rgba(102, 126, 234, 0.06);
  border-radius: 0 8px 8px 0;
  color: rgba(255, 255, 255, 0.55);
  font-style: italic;
}

/* 列表 */
.changelog-content :deep(ul),
.changelog-content :deep(ol) {
  margin: 12px 0;
  padding-left: 24px;
}

.changelog-content :deep(li) {
  margin: 8px 0;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.65);
}

/* 加粗 */
.changelog-content :deep(strong) {
  font-weight: 700;
  color: #c4b5fd;
}

/* 代码 */
.changelog-content :deep(code) {
  background: rgba(102, 126, 234, 0.15);
  padding: 2px 8px;
  border-radius: 4px;
  font-family: 'Fira Code', 'Courier New', monospace;
  font-size: 13px;
  color: #a78bfa;
}

.changelog-content :deep(pre) {
  background: rgba(0, 0, 0, 0.3);
  padding: 16px 20px;
  border-radius: 10px;
  overflow-x: auto;
  margin: 16px 0;
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.changelog-content :deep(pre code) {
  background: none;
  padding: 0;
  color: #abb2bf;
  font-size: 13px;
}

/* 分隔线 */
.changelog-content :deep(hr) {
  border: none;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  margin: 32px 0;
}

/* 链接 */
.changelog-content :deep(a) {
  color: #818cf8;
  text-decoration: none;
  transition: color 0.2s;
}

.changelog-content :deep(a:hover) {
  color: #a78bfa;
  text-decoration: underline;
}

/* 表格（如果有） */
.changelog-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 16px 0;
}

.changelog-content :deep(table th),
.changelog-content :deep(table td) {
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 10px 14px;
  text-align: left;
  color: rgba(255, 255, 255, 0.75);
}

.changelog-content :deep(table th) {
  background: rgba(102, 126, 234, 0.12);
  font-weight: 600;
}

.changelog-content :deep(table tr:hover) {
  background: rgba(255, 255, 255, 0.03);
}

/* 底部 */
.changelog-footer {
  text-align: center;
  padding: 24px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  position: relative;
  z-index: 1;
}

.changelog-footer p {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.25);
  margin: 0;
}

/* 响应式 */
@media (max-width: 768px) {
  .changelog-body {
    padding: 20px 16px 60px;
  }

  .changelog-content {
    padding: 24px 20px;
  }

  .page-title {
    font-size: 30px;
  }

  .changelog-content :deep(h1) {
    font-size: 22px;
  }

  .changelog-content :deep(h2) {
    font-size: 18px;
  }

  .header-actions .header-nav-link span {
    display: none;
  }

  .header-actions .nav-link-icon {
    margin-right: 0;
  }
}
</style>
