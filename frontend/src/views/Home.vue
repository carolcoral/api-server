<!--
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
-->

<template>
  <div class="home">
    <el-row :gutter="20">
      <el-col :xs="12" :sm="12" :md="12" :lg="6" :xl="colSpan">
        <el-card class="stat-card">
          <div class="stat-icon project-icon">
            <Folder :width="'1.5em'" :height="'1.5em'" />
          </div>
          <div class="stat-content">
            <h3>{{ $t('home.projectCount') }}</h3>
            <el-tooltip v-if="loading || stats.projectCount > 9999" :content="String(stats.projectCount)" placement="top" :disabled="loading">
              <p>{{ loading ? '-' : formatCount(stats.projectCount) }}</p>
            </el-tooltip>
            <p v-else>{{ stats.projectCount }}</p>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="12" :lg="6" :xl="colSpan">
        <el-card class="stat-card">
          <div class="stat-icon api-icon">
            <Connection :width="'1.5em'" :height="'1.5em'" />
          </div>
          <div class="stat-content">
            <h3>{{ $t('home.apiCount') }}</h3>
            <el-tooltip v-if="loading || stats.apiCount > 9999" :content="String(stats.apiCount)" placement="top" :disabled="loading">
              <p>{{ loading ? '-' : formatCount(stats.apiCount) }}</p>
            </el-tooltip>
            <p v-else>{{ stats.apiCount }}</p>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="12" :lg="6" :xl="colSpan">
        <el-card class="stat-card">
          <div class="stat-icon user-icon">
            <User :width="'1.5em'" :height="'1.5em'" />
          </div>
          <div class="stat-content">
            <h3>{{ $t('home.userCount') }}</h3>
            <el-tooltip v-if="loading || stats.userCount > 9999" :content="String(stats.userCount)" placement="top" :disabled="loading">
              <p>{{ loading ? '-' : formatCount(stats.userCount) }}</p>
            </el-tooltip>
            <p v-else>{{ stats.userCount }}</p>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="12" :lg="6" :xl="colSpan">
        <el-card class="stat-card">
          <div class="stat-icon request-icon">
            <Position :width="'1.5em'" :height="'1.5em'" />
          </div>
          <div class="stat-content">
            <h3>{{ $t('home.todayRequests') }}</h3>
            <el-tooltip v-if="loading || stats.requestCount > 9999" :content="String(stats.requestCount)" placement="top" :disabled="loading">
              <p>{{ loading ? '-' : formatCount(stats.requestCount) }}</p>
            </el-tooltip>
            <p v-else>{{ stats.requestCount }}</p>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="12" :lg="6" :xl="colSpan">
        <el-card class="stat-card">
          <div class="stat-icon total-request-icon">
            <DataLine :width="'1.5em'" :height="'1.5em'" />
          </div>
          <div class="stat-content">
            <h3>{{ $t('home.totalRequests') }}</h3>
            <el-tooltip v-if="loading || stats.totalRequestCount > 9999" :content="String(stats.totalRequestCount)" placement="top" :disabled="loading">
              <p>{{ loading ? '-' : formatCount(stats.totalRequestCount) }}</p>
            </el-tooltip>
            <p v-else>{{ stats.totalRequestCount }}</p>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header-row">
              <span>{{ $t('home.usageGuide') }}</span>
              <div class="card-header-actions">
                <el-button type="primary" link @click="showGuide = true">
                  <svg class="btn-svg-icon" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/>
                  </svg>
                  {{ $t('common.userGuide') || '使用说明' }}
                </el-button>
                <el-button type="primary" link @click="$router.push('/changelog')">
                  <el-icon><Clock /></el-icon>
                  {{ $t('home.viewChangelog') }}
                </el-button>
              </div>
            </div>
          </template>
          <div class="readme-content" v-if="readmeLoading">
            <el-skeleton :rows="8" animated />
          </div>
          <div class="readme-content" v-else-if="readmeError">
            <el-empty :description="$t('home.statsFailed')" :image-size="60" />
          </div>
          <div class="readme-content" v-else v-html="readmeRendered"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>{{ $t('home.systemAnnouncement') }}</span>
          </template>
          <div class="announcement" v-if="announcement">
            <div class="announcement-content" v-html="renderedContent"></div>
          </div>
          <div v-else class="no-announcement">
            <el-empty :description="$t('home.noAnnouncement')" :image-size="80" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 使用说明引导对话框 -->
    <GuideDialog v-model="showGuide" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useI18n } from 'vue-i18n'
import request from '@/utils/request'
import {
  Folder,
  Connection,
  User,
  Position,
  DataLine,
  Clock
} from '@element-plus/icons-vue'
import { marked } from 'marked'
import GuideDialog from '@/components/GuideDialog.vue'

const { t, locale } = useI18n()
const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)
const showGuide = ref(false)

// 统计数据
const stats = ref({
  projectCount: 0,
  apiCount: 0,
  userCount: 0,
  requestCount: 0,
  totalRequestCount: 0
})

// 系统公告
const announcement = ref(null)

const loading = ref(true)

// 计算每个统计项的宽度（当页面宽度大于750px时，5个统计项在一行显示）
const colSpan = computed(() => {
  // 获取页面宽度
  const width = window.innerWidth || 1920

  // 如果页面宽度大于750px，每个统计项占20%（5个*20%=100%），在一行显示
  if (width > 750) {
    return 24 / 5 // el-col 的 span 是 24 的比例，5个统计项每个占 4.8
  }

  // 否则使用默认的响应式布局
  return 12
})

// 渲染Markdown内容
const renderedContent = computed(() => {
  if (announcement.value && announcement.value.content) {
    return marked(announcement.value.content)
  }
  return ''
})

// 数字格式化：超过9999显示9999+
const formatCount = (count) => {
  if (count > 9999) return '9999+'
  return count
}

// README 使用说明
const readmeLoading = ref(true)
const readmeError = ref(false)
const readmeRendered = ref('')

// 获取 README 内容（根据语言选择中文或英文版本）
const fetchReadme = async () => {
  readmeLoading.value = true
  readmeError.value = false
  try {
    const readmeFile = locale.value === 'zh-CN' ? '/README.md' : '/README-US.md'
    const response = await fetch(readmeFile, { cache: 'no-cache' })
    if (!response.ok) throw new Error('Failed to fetch README')
    const content = await response.text()
    readmeRendered.value = marked(content)
    readmeLoading.value = false
  } catch (err) {
    console.error('获取使用说明失败:', err)
    readmeError.value = true
    readmeLoading.value = false
  }
}

// 页面加载时获取统计数据
onMounted(() => {
  fetchRealStats()
  fetchAnnouncement()
  fetchReadme()
})

// 获取真实统计数据
const fetchRealStats = async () => {
  loading.value = true
  try {
    const response = await request.get('/dashboard/stats?includeTodayRequests=true')
      if (response.code === 200) {
      stats.value = {
        projectCount: response.data.projectCount || 0,
        apiCount: response.data.apiCount || 0,
        userCount: response.data.userCount || 0,
        requestCount: response.data.requestCount || 0,
        totalRequestCount: response.data.totalRequestCount || 0
      }
    } else {
      ElMessage.error(t('home.statsFailed'))
      setDefaultStats()
    }
  } catch (error) {
    console.error('获取统计数据失败', error)
    ElMessage.error(t('home.statsFailed'))
    setDefaultStats()
  } finally {
    loading.value = false
  }
}

// 设置默认值
const setDefaultStats = () => {
  stats.value = {
    projectCount: 0,
    apiCount: 0,
    userCount: 0,
    requestCount: 0,
    totalRequestCount: 0
  }
}

// 获取系统公告
const fetchAnnouncement = async () => {
  try {
    const response = await request.get('/dashboard/announcement')
    if (response.code === 200 && response.data) {
      announcement.value = response.data
    }
  } catch (error) {
    console.error('获取系统公告失败', error)
  }
}
</script>

<style scoped>
.home {
  padding: 20px;
}

.stat-card {
  height: 120px;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: flex-start;
  padding: 0 20px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20px;
  font-size: 20px;
  color: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
  flex-shrink: 0;
  float: left;
}

.stat-icon:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.15);
}

.project-icon {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.api-icon {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.user-icon {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

.request-icon {
  background: linear-gradient(135deg, #fa709a 0%, #fee140 100%);
}

.total-request-icon {
  background: linear-gradient(135deg, #30cfd0 0%, #330867 100%);
}

.stat-content {
  flex: 1;
  text-align: left;
}

.stat-content h3 {
  margin: 0 0 8px 0;
  font-size: 14px;
  color: #909399;
  font-weight: normal;
  letter-spacing: 0.5px;
}

.stat-content p {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  line-height: 1;
}

.quick-start {
  padding: 10px;
}

.card-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-svg-icon {
  flex-shrink: 0;
}

.readme-content {
  color: #606266;
  line-height: 1.8;
  max-height: 45vh;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 10px;
  word-wrap: break-word;
  word-break: break-word;
}

/* README Markdown 样式 */
.readme-content :deep(h1) {
  font-size: 22px;
  font-weight: 700;
  margin: 16px 0 12px;
  padding-bottom: 8px;
  border-bottom: 2px solid #e4e7ed;
  color: #303133;
}

.readme-content :deep(h2) {
  font-size: 18px;
  font-weight: 600;
  margin: 14px 0 10px;
  padding-bottom: 6px;
  border-bottom: 1px solid #e4e7ed;
  color: #303133;
}

.readme-content :deep(h3) {
  font-size: 16px;
  font-weight: 600;
  margin: 12px 0 8px;
  color: #303133;
}

.readme-content :deep(p) {
  margin: 8px 0;
  line-height: 1.8;
}

.readme-content :deep(ul),
.readme-content :deep(ol) {
  margin: 8px 0;
  padding-left: 24px;
}

.readme-content :deep(li) {
  margin: 4px 0;
  line-height: 1.7;
}

.readme-content :deep(code) {
  background-color: #f5f5f5;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  color: #e83e8c;
}

.readme-content :deep(pre) {
  background-color: #282c34;
  color: #abb2bf;
  padding: 12px;
  border-radius: 5px;
  overflow-x: auto;
  margin: 10px 0;
  line-height: 1.5;
}

.readme-content :deep(pre code) {
  background-color: transparent;
  padding: 0;
  color: #abb2bf;
  font-size: 13px;
}

.readme-content :deep(blockquote) {
  border-left: 4px solid #409eff;
  padding: 8px 15px;
  margin: 10px 0;
  color: #606266;
  background-color: #f4f4f5;
  border-radius: 3px;
}

.readme-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 10px 0;
  font-size: 13px;
}

.readme-content :deep(table th),
.readme-content :deep(table td) {
  border: 1px solid #e4e7ed;
  padding: 8px 10px;
  text-align: left;
}

.readme-content :deep(table th) {
  background-color: #f5f7fa;
  font-weight: 600;
}

.readme-content :deep(a) {
  color: #409eff;
  text-decoration: none;
}

.readme-content :deep(a:hover) {
  text-decoration: underline;
}

.readme-content :deep(strong) {
  font-weight: 600;
  color: #303133;
}

.readme-content :deep(hr) {
  border: none;
  border-top: 1px solid #e4e7ed;
  margin: 16px 0;
}

.readme-content :deep(img) {
  max-width: 100%;
}

/* 第一个 h1 不需要上边距 */
.readme-content :deep(h1:first-child) {
  margin-top: 0;
}

.system-info {
  padding: 10px;
  line-height: 2;
}

.system-info p {
  margin: 5px 0;
}

.system-info a {
  color: #409EFF;
  text-decoration: none;
}

.system-info a:hover {
  text-decoration: underline;
}

.announcement {
  padding: 0;
}

.announcement-content {
  color: #606266;
  line-height: 1.8;
  max-height: 20vh;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 10px;
  word-wrap: break-word;
  word-break: break-word;
}

.announcement-content h1,
.announcement-content h2,
.announcement-content h3,
.announcement-content h4,
.announcement-content h5,
.announcement-content h6 {
  margin: 15px 0 10px 0;
  color: #303133;
}

.announcement-content p {
  margin: 10px 0;
}

.announcement-content ul,
.announcement-content ol {
  margin: 10px 0;
  padding-left: 20px;
}

.announcement-content li {
  margin: 5px 0;
}

.announcement-content code {
  background-color: #f5f5f5;
  padding: 2px 5px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
  font-size: 14px;
}

.announcement-content pre {
  background-color: #f5f5f5;
  padding: 15px;
  border-radius: 5px;
  overflow-x: auto;
  margin: 15px 0;
}

.announcement-content pre code {
  background-color: transparent;
  padding: 0;
}

.announcement-content blockquote {
  border-left: 4px solid #409EFF;
  padding-left: 15px;
  margin: 15px 0;
  color: #909399;
}

.no-announcement {
  padding: 20px 0;
  display: flex;
  justify-content: center;
  align-items: center;
}

@media (max-width: 768px) {
  .home {
    padding: 8px;
  }

  .stat-card {
    height: 90px;
    margin-top: 6px;
    margin-bottom: 10px;
    padding: 0 12px;
  }

  .stat-icon {
    width: 44px;
    height: 44px;
    font-size: 18px;
    margin-right: 12px;
  }

  .stat-content p {
    font-size: 18px;
  }

  .stat-content h3 {
    font-size: 12px;
  }

  .card-header-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .readme-content {
    max-height: 30vh;
    padding: 6px;
  }
}

/* 1200px - 1599px: 每个统计项最大宽度25% */
@media only screen and (min-width: 1200px) and (max-width: 1599px) {
  .el-col-lg-6,
  .el-col-lg-6.is-guttered {
    max-width: 25%;
    margin-top: 10px;
  }
}

/* 1600px及以上: 每个统计项最大宽度20% */
@media only screen and (min-width: 1600px) {
  .el-col-lg-6,
  .el-col-lg-6.is-guttered {
    max-width: 20%;
  }
}
</style>
