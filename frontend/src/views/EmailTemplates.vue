<!--
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
-->

<template>
  <div class="email-templates">
    <div class="page-header">
      <h1>{{ $t('emailTemplate.title') }}</h1>
      <div style="display: flex; align-items: center; gap: 10px;">
        <el-button @click="showPlaceholders = !showPlaceholders" :type="showPlaceholders ? 'primary' : 'default'">
          <InfoFilled :width="'1em'" :height="'1em'" style="margin-right: 4px;" />
          {{ $t('emailTemplate.placeholdersBtn') }}
        </el-button>
        <el-button type="primary" @click="openDialog()" :disabled="!canCreateTemplate">
          <Edit :width="'1em'" :height="'1em'" style="margin-right: 5px;" />
          {{ $t('emailTemplate.createTemplate') }}
        </el-button>
      </div>
    </div>

    <!-- 占位符变量说明 -->
    <el-card v-if="showPlaceholders" style="margin-bottom: 20px; background: #f8f9fa;" shadow="never">
      <template #header>
        <span style="font-weight: 600;">{{ $t('emailTemplate.placeholdersTitle') }}</span>
      </template>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="{{username}}">{{ $t('emailTemplate.placeholderUsername') }}</el-descriptions-item>
        <el-descriptions-item label="{{email}}">{{ $t('emailTemplate.placeholderEmail') }}</el-descriptions-item>
        <el-descriptions-item label="{{time}} / {{loginTime}}">{{ $t('emailTemplate.placeholderTime') }}</el-descriptions-item>
        <el-descriptions-item label="{{siteUrl}} / {{baseUrl}}">{{ $t('emailTemplate.placeholderSiteUrl') }}</el-descriptions-item>
        <el-descriptions-item label="{{code}}">{{ $t('emailTemplate.placeholderCode') }}</el-descriptions-item>
        <el-descriptions-item label="{{password}} / {{newPassword}}">{{ $t('emailTemplate.placeholderPassword') }}</el-descriptions-item>
      </el-descriptions>
      <div style="margin-top: 12px; color: #909399; font-size: 12px; line-height: 1.8;">
        <p style="margin: 0;">{{ $t('emailTemplate.placeholderNote') }}</p>
        <p style="margin: 2px 0 0 0;">• <strong>{{ $t('emailTemplate.typeRegister') }}</strong>：{{ $t('emailTemplate.placeholderCodeNoteRegister') }}</p>
        <p style="margin: 2px 0 0 0;">• <strong>{{ $t('emailTemplate.typeResetPassword') }}</strong> / <strong>{{ $t('emailTemplate.typePasswordChanged') }}</strong>：{{ $t('emailTemplate.placeholderCodeNotePassword') }}</p>
      </div>
    </el-card>

    <el-card>
      <!-- 搜索栏 -->
      <el-form :inline="true" :model="searchForm" style="margin-bottom: 16px;">
        <el-form-item :label="$t('emailTemplate.name')">
          <el-input v-model="searchForm.name" :placeholder="$t('emailTemplate.name')" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item :label="$t('emailTemplate.type')">
          <el-select v-model="searchForm.type" :placeholder="$t('emailTemplate.type')" clearable style="width: 180px">
            <el-option :label="$t('emailTemplate.typeRegister')" value="REGISTER" />
            <el-option :label="$t('emailTemplate.typeResetPassword')" value="RESET_PASSWORD" />
            <el-option :label="$t('emailTemplate.typePasswordChanged')" value="PASSWORD_CHANGED" />
            <el-option :label="$t('emailTemplate.typeLoginAlert')" value="LOGIN_ALERT" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('emailTemplate.enabled')">
          <el-select v-model="searchForm.enabled" :placeholder="$t('emailTemplate.enabled')" clearable style="width: 120px">
            <el-option :label="$t('project.enabledStatus')" :value="true" />
            <el-option :label="$t('project.disabledStatus')" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            {{ $t('common.search') }}
          </el-button>
          <el-button @click="handleReset">
            {{ $t('common.reset') }}
          </el-button>
        </el-form-item>
      </el-form>

      <el-table :data="templates" border style="width: 100%" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" :label="$t('emailTemplate.name')" width="200" />
        <el-table-column prop="type" :label="$t('emailTemplate.type')" width="180">
          <template #default="{ row }">
            <el-tag v-if="row.type === 'REGISTER'" type="primary">
              {{ $t('emailTemplate.typeRegister') }}
            </el-tag>
            <el-tag v-else-if="row.type === 'RESET_PASSWORD'" type="warning">
              {{ $t('emailTemplate.typeResetPassword') }}
            </el-tag>
            <el-tag v-else-if="row.type === 'PASSWORD_CHANGED'" type="danger">
              {{ $t('emailTemplate.typePasswordChanged') }}
            </el-tag>
            <el-tag v-else>{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="subject" :label="$t('emailTemplate.subject')" show-overflow-tooltip />
        <el-table-column prop="enabled" :label="$t('emailTemplate.enabled')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? $t('project.enabledStatus') : $t('project.disabledStatus') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('settings.createTime')" width="180">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.edit')" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)" :disabled="!canEditTemplate">
              <Edit :width="'1em'" :height="'1em'" />
              {{ $t('common.edit') }}
            </el-button>
            <el-button link type="danger" @click="deleteTemplate(row.id)" :disabled="!canDeleteTemplate">
              <Delete :width="'1em'" :height="'1em'" />
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="templates.length === 0 && !loading" style="text-align: center; padding: 40px;">
        <el-empty :description="$t('home.noAnnouncement')" />
      </div>

      <div style="margin-top: 16px; display: flex; justify-content: flex-end;">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="65%" :close-on-click-modal="false" :append-to-body="false">
      <el-form :model="form" label-width="120px">
        <el-form-item :label="$t('emailTemplate.templateName')">
          <el-input v-model="form.name" :placeholder="$t('emailTemplate.templateNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('emailTemplate.templateType')">
          <el-select v-model="form.type" style="width: 100%;">
            <el-option :label="$t('emailTemplate.typeRegister')" value="REGISTER" />
            <el-option :label="$t('emailTemplate.typeResetPassword')" value="RESET_PASSWORD" />
            <el-option :label="$t('emailTemplate.typePasswordChanged')" value="PASSWORD_CHANGED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('emailTemplate.templateEnabled')">
          <el-switch v-model="form.enabled" :active-text="$t('project.enabledStatus')" :inactive-text="$t('project.disabledStatus')" />
        </el-form-item>
        <el-form-item :label="$t('emailTemplate.subject')">
          <div style="display: flex; gap: 8px; width: 100%;">
            <el-input v-model="form.subject" :placeholder="$t('emailTemplate.subjectPlaceholder')" style="flex: 1;" />
            <el-button type="warning" size="small" @click="handleAiGenerateSubject" :loading="aiSubjectLoading" style="flex-shrink: 0; align-self: flex-start;">
              <MagicStick :width="'0.9em'" :height="'0.9em'" style="margin-right: 4px;" />
              {{ aiSubjectLoading ? $t('ai.generating') : $t('ai.generate') }}
            </el-button>
          </div>
        </el-form-item>
        <!-- 邮件正文 -->
        <el-form-item :label="$t('emailTemplate.content')">
          <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
            <div style="color: #909399; font-size: 12px;">
              支持的占位符：&#123;&#123;username&#125;&#125;、&#123;&#123;email&#125;&#125;、&#123;&#123;time&#125;&#125;、&#123;&#123;siteUrl&#125;&#125;、&#123;&#123;code&#125;&#125;、&#123;&#123;password&#125;&#125;、&#123;&#123;newPassword&#125;&#125;
            </div>
            <div style="display: flex; gap: 8px;">
              <el-button
                size="small"
                :disabled="!form.content"
                @click="openPreview"
              >
                <View :width="'0.9em'" :height="'0.9em'" style="margin-right: 4px;" />
                {{ $t('emailTemplate.preview') }}
              </el-button>
              <el-button
                type="warning"
                size="small"
                @click="handleAiGenerateTemplate"
                :loading="aiTemplateLoading"
              >
                <MagicStick :width="'0.9em'" :height="'0.9em'" style="margin-right: 4px;" />
                {{ aiTemplateLoading ? $t('ai.generating') : $t('ai.generateTemplate') }}
              </el-button>
            </div>
          </div>
          <textarea
            v-model="form.content"
            class="email-content-textarea"
            :placeholder="$t('emailTemplate.contentPlaceholder')"
            rows="12"
            style="width: 100%; min-height: 200px; padding: 8px 12px; border: 1px solid #dcdfe6; border-radius: 4px; font-size: 14px; line-height: 1.6; resize: vertical; font-family: inherit; outline: none; box-sizing: border-box;"
          ></textarea>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveTemplate" :loading="saving">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 预览对话框 -->
    <el-dialog
      v-model="previewDialogVisible"
      :title="$t('emailTemplate.previewHtml')"
      width="55%"
      :close-on-click-modal="true"
      :append-to-body="true"
    >
      <iframe
        class="preview-dialog-iframe"
        :srcdoc="previewContent"
        sandbox="allow-same-origin"
        scrolling="auto"
      ></iframe>
    </el-dialog>

    <!-- AI 自定义提示词对话框 - 邮件主题 -->
    <el-dialog
      v-model="subjectCustomPromptVisible"
      :title="$t('emailTemplate.customPromptTitle')"
      width="500px"
      :close-on-click-modal="false"
      :append-to-body="true"
    >
      <div style="margin-bottom: 8px; color: #606266; font-size: 13px;">
        {{ $t('emailTemplate.customPromptDescSubject') }}
      </div>
      <el-input
        v-model="subjectCustomPrompt"
        type="textarea"
        :rows="5"
        :placeholder="$t('emailTemplate.customPromptPlaceholder')"
        maxlength="1000"
        show-word-limit
      />
      <template #footer>
        <el-button @click="subjectCustomPromptVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="executeAiGenerateSubject" :loading="aiSubjectLoading">
          {{ $t('ai.generate') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- AI 自定义提示词对话框 - 邮件内容 -->
    <el-dialog
      v-model="templateCustomPromptVisible"
      :title="$t('emailTemplate.customPromptTitle')"
      width="500px"
      :close-on-click-modal="false"
      :append-to-body="true"
    >
      <div style="margin-bottom: 8px; color: #606266; font-size: 13px;">
        {{ $t('emailTemplate.customPromptDescTemplate') }}
      </div>
      <el-input
        v-model="templateCustomPrompt"
        type="textarea"
        :rows="5"
        :placeholder="$t('emailTemplate.customPromptPlaceholder')"
        maxlength="1000"
        show-word-limit
      />
      <template #footer>
        <el-button @click="templateCustomPromptVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="executeAiGenerateTemplate" :loading="aiTemplateLoading">
          {{ $t('ai.generate') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Delete, InfoFilled, View, MagicStick } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'
import { generateEmailTemplateStream } from '@/api/ai'

const { t } = useI18n()
const userStore = useUserStore()

const canCreateTemplate = computed(() => userStore.hasPermission('email-template:create'))
const canEditTemplate = computed(() => userStore.hasPermission('email-template:edit'))
const canDeleteTemplate = computed(() => userStore.hasPermission('email-template:delete'))

const templates = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const showPlaceholders = ref(false)
const previewDialogVisible = ref(false)
const previewContent = ref('')

const form = reactive({
  id: null,
  name: '',
  type: 'REGISTER',
  subject: '',
  content: '',
  enabled: true
})

// 搜索和分页
const searchForm = reactive({
  name: '',
  type: '',
  enabled: null
})
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0
})

// AI 辅助编写相关
const aiSubjectLoading = ref(false)
const aiTemplateLoading = ref(false)
// 主题AI生成的独立提示词对话框
const subjectCustomPromptVisible = ref(false)
const subjectCustomPrompt = ref('')
// 内容AI生成的独立提示词对话框
const templateCustomPromptVisible = ref(false)
const templateCustomPrompt = ref('')

// AI 生成邮件主题（流式）—— 先弹出提示词输入框
const handleAiGenerateSubject = async () => {
  if (!form.name || !form.name.trim()) {
    ElMessage.warning(t('emailTemplate.nameRequired'))
    return
  }
  subjectCustomPrompt.value = ''
  subjectCustomPromptVisible.value = true
}

// AI 生成完整邮件模板（流式）—— 先弹出提示词输入框
const handleAiGenerateTemplate = async () => {
  if (!form.name || !form.name.trim()) {
    ElMessage.warning(t('emailTemplate.nameRequired'))
    return
  }
  templateCustomPrompt.value = ''
  templateCustomPromptVisible.value = true
}

// 执行 AI 生成主题（弹窗确认后调用）
const executeAiGenerateSubject = async () => {
  subjectCustomPromptVisible.value = false
  aiSubjectLoading.value = true
  try {
    const params = {
      templateType: form.type,
      templateName: form.name.trim(),
      existingSubject: form.subject || undefined,
      existingContent: form.content || undefined
    }
    if (subjectCustomPrompt.value && subjectCustomPrompt.value.trim()) {
      params.customPrompt = subjectCustomPrompt.value.trim()
    }

    const fullText = await generateEmailTemplateStream(params)
    const cleaned = cleanMarkdownJson(fullText)
    const parsed = JSON.parse(cleaned)
    if (parsed.subject) form.subject = parsed.subject
    ElMessage.success(t('ai.emailSubjectGenerated'))
  } catch (error) {
    console.error('AI 生成主题失败:', error)
    ElMessage.error(error.message || t('ai.generateFailed'))
  } finally {
    aiSubjectLoading.value = false
  }
}

// 执行 AI 生成内容（弹窗确认后调用）
const executeAiGenerateTemplate = async () => {
  templateCustomPromptVisible.value = false
  aiTemplateLoading.value = true
  try {
    const params = {
      templateType: form.type,
      templateName: form.name.trim(),
      existingSubject: form.subject || undefined,
      existingContent: form.content || undefined
    }
    if (templateCustomPrompt.value && templateCustomPrompt.value.trim()) {
      params.customPrompt = templateCustomPrompt.value.trim()
    }

    const fullText = await generateEmailTemplateStream(params)
    const cleaned = cleanMarkdownJson(fullText)
    const parsed = JSON.parse(cleaned)
    if (parsed.subject) form.subject = parsed.subject
    if (parsed.content) form.content = parsed.content
    ElMessage.success(t('ai.emailTemplateGenerated'))
  } catch (error) {
    console.error('AI 生成模板失败:', error)
    ElMessage.error(error.message || t('ai.generateFailed'))
  } finally {
    aiTemplateLoading.value = false
  }
}

// 清理 markdown 代码块标记
function cleanMarkdownJson(text) {
  let cleaned = text.trim()
  cleaned = cleaned.replace(/^```(?:json)?\s*\n?/i, '')
  cleaned = cleaned.replace(/\n?\s*```$/i, '')
  // 提取 { 到 } 之间的内容
  const startIdx = cleaned.indexOf('{')
  const endIdx = cleaned.lastIndexOf('}')
  if (startIdx !== -1 && endIdx !== -1 && endIdx > startIdx) {
    cleaned = cleaned.substring(startIdx, endIdx + 1)
  }
  return cleaned
}

// 获取模板列表（支持分页和搜索）
const fetchTemplates = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.current - 1,
      size: pagination.pageSize
    }
    if (searchForm.name) params.name = searchForm.name
    if (searchForm.type) params.type = searchForm.type
    if (searchForm.enabled !== null && searchForm.enabled !== '') params.enabled = searchForm.enabled

    const response = await request.get('/email-templates', { params })
    if (response.code === 200) {
      templates.value = response.data.content || []
      pagination.total = response.data.totalElements || 0
    }
  } catch (error) {
    console.error('加载模板列表失败:', error)
    ElMessage.error(t('common.error'))
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.current = 1
  fetchTemplates()
}

const handleReset = () => {
  searchForm.name = ''
  searchForm.type = ''
  searchForm.enabled = null
  pagination.current = 1
  fetchTemplates()
}

const handleSizeChange = (size) => {
  pagination.pageSize = size
  pagination.current = 1
  fetchTemplates()
}

const handleCurrentChange = (page) => {
  pagination.current = page
  fetchTemplates()
}

// 打开编辑对话框
const openDialog = (row = null) => {
  if (row) {
    dialogTitle.value = t('emailTemplate.editTemplate')
    form.id = row.id
    form.name = row.name
    form.type = row.type || 'REGISTER'
    form.subject = row.subject
    form.content = row.content
    form.enabled = row.enabled !== false
  } else {
    dialogTitle.value = t('emailTemplate.createTemplate')
    form.id = null
    form.name = ''
    form.type = 'REGISTER'
    form.subject = ''
    form.content = ''
    form.enabled = true
  }
  dialogVisible.value = true
}

// 打开预览对话框
const openPreview = () => {
  if (!form.content) return
  previewContent.value = form.content
  previewDialogVisible.value = true
}

// 保存模板
const saveTemplate = async () => {
  if (!form.name || !form.name.trim()) {
    ElMessage.warning(t('emailTemplate.nameRequired'))
    return
  }
  if (!form.subject || !form.subject.trim()) {
    ElMessage.warning(t('emailTemplate.subjectRequired'))
    return
  }
  if (!form.content || !form.content.trim()) {
    ElMessage.warning(t('emailTemplate.contentRequired'))
    return
  }

  saving.value = true
  try {
    let response
    if (form.id) {
      response = await request.put(`/email-templates/${form.id}`, {
        name: form.name.trim(),
        type: form.type || 'REGISTER',
        subject: form.subject.trim(),
        content: form.content,
        enabled: form.enabled
      })
    } else {
      response = await request.post('/email-templates', {
        name: form.name.trim(),
        type: form.type || 'REGISTER',
        subject: form.subject.trim(),
        content: form.content,
        enabled: form.enabled
      })
    }

    if (response.code === 200) {
      ElMessage.success(form.id ? t('emailTemplate.updateSuccess') : t('emailTemplate.createSuccess'))
      dialogVisible.value = false
      fetchTemplates()
    } else {
      ElMessage.error(response.message || t('common.error'))
    }
  } catch (error) {
    console.error('保存模板失败:', error)
    ElMessage.error(t('common.error'))
  } finally {
    saving.value = false
  }
}

// 删除模板
const deleteTemplate = async (id) => {
  try {
    await ElMessageBox.confirm(t('emailTemplate.deleteConfirm'), t('common.warning'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    })

    const response = await request.delete(`/email-templates/${id}`)
    if (response.code === 200) {
      ElMessage.success(t('emailTemplate.deleteSuccess'))
      fetchTemplates()
    } else {
      ElMessage.error(response.message || t('common.error'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除模板失败:', error)
      ElMessage.error(t('common.error'))
    }
  }
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

onMounted(() => {
  fetchTemplates()
})
</script>

<style scoped>
.email-templates {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

/* 确保邮件内容文本域在对话框中可见 */
:deep(.el-textarea) {
  width: 100%;
}

:deep(.el-textarea__inner) {
  min-height: 200px;
  display: block !important;
  visibility: visible !important;
}

.preview-dialog-iframe {
  width: 100%;
  min-height: 500px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  display: block;
}
</style>
