<!--
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
-->

<template>
  <div class="projects">
    <div class="page-header">
      <h1>{{ $t('project.title') }}</h1>
      <div class="header-actions">
        <el-button type="primary" @click="handleCreate" v-if="canCreateProject">
          <Plus :width="'1em'" :height="'1em'" />
          {{ $t('project.createProject') }}
        </el-button>
        <el-button type="success" @click="handleImportData" v-if="canCreateProject && canImportDataPerm">
          <UploadFilled :width="'1em'" :height="'1em'" />
          {{ $t('project.importData') }}
        </el-button>
      </div>
    </div>

    <el-card class="search-card">
      <el-row :gutter="20">
        <el-col :span="8">
          <el-input v-model="searchForm.name" :placeholder="$t('project.searchByName')" clearable @clear="handleSearch" />
        </el-col>
        <el-col :span="6">
          <el-input v-model="searchForm.code" :placeholder="$t('project.searchByCode')" clearable @clear="handleSearch" />
        </el-col>
        <el-col :span="6">
          <el-select v-model="searchForm.enabled" :placeholder="$t('project.status')" clearable @change="handleSearch" style="width: 100%">
            <el-option :label="$t('project.enabledStatus')" :value="true" />
            <el-option :label="$t('project.disabledStatus')" :value="false" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-button type="primary" @click="handleSearch">{{ $t('project.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('project.reset') }}</el-button>
        </el-col>
      </el-row>
    </el-card>

    <el-card class="table-card">
      <el-table
        v-loading="loading"
        :data="projectList"
        border
        style="width: 100%"
        :header-cell-style="{ background: '#f5f7fa' }"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" :label="$t('project.projectName')" min-width="150" />
        <el-table-column prop="code" :label="$t('project.projectCode')" min-width="120" />
        <el-table-column prop="description" :label="$t('project.description')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="enabled" :label="$t('project.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? $t('project.enabledStatus') : $t('project.disabledStatus') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('project.createdAt')" width="200">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('project.actions')" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-dropdown trigger="click" @command="(cmd) => handleActionCommand(cmd, row)">
              <el-button type="primary" link>
                {{ $t('common.more') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="edit" :disabled="!canEditProject(row)">{{ $t('project.editProject') }}</el-dropdown-item>
                  <el-dropdown-item command="api">{{ $t('project.apiManagement') }}</el-dropdown-item>
                  <el-dropdown-item command="import" :disabled="!canImportSwagger(row)">{{ $t('project.importSwagger') }}</el-dropdown-item>
                  <el-dropdown-item command="exportSwagger" :disabled="!canExportSwagger(row)">{{ $t('project.exportSwagger') }}</el-dropdown-item>
                  <el-dropdown-item command="exportData" :disabled="!canExportData(row)">{{ $t('project.exportData') }}</el-dropdown-item>
                  <el-dropdown-item command="members" :disabled="!canManageMembers(row)">{{ $t('project.memberManagement') }}</el-dropdown-item>
                  <el-dropdown-item command="delete" :disabled="!canDeleteProject(row)" divided style="color: #f56c6c;">{{ $t('project.deleteProject') }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" @close="handleDialogClose">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item :label="$t('project.projectName')" prop="name">
          <el-input v-model="form.name" :placeholder="$t('project.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('project.projectCode')" prop="code">
          <el-input v-model="form.code" :placeholder="$t('project.codePlaceholder')" :disabled="isEdit" />
        </el-form-item>
        <el-form-item :label="$t('project.projectDescription')" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" :placeholder="$t('project.descriptionPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('project.status')" prop="enabled">
          <el-switch v-model="form.enabled" :active-text="$t('project.enabledStatus')" :inactive-text="$t('project.disabledStatus')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="memberDialogVisible" :title="$t('project.memberTitle')" width="50%" @close="handleMemberDialogClose">
      <div class="member-header">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-button type="primary" @click="handleAddMember">
              <Plus :width="'1em'" :height="'1em'" />
              {{ $t('project.addMember') }}
            </el-button>
          </el-col>
          <el-col :span="12" style="text-align: right;">
            <span style="color: #909399; font-size: 14px;">
              <span>{{ $t('project.currentProject') }}</span>
              <span style="color: #303133; font-weight: 600;">{{ currentProject?.name }}</span>
            </span>
          </el-col>
        </el-row>
      </div>

      <el-table
        v-loading="memberLoading"
        :data="memberList"
        border
        style="width: 100%; margin-top: 16px;"
        :header-cell-style="{ background: '#f5f7fa' }"
      >
        <el-table-column prop="id" :label="$t('project.memberId')" width="80" />
        <el-table-column prop="userId" :label="$t('project.userId')" width="80" />
        <el-table-column prop="username" :label="$t('project.username')" min-width="120">
          <template #default="{ row }">
            <span>{{ row.username || $t('project.username') + row.userId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="email" :label="$t('project.email')" min-width="180">
          <template #default="{ row }">
            <span>{{ row.email || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="role" :label="$t('project.role')" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.role === 'ADMIN' || row.role === 'CREATOR'" type="warning" size="small">{{ $t('project.projectAdmin') }}</el-tag>
            <el-tag v-else type="info" size="small">{{ $t('project.projectMember') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('project.joinTime')" width="200">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('project.actions')" width="100" align="center">
          <template #default="{ row }">
            <el-dropdown trigger="click" @command="(cmd) => handleMemberActionCommand(cmd, row)">
                <el-button type="primary" link>
                  {{ $t('common.more') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="role">{{ $t('project.modifyRole') }}</el-dropdown-item>
                    <el-dropdown-item command="remove" divided style="color: #f56c6c;">{{ $t('project.remove') }}</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- Swagger 导入对话框 -->
    <el-dialog v-model="importDialogVisible" :title="$t('project.importSwaggerTitle')" width="560px" @close="handleImportDialogClose">
      <el-tabs v-model="importTab" class="import-tabs">
        <el-tab-pane :label="$t('project.importByFile')" name="file">
          <el-upload
            ref="uploadRef"
            class="import-upload"
            drag
            :auto-upload="false"
            :limit="1"
            accept=".json"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
          >
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div class="upload-text">
              <p>{{ $t('project.uploadHint') }}</p>
              <p class="upload-sub">{{ $t('project.uploadFormat') }}</p>
            </div>
          </el-upload>
        </el-tab-pane>
        <el-tab-pane :label="$t('project.importByUrl')" name="url">
          <el-input
            v-model="importUrl"
            :placeholder="$t('project.swaggerUrlPlaceholder')"
            size="large"
            clearable
          >
            <template #prefix>
              <el-icon><Link /></el-icon>
            </template>
          </el-input>
          <div class="import-url-hint">{{ $t('project.swaggerUrlHint') }}</div>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="importDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="importLoading" @click="handleImportSubmit">
          {{ $t('project.startImport') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 导入结果对话框 -->
    <el-dialog v-model="importResultVisible" :title="$t('project.importResultTitle')" width="580px">
      <div class="import-result">
        <div class="result-summary">
          <div class="result-item success">
            <el-icon><CircleCheckFilled /></el-icon>
            <span>{{ $t('project.importSuccessCount', { count: importResult.success }) }}</span>
          </div>
          <div v-if="importResult.skipped > 0" class="result-item skipped">
            <el-icon><RemoveFilled /></el-icon>
            <span>{{ $t('project.importSkippedCount', { count: importResult.skipped }) }}</span>
          </div>
          <div class="result-item failed" v-if="importResult.failed > 0">
            <el-icon><CircleCloseFilled /></el-icon>
            <span>{{ $t('project.importFailedCount', { count: importResult.failed }) }}</span>
          </div>
        </div>

        <!-- 冲突提示 -->
        <div v-if="importResult.conflicts && importResult.conflicts.length > 0" class="result-conflicts">
          <h4>{{ $t('project.importConflictTitle', { count: importResult.conflicts.length }) }}</h4>
          <p class="conflict-hint">{{ $t('project.importConflictHint') }}</p>
          <div class="conflict-table">
            <div class="conflict-header">
              <el-checkbox v-model="conflictSelectAll" @change="handleConflictSelectAll">
                {{ $t('project.importConflictSelectAll') }}
              </el-checkbox>
            </div>
            <div v-for="(conflict, idx) in importResult.conflicts" :key="idx" class="conflict-row">
              <el-checkbox v-model="conflictSelected[idx]" class="conflict-checkbox" />
              <div class="conflict-info">
                <div class="conflict-path">
                  <span class="error-method">{{ conflict.method }}</span>
                  <span class="error-path">{{ conflict.path }}</span>
                  <el-tag size="small" type="warning" effect="plain" style="margin-left: 8px;">
                    {{ $t('project.importConflictChangeCount', { count: conflict.changes?.length || 0 }) }}
                  </el-tag>
                </div>
                <!-- 变更详情列表 -->
                <div class="conflict-changes">
                  <div v-for="(change, cIdx) in conflict.changes" :key="cIdx" class="change-item">
                    <div class="change-field-tag">{{ change.fieldName }}</div>
                    <div class="change-values">
                      <div class="change-value-row">
                        <span class="change-arrow-label">{{ $t('project.importConflictExisting') }}</span>
                        <span class="change-val old-val" :title="change.existingValue">{{ truncateValue(change.existingValue) }}</span>
                      </div>
                      <div class="change-value-row">
                        <span class="change-arrow-label">{{ $t('project.importConflictNew') }}</span>
                        <span class="change-val new-val" :title="change.newValue">{{ truncateValue(change.newValue) }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="importResult.errors && importResult.errors.length > 0" class="result-errors">
          <h4>{{ $t('project.importErrorList') }}</h4>
          <div v-for="(err, idx) in importResult.errors" :key="idx" class="error-item">
            <span class="error-method">{{ err.method }}</span>
            <span class="error-path">{{ err.path }}</span>
            <span class="error-reason">{{ err.reason }}</span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button
          v-if="importResult.conflicts && importResult.conflicts.length > 0"
          type="warning"
          :loading="conflictResolving"
          @click="handleConflictResolve"
        >
          {{ conflictResolving ? $t('project.importConflictOverwriting') : $t('project.importConflictOverwrite') }}
        </el-button>
        <el-button type="primary" @click="onImportDone">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 修改角色对话框 -->
    <el-dialog v-model="roleChangeDialogVisible" :title="roleChangeDialogTitle" width="420px" @close="handleRoleChangeDialogClose">
      <el-form label-width="80px">
        <el-form-item :label="$t('project.role')">
          <el-select v-model="roleChangeForm.role" :placeholder="$t('project.selectRole')" style="width: 100%;">
            <el-option :label="$t('project.projectAdmin')" value="ADMIN" />
            <el-option :label="$t('project.projectMember')" value="MEMBER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleChangeDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="roleChangeLoading" @click="handleRoleChangeSubmit">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="addMemberDialogVisible" :title="addMemberDialogTitle" width="500px" style="max-width: 50%;" @close="handleAddMemberDialogClose">
      <el-form ref="memberFormRef" :model="memberForm" :rules="memberRules" label-width="80px">
        <el-form-item :label="$t('project.selectUser')" prop="userId">
          <el-select
            v-model="memberForm.userId"
            filterable
            remote
            reserve-keyword
            :placeholder="$t('project.searchUsers')"
            :remote-method="searchUsers"
            :loading="userSearchLoading"
            style="width: 100%"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="`${user.username} (${user.email})`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('project.role')" prop="role">
          <el-select v-model="memberForm.role" :placeholder="$t('project.selectRole')" style="width: 100%;">
            <el-option :label="$t('project.projectAdmin')" value="ADMIN" />
            <el-option :label="$t('project.projectMember')" value="MEMBER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addMemberDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="memberSubmitLoading" @click="handleMemberSubmit">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- Swagger 版本选择对话框 -->
    <el-dialog v-model="swaggerExportDialogVisible" :title="$t('project.exportSwaggerTitle')" width="420px">
      <div style="padding: 16px 0;">
        <el-radio-group v-model="swaggerExportVersion">
          <el-radio value="2.0" size="large" style="display: block;">{{ $t('project.swaggerVersion2') }}</el-radio>
          <el-radio value="3.0" size="large" style="display: block;">{{ $t('project.swaggerVersion3') }}</el-radio>
        </el-radio-group>
      </div>
      <template #footer>
        <el-button @click="swaggerExportDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="swaggerExportLoading" @click="handleSwaggerExportConfirm">
          {{ $t('common.download') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 导入项目数据对话框 -->
    <el-dialog v-model="importDataDialogVisible" :title="$t('project.importDataTitle')" width="520px" @close="handleImportDataDialogClose">
      <div style="margin-bottom: 12px; padding: 10px; background: #f0f9ff; border: 1px solid #b3d8ff; border-radius: 4px;">
        <el-icon style="color: #409eff; margin-right: 6px;"><InfoFilled /></el-icon>
        <span style="color: #409eff; font-size: 13px;">{{ $t('project.importDataHint') }}</span>
      </div>
      <el-upload
        ref="dataUploadRef"
        class="import-upload"
        drag
        :auto-upload="false"
        :limit="1"
        accept=".json"
        :on-change="handleDataFileChange"
        :on-remove="handleDataFileRemove"
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-text">
          <p>{{ $t('project.importDataUpload') }}</p>
          <p class="upload-sub">{{ $t('project.importDataFormat') }}</p>
        </div>
      </el-upload>
      <!-- 检测到已存在项目时显示 -->
      <div v-if="importDataConflictInfo" style="margin-top: 16px; padding: 12px; background: #fef9e7; border: 1px solid #faecd8; border-radius: 4px;">
        <p style="margin: 0 0 8px; color: #e6a23c; font-weight: 600; font-size: 14px;">
          <el-icon style="margin-right: 4px;"><WarningFilled /></el-icon>
          {{ $t('project.importDataConflictTitle', { name: importDataConflictInfo.name, code: importDataConflictInfo.code }) }}
        </p>
        <p style="margin: 0 0 12px; color: #909399; font-size: 13px;">{{ $t('project.importDataConflictHint') }}</p>
        <el-radio-group v-model="importDataMode">
          <el-radio value="merge">{{ $t('project.importDataMerge') }}</el-radio>
          <el-radio value="replace">{{ $t('project.importDataReplace') }}</el-radio>
        </el-radio-group>
      </div>
      <template #footer>
        <el-button @click="importDataDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="importDataLoading" @click="handleImportDataSubmit" :disabled="!importDataFile">
          {{ $t('common.import') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Search, ArrowDown, UploadFilled, Link, CircleCheckFilled, CircleCloseFilled, RemoveFilled, Download, InfoFilled, WarningFilled } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { formatTime, loadDateFormat } from '@/utils/dateFormat'
import { exportSwagger, exportProjectData, importProjectData } from '@/api/projectExport'

const router = useRouter()
const { t } = useI18n()
const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)
// 管理员或拥有 project:view_all 权限的用户可以看到所有项目
const canViewAllProjects = computed(() => userStore.hasPermission('project:view_all'))
const canCreateProject = computed(() => userStore.hasPermission('project:create'))
const canEditProject = (project) => {
  // 有 project:view_all 且有 project:edit 权限：可编辑所有项目
  if (canViewAllProjects.value && userStore.hasPermission('project:edit')) return true
  // 否则只能编辑自己是项目管理员/创建者的项目
  const role = project.userRole
  return role === 'ADMIN' || role === 'CREATOR'
}
const canDeleteProject = (project) => {
  // 有 project:view_all 且有 project:delete 权限：可删除所有项目
  if (canViewAllProjects.value && userStore.hasPermission('project:delete')) return true
  // 否则只能删除自己是项目管理员/创建者的项目
  const role = project.userRole
  return role === 'ADMIN' || role === 'CREATOR'
}
// 项目操作权限
// 导入 Swagger：仅项目管理员可操作（且有 project:import_swagger 权限）
const canImportSwagger = (project) => {
  if (canViewAllProjects.value && userStore.hasPermission('project:import_swagger')) return true
  const role = project.userRole
  return (role === 'ADMIN' || role === 'CREATOR') && userStore.hasPermission('project:import_swagger')
}
// 导出 Swagger：项目成员可操作（需 project:export_swagger 权限）
const canExportSwagger = (project) => {
  if (canViewAllProjects.value && userStore.hasPermission('project:export_swagger')) return true
  const role = project.userRole
  return (role === 'ADMIN' || role === 'CREATOR' || role === 'MEMBER') && userStore.hasPermission('project:export_swagger')
}
// 导出项目数据：项目成员可操作（需 project:export_data 权限）
const canExportData = (project) => {
  if (canViewAllProjects.value && userStore.hasPermission('project:export_data')) return true
  const role = project.userRole
  return (role === 'ADMIN' || role === 'CREATOR' || role === 'MEMBER') && userStore.hasPermission('project:export_data')
}
// 导入项目数据：页面级操作，需 project:import_data 权限
const canImportDataPerm = computed(() => userStore.hasPermission('project:import_data'))

const searchForm = reactive({
  name: '',
  code: '',
  enabled: null
})

const loading = ref(false)
const projectList = ref([])

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref()

const form = reactive({
  id: null,
  name: '',
  code: '',
  description: '',
  enabled: true
})

const rules = computed(() => ({
  name: [
    { required: true, message: t('project.nameRequired'), trigger: 'blur' },
    { min: 2, max: 100, message: t('project.nameLengthError'), trigger: 'blur' }
  ],
  code: [
    { required: true, message: t('project.codeRequired'), trigger: 'blur' },
    { min: 2, max: 50, message: t('project.codeLengthError'), trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]+$/, message: t('project.codeFormatError'), trigger: 'blur' }
  ]
}))

const fetchProjects = async () => {
  loading.value = true
  try {
    const url = canViewAllProjects.value ? '/projects' : '/projects/accessible'
    const params = {
      page: pagination.current - 1,
      size: pagination.pageSize
    }
    if (searchForm.name) params.name = searchForm.name
    if (searchForm.code) params.code = searchForm.code
    if (searchForm.enabled !== null && searchForm.enabled !== '') params.enabled = searchForm.enabled

    const response = await request({
      url,
      method: 'get',
      params
    })
    if (response.code === 200) {
      projectList.value = response.data.content || []
      pagination.total = response.data.totalElements || 0
    } else {
      ElMessage.error(t('project.fetchFailed'))
    }
  } catch (error) {
    console.error('获取项目列表失败:', error)
    ElMessage.error(t('project.fetchFailed'))
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.current = 1
  fetchProjects()
}

const handleReset = () => {
  searchForm.name = ''
  searchForm.code = ''
  searchForm.enabled = null
  handleSearch()
}

const handleSizeChange = (size) => {
  pagination.pageSize = size
  fetchProjects()
}

const handleCurrentChange = (page) => {
  pagination.current = page
  fetchProjects()
}

const handleCreate = () => {
  dialogTitle.value = t('project.createProject')
  isEdit.value = false
  form.id = null
  form.name = ''
  form.code = ''
  form.description = ''
  form.enabled = true
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = t('project.editProject')
  isEdit.value = true
  form.id = row.id
  form.name = row.name
  form.code = row.code
  form.description = row.description
  form.enabled = row.enabled
  dialogVisible.value = true
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(t('project.confirmDelete', { name: row.name }), t('common.warning'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'error',
      confirmButtonClass: 'el-button--danger'
    })

    const response = await request({
      url: `/projects/${row.id}`,
      method: 'delete'
    })
    if (response.code === 200) {
      ElMessage.success(t('project.deleteSuccess'))
      fetchProjects()
    } else {
      ElMessage.error(t('project.deleteFailed'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error(t('project.deleteFailed'))
    }
  }
}

const handleActionCommand = (command, row) => {
  switch (command) {
    case 'edit':
      handleEdit(row)
      break
    case 'api':
      router.push(`/apis?projectId=${row.id}`)
      break
    case 'import':
      if (canImportSwagger(row)) handleImportSwagger(row)
      break
    case 'exportSwagger':
      if (canExportSwagger(row)) handleExportSwagger(row)
      break
    case 'exportData':
      if (canExportData(row)) handleExportData(row)
      break
    case 'members':
      handleManageMembers(row)
      break
    case 'delete':
      handleDelete(row)
      break
  }
}

const handleMemberActionCommand = (command, row) => {
  switch (command) {
    case 'role':
      handleUpdateMemberRole(row)
      break
    case 'remove':
      handleRemoveMember(row)
      break
  }
}

const handleSubmit = async () => {
  try {
    await formRef.value.validate()
    submitLoading.value = true

    const response = isEdit.value
      ? await request({
          url: '/projects',
          method: 'put',
          data: form
        })
      : await request({
          url: '/projects',
          method: 'post',
          data: form
        })

    if (response.code === 200) {
      ElMessage.success(isEdit.value ? t('project.editSuccess') : t('project.createSuccess'))
      dialogVisible.value = false
      fetchProjects()
    } else {
      ElMessage.error(response.message || (isEdit.value ? t('project.editFailed') : t('project.createFailed')))
    }
  } catch (error) {
    console.error('提交失败:', error)
    ElMessage.error(isEdit.value ? t('project.editFailed') : t('project.createFailed'))
  } finally {
    submitLoading.value = false
  }
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
}

// ====== 成员管理相关 ======
const memberDialogVisible = ref(false)
const memberLoading = ref(false)
const memberList = ref([])
const currentProject = ref(null)

const addMemberDialogVisible = ref(false)
const addMemberDialogTitle = ref('')
const memberFormRef = ref()
const memberSubmitLoading = ref(false)
const userSearchLoading = ref(false)
const userOptions = ref([])

const memberForm = reactive({
  userId: null,
  role: 'MEMBER'
})

const memberRules = computed(() => ({
  userId: [
    { required: true, message: t('project.selectUser'), trigger: 'change' }
  ],
  role: [
    { required: true, message: t('project.selectRole'), trigger: 'change' }
  ]
}))

// ====== 修改角色对话框 ======
const roleChangeDialogVisible = ref(false)
const roleChangeDialogTitle = ref('')
const roleChangeLoading = ref(false)
const currentRoleChangeMember = ref(null)
const roleChangeForm = reactive({
  role: 'MEMBER'
})

const handleRoleChangeDialogClose = () => {
  currentRoleChangeMember.value = null
}

const handleRoleChangeSubmit = async () => {
  if (!currentRoleChangeMember.value) return
  const member = currentRoleChangeMember.value
  roleChangeLoading.value = true

  try {
    const response = await request({
      url: `/project-members/${currentProject.value.id}/users/${member.userId}`,
      method: 'put',
      params: { role: roleChangeForm.role }
    })

    if (response.code === 200) {
      ElMessage.success(t('project.roleUpdated'))
      roleChangeDialogVisible.value = false
      fetchProjectMembers(currentProject.value.id)
    } else {
      ElMessage.error(t('project.roleUpdateFailed'))
    }
  } catch (error) {
    console.error('修改角色失败:', error)
    ElMessage.error(t('project.roleUpdateFailed'))
  } finally {
    roleChangeLoading.value = false
  }
}

const handleManageMembers = async (row) => {
  currentProject.value = row
  memberDialogVisible.value = true
  await fetchProjectMembers(row.id)
}

const searchUsers = async (query) => {
  if (!query) {
    userOptions.value = []
    return
  }

  userSearchLoading.value = true
  try {
    const response = await request({
      url: '/users/search',
      method: 'get',
      params: { keyword: query }
    })
    if (response.code === 200) {
      userOptions.value = response.data || []
    }
  } catch (error) {
    console.error('搜索用户失败:', error)
  } finally {
    userSearchLoading.value = false
  }
}

const fetchProjectMembers = async (projectId) => {
  memberLoading.value = true
  try {
    const response = await request({
      url: `/project-members/${projectId}`,
      method: 'get'
    })
    if (response.code === 200) {
      memberList.value = response.data || []
    } else {
      ElMessage.error(t('project.fetchMembersFailed'))
    }
  } catch (error) {
    console.error('获取成员列表失败:', error)
    ElMessage.error(t('project.fetchMembersFailed'))
  } finally {
    memberLoading.value = false
  }
}

const handleAddMember = () => {
  addMemberDialogTitle.value = t('project.addMember')
  memberForm.userId = null
  memberForm.role = 'MEMBER'
  userOptions.value = []
  addMemberDialogVisible.value = true
}

const handleMemberSubmit = async () => {
  try {
    await memberFormRef.value.validate()
    memberSubmitLoading.value = true

    const addResponse = await request({
      url: `/project-members/${currentProject.value.id}/users/${memberForm.userId}?role=${memberForm.role}`,
      method: 'post'
    })

    if (addResponse.code === 200) {
      ElMessage.success(t('project.addMemberSuccess'))
      addMemberDialogVisible.value = false
      fetchProjectMembers(currentProject.value.id)
    } else {
      ElMessage.error(addResponse.message || t('project.addMemberFail'))
    }
  } catch (error) {
    console.error('添加成员失败:', error)
    ElMessage.error(t('project.addMemberFailed'))
  } finally {
    memberSubmitLoading.value = false
  }
}

const handleUpdateMemberRole = async (member) => {
  const userName = member.username || `${t('project.username')}${member.userId}`
  currentRoleChangeMember.value = member
  roleChangeForm.role = member.role === 'ADMIN' ? 'ADMIN' : 'MEMBER'
  roleChangeDialogTitle.value = t('project.modifyRoleTitle', { name: userName })
  roleChangeDialogVisible.value = true
}

const handleRemoveMember = async (member) => {
  try {
    const userName = member.username || `${t('project.username')}${member.userId}`

    await ElMessageBox.confirm(
      t('project.confirmRemove', { name: userName }),
      t('common.warning'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'error',
        confirmButtonClass: 'el-button--danger'
      }
    )

    const response = await request({
      url: `/project-members/${currentProject.value.id}/users/${member.userId}`,
      method: 'delete'
    })

    if (response.code === 200) {
      ElMessage.success(t('project.removeSuccess'))
      fetchProjectMembers(currentProject.value.id)
    } else {
      ElMessage.error(t('project.removeFailed'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('移除成员失败:', error)
      ElMessage.error(t('project.removeMemberFailed'))
    }
  }
}

const handleMemberDialogClose = () => {
  memberList.value = []
  currentProject.value = null
}

// ====== Swagger 导入相关 ======
const importDialogVisible = ref(false)
const importLoading = ref(false)
const importTab = ref('file')
const importUrl = ref('')
const importFile = ref(null)
const uploadRef = ref(null)
const currentImportProject = ref(null)

// ====== Swagger 导出相关 ======
const swaggerExportDialogVisible = ref(false)
const swaggerExportLoading = ref(false)
const swaggerExportVersion = ref('3.0')
const currentExportProject = ref(null)

const handleExportSwagger = (row) => {
  currentExportProject.value = row
  swaggerExportVersion.value = '3.0'
  swaggerExportDialogVisible.value = true
}

const handleSwaggerExportConfirm = async () => {
  if (!currentExportProject.value) return
  swaggerExportLoading.value = true
  try {
    await exportSwagger(currentExportProject.value.id, swaggerExportVersion.value)
    ElMessage.success(t('project.exportSuccess'))
    swaggerExportDialogVisible.value = false
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error(t('project.exportFailed'))
  } finally {
    swaggerExportLoading.value = false
  }
}

// ====== 项目数据导出 ======
const handleExportData = async (row) => {
  try {
    await exportProjectData(row.id)
    ElMessage.success(t('project.exportSuccess'))
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error(t('project.exportFailed'))
  }
}

// ====== 项目数据导入 ======
const importDataDialogVisible = ref(false)
const importDataLoading = ref(false)
const importDataFile = ref(null)
const importDataMode = ref('merge')
const dataUploadRef = ref(null)
const importDataConflictInfo = ref(null) // 冲突的项目信息 { name, code, id }

const handleImportData = () => {
  importDataFile.value = null
  importDataMode.value = 'merge'
  importDataConflictInfo.value = null
  importDataDialogVisible.value = true
}

const handleDataFileChange = async (file) => {
  importDataFile.value = file.raw
  importDataConflictInfo.value = null

  // 解析 JSON 检测项目是否已存在
  try {
    const text = await file.raw.text()
    const data = JSON.parse(text)
    if (data && data.project && data.project.code) {
      const code = data.project.code
      // 查找当前项目列表中是否有相同 code 的项目
      const existing = projectList.value.find(p => p.code === code)
      if (existing) {
        importDataConflictInfo.value = {
          name: existing.name,
          code: existing.code,
          id: existing.id
        }
        importDataMode.value = 'merge'
      }
    }
  } catch (e) {
    // 解析失败不阻止，由后端处理
    console.warn('解析导入文件失败:', e)
  }
}

const handleDataFileRemove = () => {
  importDataFile.value = null
  importDataConflictInfo.value = null
}

const handleImportDataSubmit = async () => {
  if (!importDataFile.value) {
    ElMessage.warning(t('project.pleaseSelectFile'))
    return
  }
  importDataLoading.value = true
  try {
    const res = await importProjectData(null, importDataFile.value, importDataMode.value)
    if (res.code === 200) {
      ElMessage.success(t('project.importDataSuccess', { count: res.data?.successCount || 0 }))
      importDataDialogVisible.value = false
      fetchProjects()
    } else {
      ElMessage.error(res.message || t('project.importDataFailed'))
    }
  } catch (error) {
    console.error('导入失败:', error)
    ElMessage.error(t('project.importDataFailed'))
  } finally {
    importDataLoading.value = false
  }
}

const handleImportDataDialogClose = () => {
  importDataFile.value = null
  importDataConflictInfo.value = null
  dataUploadRef.value?.clearFiles()
}

const importResultVisible = ref(false)
const importResult = reactive({
  total: 0,
  success: 0,
  failed: 0,
  skipped: 0,
  errors: [],
  conflicts: []
})

// 冲突处理
const conflictSelected = ref([])
const conflictSelectAll = ref(false)
const conflictResolving = ref(false)

const handleImportSwagger = (row) => {
  currentImportProject.value = row
  importTab.value = 'file'
  importUrl.value = ''
  importFile.value = null
  importResult.total = 0
  importResult.success = 0
  importResult.failed = 0
  importResult.skipped = 0
  importResult.errors = []
  importResult.conflicts = []
  importDialogVisible.value = true
}

const handleFileChange = (file) => {
  importFile.value = file.raw
}

const handleFileRemove = () => {
  importFile.value = null
}

const handleImportSubmit = async () => {
  if (importTab.value === 'file') {
    if (!importFile.value) {
      ElMessage.warning(t('project.pleaseSelectFile'))
      return
    }
    await importFromFile()
  } else {
    if (!importUrl.value || !importUrl.value.trim()) {
      ElMessage.warning(t('project.pleaseEnterUrl'))
      return
    }
    await importFromUrl()
  }
}

const importFromFile = async () => {
  importLoading.value = true
  try {
    const formData = new FormData()
    formData.append('file', importFile.value)

    const response = await request({
      url: `/projects/${currentImportProject.value.id}/import-swagger-file`,
      method: 'post',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })

    if (response.code === 200) {
      Object.assign(importResult, response.data)
      initConflictSelection()
      importDialogVisible.value = false
      importResultVisible.value = true
    } else {
      ElMessage.error(response.message || t('project.importFailed'))
    }
  } catch (error) {
    console.error('导入失败:', error)
    ElMessage.error(t('project.importFailed'))
  } finally {
    importLoading.value = false
  }
}

const importFromUrl = async () => {
  importLoading.value = true
  try {
    const response = await request({
      url: `/projects/${currentImportProject.value.id}/import-swagger-url`,
      method: 'post',
      data: { url: importUrl.value.trim() }
    })

    if (response.code === 200) {
      Object.assign(importResult, response.data)
      initConflictSelection()
      importDialogVisible.value = false
      importResultVisible.value = true
    } else {
      ElMessage.error(response.message || t('project.importFailed'))
    }
  } catch (error) {
    console.error('导入失败:', error)
    ElMessage.error(t('project.importFailed'))
  } finally {
    importLoading.value = false
  }
}

const onImportDone = () => {
  importResultVisible.value = false
  // 导入完成后跳转到接口管理页面查看结果
  if (currentImportProject.value && importResult.success > 0) {
    router.push(`/apis?projectId=${currentImportProject.value.id}`)
  }
}

const handleImportDialogClose = () => {
  importFile.value = null
  importUrl.value = ''
  uploadRef.value?.clearFiles()
}

const handleAddMemberDialogClose = () => {
  memberFormRef.value?.resetFields()
}

// 截断过长文本用于展示
const truncateValue = (val) => {
  if (!val) return '-'
  const str = String(val)
  if (str.length > 80) return str.substring(0, 77) + '...'
  return str
}

// ====== 冲突解决 ======
const initConflictSelection = () => {
  if (importResult.conflicts && importResult.conflicts.length > 0) {
    conflictSelected.value = new Array(importResult.conflicts.length).fill(false)
    conflictSelectAll.value = false
  } else {
    conflictSelected.value = []
    conflictSelectAll.value = false
  }
}

const handleConflictSelectAll = (val) => {
  if (importResult.conflicts) {
    conflictSelected.value = new Array(importResult.conflicts.length).fill(val)
  }
}

const handleConflictResolve = async () => {
  const selectedConflicts = importResult.conflicts.filter((_, idx) => conflictSelected.value[idx])
  if (selectedConflicts.length === 0) {
    ElMessage.warning(t('project.importConflictNoSelect'))
    return
  }

  conflictResolving.value = true
  try {
    const requests = selectedConflicts.map(c => {
      const req = { existingApiId: c.existingApiId }
      if (c.changes) {
        c.changes.forEach(change => {
          switch (change.field) {
            case 'name':
              req.newName = change.newValue
              break
            case 'description':
              req.newDescription = change.newValue
              break
            case 'method':
              req.newMethod = change.newValue
              break
            case 'responseBody':
              req.newResponseBody = change.newValue
              break
            case 'requestParams':
              req.newRequestParamsJson = change.newValue
              break
          }
        })
      }
      return req
    })

    const response = await request({
      url: `/projects/${currentImportProject.value.id}/import-conflicts/resolve`,
      method: 'post',
      data: requests
    })

    if (response.code === 200) {
      const resolved = response.data?.resolved || 0
      ElMessage.success(t('project.importConflictResolved', { count: resolved }))
      // 从冲突列表中移除已解决的项
      importResult.conflicts = importResult.conflicts.filter((_, idx) => !conflictSelected.value[idx])
      initConflictSelection()
    } else {
      ElMessage.error(response.message || t('project.importConflictResolveFailed'))
    }
  } catch (error) {
    console.error('冲突解决失败:', error)
    ElMessage.error(t('project.importConflictResolveFailed'))
  } finally {
    conflictResolving.value = false
  }
}

const canManageMembers = (project) => {
  if (canEditProject(project)) return true
  return false
}

// 注意: canEditProject 和 canDeleteProject 已在 computed 区域重新定义（见上方）

onMounted(async () => {
  await loadDateFormat()
  fetchProjects()
})
</script>

<style scoped>
.projects {
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

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-card {
  margin-bottom: 20px;
}

.table-card {
  margin-bottom: 20px;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

:deep(.stat-card) {
  margin-bottom: 0;
}

.member-header {
  padding: 12px 0;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 16px;
}

/* ====== Swagger 导入 ====== */
.import-tabs {
  margin-top: 4px;
}

.import-upload {
  padding: 20px 0;
}

.import-upload .upload-icon {
  font-size: 48px;
  color: #c0c4cc;
  margin-bottom: 8px;
}

.upload-text p {
  margin: 4px 0;
  color: #606266;
  font-size: 14px;
}

.upload-sub {
  color: #909399 !important;
  font-size: 12px !important;
}

.import-url-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}

/* 导入结果 */
.import-result {
  padding: 8px 0;
}

.result-summary {
  display: flex;
  gap: 24px;
  margin-bottom: 16px;
}

.result-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 500;
}

.result-item.success {
  color: #67c23a;
}

.result-item.skipped {
  color: #909399;
}

.result-item.failed {
  color: #f56c6c;
}

.result-item .el-icon {
  font-size: 20px;
}

.result-errors {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 12px 16px;
  max-height: 240px;
  overflow-y: auto;
}

.result-errors h4 {
  margin: 0 0 10px;
  font-size: 13px;
  color: #909399;
}

.error-item {
  display: flex;
  gap: 10px;
  padding: 6px 0;
  font-size: 13px;
  border-bottom: 1px solid #ebeef5;
}

.error-item:last-child {
  border-bottom: none;
}

.error-method {
  display: inline-block;
  padding: 1px 6px;
  background: #e6f0ff;
  color: #409eff;
  border-radius: 3px;
  font-weight: 600;
  font-size: 11px;
  min-width: 44px;
  text-align: center;
  flex-shrink: 0;
}

.error-path {
  color: #303133;
  word-break: break-all;
  flex: 1;
}

.error-reason {
  color: #f56c6c;
  flex-shrink: 0;
  font-size: 12px;
}

/* 冲突处理 */
.result-conflicts {
  margin-bottom: 16px;
}

.result-conflicts h4 {
  margin: 0 0 6px;
  font-size: 14px;
  color: #e6a23c;
}

.conflict-hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.conflict-table {
  background: #fef9e7;
  border: 1px solid #faecd8;
  border-radius: 8px;
  padding: 12px 14px;
  max-height: 280px;
  overflow-y: auto;
}

.conflict-header {
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #faecd8;
}

.conflict-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid #f5e6cc;
}

.conflict-row:last-child {
  border-bottom: none;
}

.conflict-checkbox {
  flex-shrink: 0;
  margin-top: 2px;
}

.conflict-info {
  flex: 1;
  min-width: 0;
}

.conflict-path {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
}

.conflict-diff {
  display: flex;
  gap: 20px;
  font-size: 12px;
}

.conflict-col {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.conflict-label {
  color: #b0b0b0;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.conflict-val {
  color: #606266;
  word-break: break-all;
}

.conflict-val.new-val {
  color: #409eff;
  font-weight: 500;
}

/* 变更详情列表 */
.conflict-changes {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.change-item {
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 8px 12px;
}

.change-field-tag {
  display: inline-block;
  background: #e6f7ff;
  color: #409eff;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 3px;
  margin-bottom: 6px;
}

.change-values {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.change-value-row {
  display: flex;
  gap: 8px;
  font-size: 12px;
  align-items: flex-start;
}

.change-arrow-label {
  color: #b0b0b0;
  flex-shrink: 0;
  min-width: 36px;
  font-size: 11px;
}

.change-val {
  word-break: break-all;
  flex: 1;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 11px;
  line-height: 1.5;
  max-height: 40px;
  overflow: hidden;
}

.change-val.old-val {
  color: #909399;
  text-decoration: line-through;
}

.change-val.new-val {
  color: #409eff;
}
</style>

