<!--
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
-->

<template>
  <div class="apis">
    <div class="page-header">
      <h1>{{ $t('api.title') }}</h1>
      <div class="header-actions">
        <el-button @click="fetchApis" :loading="loading" style="margin-right: 10px">
          <Refresh :width="'1em'" :height="'1em'" />
          {{ $t('api.refresh') }}
        </el-button>
        <el-button @click="handleExportMarkdown" :disabled="selectedApis.length === 0" style="margin-right: 10px">
          <Document :width="'1em'" :height="'1em'" />
          {{ $t('api.exportMarkdown') }}
        </el-button>
        <el-button type="primary" @click="handleCreate" v-if="canCreateApi">
          <Plus :width="'1em'" :height="'1em'" />
          {{ $t('api.createApi') }}
        </el-button>
      </div>
    </div>

    <el-card class="search-card">
      <el-row :gutter="20">
        <el-col :span="4">
          <el-input v-model="searchForm.name" :placeholder="$t('api.searchByName')" clearable @clear="handleSearch" />
        </el-col>
        <el-col :span="5">
          <el-input v-model="searchForm.path" :placeholder="$t('api.searchByPath')" clearable @clear="handleSearch" />
        </el-col>
        <el-col :span="4">
          <el-select v-model="searchForm.projectId" :placeholder="$t('api.searchByProject')" clearable @change="handleSearch" style="width: 100%" filterable>
            <el-option
              v-for="project in projectList"
              :key="project.id"
              :label="project.name"
              :value="project.id"
            />
          </el-select>
        </el-col>
        <el-col :span="3">
          <el-select v-model="searchForm.method" :placeholder="$t('api.searchByMethod')" clearable @change="handleSearch" style="width: 100%">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
            <el-option label="PATCH" value="PATCH" />
          </el-select>
        </el-col>
        <el-col :span="3">
          <el-select v-model="searchForm.enabled" :placeholder="$t('api.searchByStatus')" clearable @change="handleSearch" style="width: 100%">
            <el-option :label="$t('api.enabledStatus')" :value="true" />
            <el-option :label="$t('api.disabledStatus')" :value="false" />
          </el-select>
        </el-col>
        <el-col :span="5">
          <el-button type="primary" @click="handleSearch">{{ $t('api.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('api.reset') }}</el-button>
        </el-col>
      </el-row>
    </el-card>

    <el-card class="table-card">
      <el-table
        v-loading="loading"
        :data="apiList"
        border
        style="width: 100%"
        :header-cell-style="{ background: '#f5f7fa' }"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" fixed />
        <el-table-column prop="id" label="ID" width="80" fixed />
        <el-table-column prop="project.name" :label="$t('api.project')" min-width="120" show-overflow-tooltip fixed />
        <el-table-column prop="name" :label="$t('api.apiName')" min-width="150" fixed />
        <el-table-column :label="$t('api.apiPath')" min-width="360" show-overflow-tooltip fixed>
          <template #default="{ row }">
            <div class="path-cell">
              <el-button
                type="primary"
                link
                @click="handleCopyPath(row)"
                style="font-family: monospace;"
              >
                /api/api-server/{{ row.project?.code }}{{ row.path }}
              </el-button>
              <el-tooltip :content="$t('api.copyPath')" placement="top">
                <el-button
                  class="copy-btn"
                  size="small"
                  circle
                  @click.stop="handleCopyPath(row)"
                >
                  <el-icon><CopyDocument /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="method" :label="$t('api.apiMethod')" width="100">
          <template #default="{ row }">
            <el-tag :type="getMethodTagType(row.method)">
              {{ row.method }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="requestType" :label="$t('api.apiRequestType')" width="100">
          <template #default="{ row }">
            <el-tag type="info" size="small">
              {{ row.requestType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" :label="$t('api.description')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="enabled" :label="$t('api.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? $t('api.enabledStatus') : $t('api.disabledStatus') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="responseDelay" :label="$t('api.delayMs')" width="100" align="center" />
        <el-table-column :label="$t('api.actions')" width="120" fixed="right">
          <template #default="{ row }">
            <el-dropdown trigger="click" @command="(cmd) => handleApiAction(cmd, row)">
              <el-button type="primary" link>
                {{ $t('common.more') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="edit" :disabled="!canEditApi">{{ $t('api.edit') }}</el-dropdown-item>
                  <el-dropdown-item command="responses" :disabled="!canEditApi">{{ $t('api.manageResponses') }}</el-dropdown-item>
                  <el-dropdown-item command="delete" :disabled="!canDeleteApi" divided style="color: #f56c6c;">{{ $t('api.delete') }}</el-dropdown-item>
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

    <!-- 创建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="900px" @close="handleDialogClose">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item :label="$t('api.selectProject')" prop="projectId" v-if="!isEdit">
          <el-select v-model="form.projectId" :placeholder="$t('api.selectProject')" clearable style="width: 100%" filterable>
            <el-option
              v-for="project in accessibleProjects"
              :key="project.id"
              :label="project.name"
              :value="project.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('api.apiName')" prop="name">
          <el-input v-model="form.name" :placeholder="$t('api.nameRequired')" />
        </el-form-item>
        <el-form-item :label="$t('api.apiPath')" prop="path">
          <el-input v-model="form.path" placeholder="例如: /api/user/login 或 /api/user/{userId}" :disabled="isEdit">
            <template #append>
              <el-tooltip content="支持RESTful风格，例如 /api/user/{userId}，其中userId为参数名" placement="top">
                <el-icon><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item :label="$t('api.apiMethod')" prop="method">
          <el-select v-model="form.method" :placeholder="$t('api.methodRequired')" style="width: 100%">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
            <el-option label="PATCH" value="PATCH" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('api.apiRequestType')" prop="requestType">
          <el-select v-model="form.requestType" :placeholder="$t('api.requestTypeRequired')" style="width: 100%">
            <el-option label="HTTP" value="HTTP" />
            <el-option label="WEBSOCKET" value="WEBSOCKET" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('api.apiDescription')" prop="description">
          <div style="display: flex; gap: 8px; width: 100%;">
            <el-input v-model="form.description" type="textarea" :rows="3" :placeholder="$t('api.apiDescription')" style="flex: 1;" />
            <el-button type="warning" @click="handleAiGenerateDescription" :loading="aiDescLoading" :disabled="!form.name || !form.path" style="flex-shrink: 0; align-self: flex-start;">
              <MagicStick :width="'1em'" :height="'1em'" style="margin-right: 4px;" />
              {{ aiDescLoading ? $t('ai.generatingDescription') : $t('ai.generateDescription') }}
            </el-button>
          </div>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$t('api.responseDelay')" prop="responseDelay">
              <el-input-number v-model="form.responseDelay" :min="0" :max="5000" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('api.status')" prop="enabled">
              <el-switch v-model="form.enabled" :active-text="$t('api.enabledStatus')" :inactive-text="$t('api.disabledStatus')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$t('api.enableRandom')" prop="enableRandom">
          <el-switch
            v-model="form.enableRandom"
            active-text="是"
            inactive-text="否"
            :disabled="hasMultipleActiveResponses"
          />
          <div v-if="hasMultipleActiveResponses" style="color: #909399; font-size: 12px; margin-top: 4px; display: inline-block; margin-left: 12px;">
            <el-icon><WarningFilled /></el-icon> {{ $t('api.enableRandomHint') }}
          </div>
          <div v-else-if="hasMultipleActiveResponses && form.enableRandom" style="color: #909399; font-size: 12px; margin-top: 4px; display: inline-block; margin-left: 12px;">
            <el-icon><WarningFilled /></el-icon> {{ $t('api.randomWarning') }}
          </div>
        </el-form-item>

        <!-- 自定义响应处理器（仅编辑时显示） -->
        <template v-if="isEdit">
        <el-divider content-position="left">
          <span style="font-size: 14px; font-weight: 600; color: #303133;">{{ $t('api.customHandlerTitle') }}</span>
        </el-divider>
        <div style="margin-bottom: 12px; padding: 10px; background: #fdf6ec; border: 1px solid #faecd8; border-radius: 4px;">
          <el-icon style="color: #E6A23C; margin-right: 6px;"><WarningFilled /></el-icon>
          <span style="color: #E6A23C; font-size: 13px;" v-html="$t('api.customHandlerHint')"></span>
        </div>
        <div style="margin-bottom: 8px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap;">
          <el-select
            v-model="selectedTemplateId"
            :placeholder="$t('api.selectTemplate')"
            clearable
            filterable
            size="small"
            style="width: 280px;"
            @change="handleTemplateSelect"
          >
            <el-option
              v-for="tpl in projectTemplates"
              :key="tpl.id"
              :label="tpl.name"
              :value="tpl.id"
              :disabled="tpl.isSystem && !isEdit"
            >
              <span>
                <el-tag v-if="tpl.isSystem" type="danger" size="small" effect="dark" style="margin-right: 6px;">{{ $t('codeTemplate.systemDefault') }}</el-tag>
                {{ tpl.name }}
              </span>
              <span style="float: right; color: #909399; font-size: 12px;">{{ tpl.project?.name }}</span>
            </el-option>
          </el-select>
          <el-button size="small" type="success" @click="validateCustomCode" :loading="validatingCode">{{ $t('api.compileValidate') }}</el-button>
          <el-button size="small" type="warning" @click="clearCustomCode">{{ $t('api.clearCode') }}</el-button>
          <span v-if="validationResult" :style="{ color: validationResult.success ? '#67C23A' : '#F56C6C', fontSize: '13px', marginLeft: '8px' }">
            {{ validationResult.success ? $t('api.validatePassed') : $t('api.validateFailed') + validationResult.message }}
          </span>
          <el-button size="small" style="margin-left: auto;" @click="apiCodeFullscreen = !apiCodeFullscreen">
            {{ apiCodeFullscreen ? $t('common.exitFullscreen') : $t('common.fullscreen') }}
          </el-button>
        </div>
        <el-form-item prop="customResponseSource" label-width="0" :class="{ 'api-code-fullscreen': apiCodeFullscreen }">
          <div v-if="apiCodeFullscreen" class="api-fullscreen-toolbar">
            <el-select
              v-model="selectedTemplateId"
              :placeholder="$t('api.selectTemplate')"
              clearable filterable size="small" style="width: 280px;"
              @change="handleTemplateSelect"
            >
              <el-option v-for="tpl in projectTemplates" :key="tpl.id" :label="tpl.name" :value="tpl.id">
                <span>
                  <el-tag v-if="tpl.isSystem" type="danger" size="small" effect="dark" style="margin-right: 6px;">{{ $t('codeTemplate.systemDefault') }}</el-tag>
                  {{ tpl.name }}
                </span>
                <span style="float: right; color: #909399; font-size: 12px;">{{ tpl.project?.name }}</span>
              </el-option>
            </el-select>
            <el-button size="small" type="success" @click="validateCustomCode" :loading="validatingCode">{{ $t('api.compileValidate') }}</el-button>
            <el-button size="small" type="warning" @click="clearCustomCode">{{ $t('api.clearCode') }}</el-button>
            <el-button size="small" style="margin-left: auto;" @click="apiCodeFullscreen = false">{{ $t('common.exitFullscreen') }}</el-button>
          </div>
          <MonacoEditor
            v-model="form.customResponseSource"
            :read-only="!isEdit"
            :height="apiCodeFullscreen ? 'calc(100vh - 60px)' : '420px'"
          />
        </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 响应管理对话框 -->
    <el-dialog v-model="responseDialogVisible" :title="$t('api.responseTitle', { name: currentApi?.name || '' })" width="70%" @close="handleResponseDialogClose">
      <div class="response-status-tip" style="margin-bottom: 16px; padding: 12px; background: #f0f9ff; border: 1px solid #b3d8ff; border-radius: 4px;">
        <el-icon><InfoFilled /></el-icon>
        <span style="margin-left: 8px; color: #409EFF; font-size: 14px;">
          {{ $t('api.responseTip') }}
        </span>
      </div>

      <div class="response-header">
        <el-row :gutter="20">
          <el-col :span="14">
            <span style="color: #909399; font-size: 14px;">
              <span>{{ $t('api.apiPathInfo') }}</span>
              <span style="color: #303133; font-weight: 600;">/api/api-server/{{ currentApi?.project?.code }}{{ currentApi?.path }}</span>
              <span style="margin-left: 20px;">{{ $t('api.method') }}</span>
              <el-tag :type="getMethodTagType(currentApi?.method)">{{ currentApi?.method }}</el-tag>
            </span>
          </el-col>
          <el-col :span="10" style="text-align: right;">
            <el-button type="warning" size="small" @click="handleAiGenerateDialog">
              <MagicStick :width="'1em'" :height="'1em'" />
              {{ $t('ai.generateResponse') }}
            </el-button>
            <el-button type="primary" size="small" @click="handleAddResponse">
              <Plus :width="'1em'" :height="'1em'" />
              {{ $t('api.addResponse') }}
            </el-button>
            <el-button type="info" size="small" @click="templateDocDialogVisible = true" v-if="canUseTemplateEngine">
              <el-icon><Document /></el-icon>
              {{ $t('api.templateDoc') }}
            </el-button>
          </el-col>
        </el-row>
      </div>

      <!-- 响应列表 -->
      <el-table
        v-loading="responseLoading"
        :data="responseList"
        border
        style="width: 100%; margin-top: 16px;"
        :header-cell-style="{ background: '#f5f7fa' }"
        :row-class-name="getResponseRowClass"
        row-key="id"
      >
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="statusCode" :label="$t('api.statusCode')" width="80" />
        <el-table-column :label="$t('api.responseContent')" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.responseBody }}
          </template>
        </el-table-column>
        <el-table-column prop="contentType" :label="$t('api.contentType')" width="130" />
        <el-table-column prop="weight" :label="$t('api.weight')" width="70" align="center" />
        <el-table-column prop="responseDelay" :label="$t('api.delayMs')" width="80" align="center" />
        <el-table-column prop="isDefault" :label="$t('api.responseDefault')" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isDefault ? 'warning' : 'info'" size="small">
              {{ row.isDefault ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" :label="$t('api.responseEnabled')" width="70" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              @change="handleUpdateResponse(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="active" :label="$t('api.responseActive')" width="70" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.active"
              @change="handleToggleActive(row)"
            />
          </template>
        </el-table-column>
        <el-table-column :label="$t('api.responseActions')" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              size="small"
              @click="handleEditResponse(row)"
            >
              {{ $t('api.edit') }}
            </el-button>
            <el-button
              type="success"
              link
              size="small"
              @click="handleRequestParams(row)"
            >
              {{ $t('api.params') }}
            </el-button>
            <el-button
              type="danger"
              link
              size="small"
              @click="handleDeleteResponse(row)"
            >
              {{ $t('api.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 添加/编辑响应对话框 -->
    <el-dialog v-model="responseFormDialogVisible" :title="responseFormTitle" width="700px" @close="handleResponseFormDialogClose">
      <el-form ref="responseFormRef" :model="responseForm" :rules="responseRules" label-width="100px">
        <el-form-item :label="$t('api.statusCode')" prop="statusCode">
          <el-input-number v-model="responseForm.statusCode" :min="100" :max="599" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('api.contentType')" prop="contentType">
          <el-select v-model="responseForm.contentType" :placeholder="$t('api.contentType')" style="width: 100%">
            <el-option label="application/json" value="application/json" />
            <el-option label="text/html" value="text/html" />
            <el-option label="text/plain" value="text/plain" />
            <el-option label="application/xml" value="application/xml" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('api.headers')" prop="headers">
          <el-input v-model="responseForm.headers" type="textarea" :rows="2" placeholder='{"X-Custom-Header": "value"}' />
        </el-form-item>
        <el-form-item :label="$t('api.responseBody')" prop="responseBody">
          <div style="display: flex; flex-direction: column; gap: 8px; width: 100%;">
            <el-input v-model="responseForm.responseBody" ref="responseBodyInputRef" type="textarea" :rows="8" :placeholder="$t('api.responseBody')" />
            <div v-if="canUseTemplateEngine" style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap;">
              <el-button size="small" type="info" @click="showTemplateHelper = !showTemplateHelper">
                <el-icon><MagicStick /></el-icon>
                {{ showTemplateHelper ? $t('api.hideTemplateHelper') : $t('api.showTemplateHelper') }}
              </el-button>
              <el-button size="small" type="success" @click="handleTemplatePreview" :loading="templatePreviewLoading">
                {{ $t('api.previewTemplate') }}
              </el-button>
            </div>
            <!-- 模板函数帮助面板 -->
            <div v-if="showTemplateHelper && canUseTemplateEngine" style="max-height: 200px; overflow-y: auto; border: 1px solid #e4e7ed; border-radius: 4px; padding: 8px; background: #fafafa;">
              <div style="font-size: 12px; color: #909399; margin-bottom: 6px;">{{ $t('api.templateHelperTitle') }}</div>
              <div style="display: flex; flex-wrap: wrap; gap: 4px;">
                <el-tag
                  v-for="(desc, func) in templateFunctions"
                  :key="func"
                  size="small"
                  style="cursor: pointer;"
                  @click="insertTemplate(func)"
                >
                  {{ func }}
                </el-tag>
              </div>
            </div>
            <!-- 模板预览结果已改为弹窗展示 -->
          </div>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$t('api.weight')" prop="weight">
              <el-input-number v-model="responseForm.weight" :min="0" :max="100" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('api.responseEnabled')" prop="enabled">
              <el-switch v-model="responseForm.enabled" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$t('api.responseDelay')" prop="responseDelay">
              <el-input-number v-model="responseForm.responseDelay" :min="0" :max="60000" placeholder="0" style="width: 100%">
                <template #append>ms</template>
              </el-input-number>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('api.defaultResponse')" prop="isDefault">
              <el-switch v-model="responseForm.isDefault" />
              <span style="margin-left: 10px; font-size: 12px; color: #909399;">{{ $t('api.responseStatusTip') }}</span>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="responseFormDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="responseSubmitLoading" @click="handleResponseSubmit">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- AI 生成响应对话框 -->
    <el-dialog v-model="aiGenerateDialogVisible" :title="$t('ai.generateResponseTitle')" width="70%" top="3vh" @close="handleAiGenerateDialogClose">
      <!-- 生成配置 -->
      <el-form label-width="90px" class="ai-generate-form" size="default">
        <el-row :gutter="20" align="middle">
          <el-col :span="24">
            <el-form-item :label="$t('ai.generateCount')" label-width="90px">
              <div style="display: flex; align-items: center; gap: 16px;">
                <el-input-number v-model="aiGenCount" :min="1" :max="5" :disabled="aiGenLoading" size="default" style="width: 120px" />
                <el-button type="primary" @click="handleAiGenerate" :loading="aiGenLoading">
                  <MagicStick :width="'1em'" :height="'1em'" style="margin-right: 4px;" />
                  {{ aiGenLoading ? $t('ai.generating') : $t('ai.startGenerate') }}
                </el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item :label="$t('ai.styleDesc')" label-width="90px">
              <el-input v-model="aiGenStyle" type="textarea" :rows="1" :placeholder="$t('ai.styleDescPlaceholder')" :disabled="aiGenLoading" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 生成结果表格 -->
      <div v-if="aiGenResults.length > 0" class="ai-result-table">
        <el-table :data="aiGenResults" border style="width: 100%" :header-cell-style="{ background: '#f5f7fa', color: '#303133', fontWeight: 600 }" row-key="id">
          <el-table-column type="index" :label="$t('ai.no')" width="55" align="center" />
          <el-table-column :label="$t('api.statusCode')" width="120" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.statusCode" :min="100" :max="599" size="small" controls-position="right" style="width: 110px" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('api.responseBody')" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="response-body-cell">{{ row.responseBody }}</div>
            </template>
          </el-table-column>
          <el-table-column :label="$t('api.contentType')" width="185" align="center">
            <template #default="{ row }">
              <el-select v-model="row.contentType" size="small" style="width: 175px">
                <el-option label="application/json" value="application/json" />
                <el-option label="text/html" value="text/html" />
                <el-option label="text/plain" value="text/plain" />
                <el-option label="application/xml" value="application/xml" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$t('api.responseDelay')" width="150" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.responseDelay" :min="0" :max="60000" size="small" controls-position="right" style="width: 140px">
                <template #suffix>ms</template>
              </el-input-number>
            </template>
          </el-table-column>
          <el-table-column :label="$t('api.weight')" width="110" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.weight" :min="0" :max="100" size="small" controls-position="right" style="width: 100px" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('api.actions')" width="100" align="center" fixed="right">
            <template #default="{ row, $index }">
              <el-button type="primary" size="small" :loading="row._applying" @click="handleApplyAiResponse(row, $index)">
                {{ $t('ai.apply') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <el-button @click="aiGenerateDialogVisible = false">{{ $t('common.close') }}</el-button>
      </template>
    </el-dialog>

    <!-- 请求参数管理对话框 -->
    <el-dialog v-model="requestParamDialogVisible" :title="$t('api.paramTitle', { statusCode: currentResponse?.statusCode || '' })" width="80%" @close="handleRequestParamDialogClose">
      <div style="margin-bottom: 16px;">
        <el-alert
          :title="$t('common.info')"
          type="info"
          :closable="false"
          style="margin-bottom: 12px;">
          <template #default>
            <p style="margin: 4px 0;">• PATH: RESTful路径参数，如 /api/users/{userId}</p>
            <p style="margin: 4px 0;">• QUERY: URL查询参数，如 ?userId=123</p>
            <p style="margin: 4px 0;">• REQUEST_BODY: 请求体参数，如 JSON body中的字段</p>
            <p style="margin: 4px 0;">• HEADER: 请求头参数</p>
            <p style="margin: 4px 0;">• FILE: 文件上传参数</p>
          </template>
        </el-alert>
        <el-button type="primary" @click="handleAddRequestParam">
          <Plus :width="'1em'" :height="'1em'" />
          {{ $t('api.addParam') }}
        </el-button>
      </div>

      <el-table
        v-loading="requestParamLoading"
        :data="requestParamList"
        border
        style="width: 100%;"
        :header-cell-style="{ background: '#f5f7fa' }"
      >
        <el-table-column prop="paramName" :label="$t('api.paramName')" width="150" />
        <el-table-column prop="paramType" :label="$t('api.paramTypeLabel')" width="130">
          <template #default="{ row }">
            <el-tag :type="getParamTypeTagType(row.paramType)">
              {{ getParamTypeLabel(row.paramType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="paramValue" :label="$t('api.paramValueLabel')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="required" :label="$t('api.paramRequired')" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.required ? 'danger' : 'info'" size="small">
              {{ row.required ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('api.paramActions')" width="100" align="center">
          <template #default="{ row }">
            <el-button
              type="danger"
              link
              size="small"
              @click="handleDeleteRequestParam(row)"
            >
              {{ $t('api.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 添加/编辑请求参数对话框 -->
    <el-dialog v-model="requestParamFormDialogVisible" :title="requestParamFormTitle" width="500px" @close="handleRequestParamFormDialogClose">
      <el-form ref="requestParamFormRef" :model="requestParamForm" :rules="requestParamRules" label-width="100px">
        <el-form-item :label="$t('api.paramName')" prop="paramName">
          <el-input v-model="requestParamForm.paramName" :placeholder="$t('api.pathPlaceholder')" @blur="handleParamNameBlur" />
          <div v-show="requestParamForm.isPathParam" style="font-size: 12px; color: #909399; margin-top: 4px;">
            {{ $t('api.paramPathHint') }}
          </div>
        </el-form-item>
        <el-form-item :label="$t('api.paramTypeLabel')" prop="paramType">
          <el-select v-model="requestParamForm.paramType" :placeholder="$t('api.paramTypeLabel')" style="width: 100%" :disabled="requestParamForm.isPathParam || requestParamForm.isQueryParam">
            <el-option label="PATH (RESTful路径)" value="PATH" />
            <el-option label="QUERY (URL查询参数)" value="QUERY" />
            <el-option label="REQUEST_BODY (请求体)" value="REQUEST_BODY" />
            <el-option label="HEADER (请求头)" value="HEADER" />
            <el-option label="FILE (文件上传)" value="FILE" />
          </el-select>
          <div v-show="requestParamForm.isPathParam" style="font-size: 12px; color: #909399; margin-top: 4px;">
            {{ $t('api.paramPathAutoHint') }}
          </div>
          <div v-show="requestParamForm.isQueryParam" style="font-size: 12px; color: #909399; margin-top: 4px;">
            {{ $t('api.paramQueryHint') }}
          </div>
        </el-form-item>
        <el-form-item :label="$t('api.paramValueLabel')" prop="paramValue">
          <el-input v-model="requestParamForm.paramValue" :placeholder="$t('api.valuePlaceholder')" :disabled="requestParamForm.paramType === 'FILE'" />
          <div v-show="requestParamForm.paramType === 'FILE'" style="font-size: 12px; color: #909399; margin-top: 4px;">
            {{ $t('api.paramFileHint') }}
          </div>
          <div v-show="requestParamForm.paramType === 'PATH'" style="font-size: 12px; color: #909399; margin-top: 4px;" v-html="$t('api.paramPathValueHint')">
          </div>
          <div v-show="requestParamForm.paramType !== 'FILE' && requestParamForm.paramType !== 'PATH'" style="font-size: 12px; color: #909399; margin-top: 4px;">
            {{ $t('api.paramMatchHint') }}
          </div>
        </el-form-item>
        <el-form-item :label="$t('api.paramRequired')" prop="required">
          <el-switch v-model="requestParamForm.required" />
          <div style="font-size: 12px; color: #909399; margin-top: 4px;">
            {{ $t('api.paramRequiredHint') }}
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="requestParamFormDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="requestParamSubmitLoading" @click="handleRequestParamSubmit">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 模板说明弹窗 -->
    <el-dialog v-model="templateDocDialogVisible" :title="$t('api.templateDocTitle')" width="700px" align-center>
      <div style="max-height: 60vh; overflow-y: auto; padding: 0 4px;">
        <el-table :data="templateFunctionsList" style="width: 100%; border-radius: 8px; overflow: hidden;" :header-cell-style="{ background: '#409EFF', color: '#fff', fontWeight: 600, fontSize: '14px' }" :cell-style="{ fontSize: '13px' }" stripe>
          <el-table-column prop="func" label="模板函数" width="200" />
          <el-table-column prop="desc" label="说明" />
        </el-table>
      </div>
    </el-dialog>

    <!-- 模板预览弹窗 -->
    <el-dialog v-model="templatePreviewDialogVisible" :title="$t('api.previewResult')" width="700px">
      <pre style="margin: 0; background: #f5f7fa; padding: 16px; border-radius: 4px; overflow-x: auto; font-family: monospace; font-size: 13px; line-height: 1.6;" v-text="templatePreviewFormattedResult"></pre>
    </el-dialog>

    <!-- 导出 Markdown 弹窗 -->
    <el-dialog v-model="exportDialogVisible" :title="$t('api.exportMarkdown')" width="560px" @close="handleExportDialogClose">
      <div class="export-dialog-body">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          :title="$t('api.exportMarkdownHint', { count: selectedApis.length })"
          style="margin-bottom: 16px;"
        />
        <el-alert
          type="info"
          :closable="false"
          :title="$t('api.exportMultiResponseHint')"
          style="margin-bottom: 16px;"
        />
        <el-form label-width="100px">
          <el-form-item :label="$t('api.aiEnhance')">
            <el-switch v-model="exportAiEnhance" />
          </el-form-item>
          <el-form-item v-if="exportAiEnhance">
            <div class="ai-enhance-tip">{{ $t('api.aiEnhanceTip') }}</div>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="exportDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="exportLoading" @click="handleExportConfirm">
          {{ exportLoading ? $t('api.exporting') : $t('api.exportConfirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Edit, Delete, InfoFilled, WarningFilled, ArrowDown, CopyDocument, MagicStick, Document } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { useRoute } from 'vue-router'
import { getAccessibleProjects, getAllAccessibleProjects } from '@/api/project'
import { getEnabledTemplatesByProjectId } from '@/api/codeTemplate'
import { generateMockResponse, generateApiDescriptionStream } from '@/api/ai'
import { exportMockApisMarkdown } from '@/api/mockApi'
import { getTemplateFunctions, previewTemplate } from '@/api/mockTemplate'
import { defineAsyncComponent } from 'vue'

const MonacoEditor = defineAsyncComponent(() => import('@/components/MonacoEditor.vue'))

const { t } = useI18n()
const route = useRoute()
const userStore = useUserStore()

// 基础 URL（协议 + 域名 + 端口），用于生成完整的接口路径
const baseUrl = window.location.origin

// 权限控制
const canCreateApi = computed(() => userStore.hasPermission('api:create'))
const canEditApi = computed(() => userStore.hasPermission('api:edit'))
const canDeleteApi = computed(() => userStore.hasPermission('api:delete'))
const canUseTemplateEngine = computed(() => userStore.hasPermission('api:template_engine'))

// 搜索表单
const searchForm = reactive({
  name: '',
  path: '',
  projectId: null,
  method: '',
  enabled: null
})

// 项目列表
const projectList = ref([])
const accessibleProjects = ref([])

// 表格数据
const loading = ref(false)
const apiList = ref([])

// 分页
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0
})

// 对话框
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref()

// 自定义代码相关
const validatingCode = ref(false)
const validationResult = ref(null)

// 代码编辑器全屏
const apiCodeFullscreen = ref(false)

// 模板下拉选择相关
const selectedTemplateId = ref(null)
const projectTemplates = ref([])

// 获取当前项目下的已启用模板列表
const fetchProjectTemplates = async (projectId) => {
  if (!projectId) {
    projectTemplates.value = []
    selectedTemplateId.value = null
    return
  }
  try {
    const response = await getEnabledTemplatesByProjectId(projectId)
    if (response.code === 200) {
      projectTemplates.value = response.data || []
    }
  } catch (error) {
    console.error('获取模板列表失败:', error)
    projectTemplates.value = []
  }
}

// 模板下拉选择处理
const handleTemplateSelect = (templateId) => {
  if (!templateId) {
    return
  }
  const template = projectTemplates.value.find(t => t.id === templateId)
  if (template && template.sourceCode) {
    form.customResponseSource = template.sourceCode
    validationResult.value = null
    ElMessage.success('已从模板加载代码')
  }
}

// 响应管理相关
const responseDialogVisible = ref(false)
const responseLoading = ref(false)
const responseFormDialogVisible = ref(false)
const responseSubmitLoading = ref(false)
const responseFormRef = ref()
const responseList = ref([])

// AI 生成响应相关
const aiGenerateDialogVisible = ref(false)
const aiGenLoading = ref(false)
const aiGenCount = ref(3)
const aiGenStyle = ref('')
const aiGenResults = ref([])

// AI 生成描述相关
const aiDescLoading = ref(false)

const currentApi = ref(null)
const activeResponseId = ref(null)
const responseFormTitle = ref('')

// 模板引擎相关
const showTemplateHelper = ref(false)
const templateFunctions = ref({})
const templatePreviewLoading = ref(false)
const templatePreviewResult = ref('')
const templatePreviewDialogVisible = ref(false)
const templatePreviewFormattedResult = ref('')
const templateDocDialogVisible = ref(false)
const responseBodyInputRef = ref(null)

// 模板函数列表（用于表格展示）
const templateFunctionsList = computed(() => {
  return Object.entries(templateFunctions.value).map(([func, desc]) => ({ func, desc }))
})

// 加载模板函数列表
const loadTemplateFunctions = async () => {
  try {
    const res = await getTemplateFunctions()
    if (res.code === 200) {
      templateFunctions.value = res.data || {}
    }
  } catch (e) {
    console.error('加载模板函数失败', e)
  }
}

// 插入模板占位符
const insertTemplate = (func) => {
  const textarea = responseBodyInputRef.value?.textarea || responseBodyInputRef.value?.$el?.querySelector('textarea')
  if (!textarea) {
    responseForm.responseBody = (responseForm.responseBody || '') + func
    return
  }
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const text = responseForm.responseBody || ''
  const before = text.substring(0, start)
  const after = text.substring(end)
  responseForm.responseBody = before + func + after
  nextTick(() => {
    textarea.selectionStart = textarea.selectionEnd = start + func.length
    textarea.focus()
  })
}

// 预览模板
const handleTemplatePreview = async () => {
  if (!canUseTemplateEngine.value) return
  if (!responseForm.responseBody || !responseForm.responseBody.trim()) {
    ElMessage.warning(t('api.enterResponseBodyFirst'))
    return
  }
  templatePreviewLoading.value = true
  try {
    const res = await previewTemplate(responseForm.responseBody)
    if (res.code === 200) {
      let result = res.data
      // 尝试 JSON 格式化
      try {
        const parsed = JSON.parse(result)
        result = JSON.stringify(parsed, null, 2)
      } catch (e) {
        // 不是 JSON，保持原样
      }
      templatePreviewFormattedResult.value = result
      templatePreviewDialogVisible.value = true
    }
  } catch (e) {
    ElMessage.error(t('api.previewFailed'))
  } finally {
    templatePreviewLoading.value = false
  }
}

// 表单数据
const form = reactive({
  id: null,
  projectId: null,
  name: '',
  path: '',
  method: 'GET',
  requestType: 'HTTP',
  description: '',
  enabled: true,
  responseDelay: 0,
  enableRandom: false,
  customResponseSource: ''
})

// 表单验证规则
const rules = computed(() => ({
  projectId: [
    { required: true, message: t('api.selectProject'), trigger: 'change' }
  ],
  name: [
    { required: true, message: t('api.nameRequired'), trigger: 'blur' },
    { min: 2, max: 100, message: t('api.nameLengthError'), trigger: 'blur' }
  ],
  path: [
    { required: true, message: t('api.pathRequired'), trigger: 'blur' },
    { min: 1, max: 200, message: t('api.nameLengthError'), trigger: 'blur' },
    {
      pattern: /^\/[a-zA-Z0-9_/{}?=&.\-]*$/,
      message: t('api.pathFormatError'),
      trigger: 'blur'
    }
  ],
  method: [
    { required: true, message: t('api.methodRequired'), trigger: 'change' }
  ],
  requestType: [
    { required: true, message: t('api.requestTypeRequired'), trigger: 'change' }
  ]
}))

// 响应表单数据
const responseForm = reactive({
  id: null,
  statusCode: 200,
  contentType: 'application/json',
  headers: '',
  responseBody: '',
  weight: 50,
  enabled: true,
  active: false,
  isDefault: false,
  responseDelay: 0
})

// 响应表单验证规则
const responseRules = computed(() => ({
  statusCode: [
    { required: true, message: t('api.statusCode'), trigger: 'blur' }
  ],
  contentType: [
    { required: true, message: t('api.contentType'), trigger: 'change' }
  ],
  responseBody: [
    { required: true, message: t('api.responseBody'), trigger: 'blur' }
  ]
}))

// 请求参数管理相关
const requestParamDialogVisible = ref(false)
const requestParamLoading = ref(false)
const requestParamFormDialogVisible = ref(false)
const requestParamSubmitLoading = ref(false)
const requestParamFormRef = ref()
const requestParamList = ref([])
const currentResponse = ref(null)
const requestParamFormTitle = ref('')

// 请求参数表单数据
const requestParamForm = reactive({
  id: null,
  paramName: '',
  paramType: 'REQUEST_BODY',
  paramValue: '',
  required: true,
  isPathParam: false,
  isQueryParam: false
})

// 请求参数表单验证规则
const requestParamRules = computed(() => ({
  paramName: [
    { required: true, message: t('api.paramName'), trigger: 'blur' }
  ],
  paramType: [
    { required: true, message: t('api.paramTypeLabel'), trigger: 'change' }
  ],
  paramValue: [
    { required: true, message: t('api.paramValueLabel'), trigger: 'blur' }
  ]
}))

// 提取接口路径中的RESTful参数名称
const extractPathParamNames = (path) => {
  const paramNames = []
  const parts = path.split('/')
  for (const part of parts) {
    if (part.startsWith('{') && part.endsWith('}')) {
      paramNames.push(part.substring(1, part.length - 1))
    }
  }
  return paramNames
}

// 提取查询字符串中的占位符参数名称
const extractQueryParamNames = (path) => {
  const paramNames = []
  // 查找查询参数部分的占位符，格式如 ?name={name}&age={age}
  const queryMatch = path.match(/\?.+/)
  if (queryMatch) {
    const queryString = queryMatch[0]
    // 匹配 {paramName} 格式的占位符
    const placeholderRegex = /\{([^}]+)\}/g
    let match
    while ((match = placeholderRegex.exec(queryString)) !== null) {
      paramNames.push(match[1])
    }
  }
  return paramNames
}

// 获取参数类型标签
const getParamTypeLabel = (type) => {
  const labels = {
    'PATH': 'PATH',
    'QUERY': 'QUERY',
    'REQUEST_BODY': 'BODY',
    'HEADER': 'HEADER',
    'FILE': 'FILE'
  }
  return labels[type] || type
}

// 获取参数类型标签类型
const getParamTypeTagType = (type) => {
  const types = {
    'PATH': 'success',
    'QUERY': 'primary',
    'REQUEST_BODY': 'warning',
    'HEADER': 'info',
    'FILE': 'danger'
  }
  return types[type] || 'info'
}

// 获取请求方法标签类型
const getMethodTagType = (method) => {
  const types = {
    'GET': 'success',
    'POST': 'primary',
    'PUT': 'warning',
    'DELETE': 'danger',
    'PATCH': 'info'
  }
  return types[method] || 'info'
}

// 判断当前编辑的接口是否有多个激活响应
const hasMultipleActiveResponses = ref(false)

// 检查接口是否有多个激活响应
const checkMultipleActiveResponses = async (apiId) => {
  if (!apiId) {
    hasMultipleActiveResponses.value = false
    return
  }
  try {
    const response = await request({
      url: `/mock-apis/${apiId}/responses`,
      method: 'get'
    })
    if (response.code === 200) {
      const activeCount = response.data.filter(r => r.active === true).length
      hasMultipleActiveResponses.value = activeCount > 1
      // 当存在多个激活响应时，自动启用随机返回并禁用开关
      if (hasMultipleActiveResponses.value && !form.enableRandom) {
        form.enableRandom = true
      }
    }
  } catch (error) {
    console.error('检查激活响应失败:', error)
    hasMultipleActiveResponses.value = false
  }
}

// 获取项目列表
const fetchProjects = async () => {
  try {
    const response = await getAccessibleProjects()
    if (response.code === 200) {
      projectList.value = response.data?.content || response.data || []
    }
  } catch (error) {
    console.error('获取项目列表失败:', error)
  }
}

const fetchAccessibleProjects = async () => {
  try {
    const response = await getAllAccessibleProjects()
    if (response.code === 200) {
      accessibleProjects.value = response.data || []
    }
  } catch (error) {
    console.error('获取可访问项目失败:', error)
  }
}

// 获取接口列表
const fetchApis = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.current - 1,
      size: pagination.pageSize
    }
    if (searchForm.name) params.name = searchForm.name
    if (searchForm.path) params.path = searchForm.path
    if (searchForm.method) params.method = searchForm.method
    if (searchForm.enabled !== null && searchForm.enabled !== '') params.enabled = searchForm.enabled

    // 使用搜索表单中的projectId，如果没有则使用路由参数
    let url = '/mock-apis'
    const projectId = searchForm.projectId || route.query.projectId
    if (projectId) {
      url = `/mock-apis/project/${projectId}`
      params.projectId = projectId
    }

    const response = await request({
      url,
      method: 'get',
      params
    })

    if (response.code === 200) {
      apiList.value = response.data.content || []
      pagination.total = response.data.totalElements || 0
    } else {
      ElMessage.error(t('api.fetchFailed'))
    }
  } catch (error) {
    console.error('获取接口列表失败:', error)
    ElMessage.error(t('api.fetchFailed'))
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  pagination.current = 1
  fetchApis()
}

// 重置
const handleReset = () => {
  searchForm.name = ''
  searchForm.path = ''
  searchForm.projectId = null
  searchForm.method = ''
  searchForm.enabled = null
  handleSearch()
}

// 分页大小变化
const handleSizeChange = (size) => {
  pagination.pageSize = size
  fetchApis()
}

// 当前页变化
const handleCurrentChange = (page) => {
  pagination.current = page
  fetchApis()
}

// 创建接口
const handleCreate = () => {
  dialogTitle.value = t('api.createApi')
  isEdit.value = false
  validationResult.value = null
  form.id = null
  form.projectId = accessibleProjects.value.length > 0 ? accessibleProjects.value[0].id : null
  form.name = ''
  form.path = ''
  form.method = 'GET'
  form.requestType = 'HTTP'
  form.description = ''
  form.enabled = true
  form.responseDelay = 0
  form.enableRandom = false
  form.customResponseSource = ''
  selectedTemplateId.value = null
  projectTemplates.value = []
  // 加载默认项目的模板列表
  if (form.projectId) {
    fetchProjectTemplates(form.projectId)
  }
  dialogVisible.value = true
}

// 导出 Markdown 相关
const selectedApis = ref([])
const exportDialogVisible = ref(false)
const exportAiEnhance = ref(false)
const exportLoading = ref(false)

// 表格多选变化
const handleSelectionChange = (rows) => {
  selectedApis.value = rows
}

// 打开导出 Markdown 对话框
const handleExportMarkdown = () => {
  if (selectedApis.value.length === 0) {
    ElMessage.warning(t('api.selectApisFirst'))
    return
  }
  exportAiEnhance.value = false
  exportDialogVisible.value = true
}

// 关闭导出对话框
const handleExportDialogClose = () => {
  exportLoading.value = false
}

// 确认导出 Markdown（支持手动控制是否进行 AI 增强）
const handleExportConfirm = async () => {
  if (selectedApis.value.length === 0) {
    ElMessage.warning(t('api.selectApisFirst'))
    return
  }
  const apiIds = selectedApis.value.map(row => row.id)
  exportLoading.value = true
  try {
    const warnings = await exportMockApisMarkdown(apiIds, exportAiEnhance.value)
    ElMessage.success(t('api.exportSuccess'))
    exportDialogVisible.value = false
    // AI 增强存在异常（如 AI 服务不可用、输出不合规）时友好提示，不影响已生成的文档
    if (warnings && warnings.length > 0) {
      ElMessageBox.alert(
        warnings.map(w => `• ${w}`).join('<br/>'),
        t('api.exportWarningsTitle'),
        {
          dangerouslyUseHTMLString: true,
          type: 'warning',
          confirmButtonText: t('common.confirm')
        }
      )
    }
  } catch (error) {
    console.error('导出 Markdown 失败:', error)
    ElMessage.error(t('api.exportFailed'))
  } finally {
    exportLoading.value = false
  }
}

// 下拉菜单操作分发
const handleApiAction = (command, row) => {
  switch (command) {
    case 'edit':
      handleEdit(row)
      break
    case 'responses':
      handleResponses(row)
      break
    case 'delete':
      handleDelete(row)
      break
  }
}

const handleEdit = async (row) => {
  dialogTitle.value = t('api.editApi')
  isEdit.value = true
  validationResult.value = null
  form.id = row.id
  form.name = row.name
  form.path = row.path
  form.method = row.method
  form.requestType = row.requestType
  form.description = row.description || ''
  form.enabled = row.enabled
  form.responseDelay = row.responseDelay || 0
  form.enableRandom = row.enableRandom || false
  form.customResponseSource = row.customResponseSource || ''
  // 编辑时projectId从row中获取
  form.projectId = row.project?.id || null

  // 检查是否有多个激活响应
  await checkMultipleActiveResponses(row.id)

  // 加载当前项目的模板列表
  selectedTemplateId.value = null
  await fetchProjectTemplates(row.project?.id)

  dialogVisible.value = true
}

// 管理响应
const handleResponses = async (row) => {
  currentApi.value = row
  responseDialogVisible.value = true
  await fetchResponses()
}

// 获取响应列表
const fetchResponses = async () => {
  if (!currentApi.value) return
  responseLoading.value = true
  try {
    const response = await request({
      url: `/mock-apis/${currentApi.value.id}/responses`,
      method: 'get'
    })
    if (response.code === 200) {
      responseList.value = response.data
      // 找到当前激活的响应
      const active = responseList.value.find(r => r.active === true)
      activeResponseId.value = active ? active.id : null
    } else {
      ElMessage.error(t('api.fetchFailed'))
    }
  } catch (error) {
    console.error('获取响应列表失败:', error)
    ElMessage.error(t('api.fetchFailed'))
  } finally {
    responseLoading.value = false
  }
}

// 根据状态码获取行样式类名
const getResponseRowClass = ({ row, rowIndex }) => {
  const statusCodes = responseList.value.map(r => r.statusCode)
  const sameStatusCount = statusCodes.filter(code => code === row.statusCode).length
  if (sameStatusCount > 1) {
    // 为相同状态码的行返回不同的背景色
    const colors = ['status-code-color-1', 'status-code-color-2', 'status-code-color-3', 'status-code-color-4', 'status-code-color-5']
    const colorIndex = statusCodes.indexOf(row.statusCode) % colors.length
    return colors[colorIndex]
  }
  return ''
}

// 添加响应
const handleAddResponse = () => {
  responseFormTitle.value = t('api.addResponse')
  responseForm.id = null
  responseForm.statusCode = 200
  responseForm.contentType = 'application/json'
  responseForm.headers = ''
  responseForm.responseBody = ''
  responseForm.weight = 50
  responseForm.enabled = true
  responseFormDialogVisible.value = true
}

// AI 生成接口描述（流式）
const handleAiGenerateDescription = async () => {
  if (!form.name || !form.path) {
    ElMessage.warning(t('ai.fillNamePathFirst'))
    return
  }

  aiDescLoading.value = true
  try {
    const fullText = await generateApiDescriptionStream({
      apiMethod: form.method,
      apiPath: form.path,
      apiName: form.name
    })

    if (fullText && fullText.trim()) {
      form.description = fullText.trim()
      ElMessage.success(t('ai.descriptionGenerated'))
    } else {
      ElMessage.error(t('ai.generateDescriptionFailed'))
    }
  } catch (error) {
    console.error('AI 生成描述失败:', error)
    const msg = error?.message || t('ai.generateDescriptionFailed')
    ElMessage.error(msg)
  } finally {
    aiDescLoading.value = false
  }
}

// 打开 AI 生成响应对话框
const handleAiGenerateDialog = () => {
  aiGenCount.value = 3
  aiGenStyle.value = ''
  aiGenResults.value = []
  aiGenerateDialogVisible.value = true
}

// 关闭 AI 生成对话框
const handleAiGenerateDialogClose = () => {
  aiGenResults.value = []
}

// AI 生成响应
const handleAiGenerate = async () => {
  if (!currentApi.value) {
    ElMessage.warning(t('ai.noAiConfig'))
    return
  }

  aiGenLoading.value = true
  aiGenResults.value = []

  try {
    const response = await generateMockResponse({
      apiMethod: currentApi.value.method,
      apiPath: currentApi.value.path,
      apiName: currentApi.value.name,
      description: currentApi.value.description + (aiGenStyle.value ? '。响应样式要求：' + aiGenStyle.value : ''),
      count: aiGenCount.value
    })

    if (response.code === 200 && response.data && response.data.length > 0) {
      aiGenResults.value = response.data.map(item => ({
        statusCode: item.statusCode || 200,
        contentType: item.contentType || 'application/json',
        responseBody: item.responseBody || '',
        responseDelay: 0,
        weight: 50,
        _applying: false
      }))
      ElMessage.success(t('ai.generateSuccess'))
    } else {
      ElMessage.error(response.message || t('ai.generateFailed'))
    }
  } catch (error) {
    console.error('AI 生成失败:', error)
    const msg = error?.response?.data?.message || error?.message || t('ai.generateFailed')
    ElMessage.error(msg)
  } finally {
    aiGenLoading.value = false
  }
}

// 应用 AI 生成的响应到接口
const handleApplyAiResponse = async (row, index) => {
  if (!currentApi.value) return

  row._applying = true
  try {
    const res = await request({
      url: `/mock-apis/${currentApi.value.id}/responses`,
      method: 'post',
      data: {
        statusCode: row.statusCode,
        contentType: row.contentType,
        headers: '',
        responseBody: row.responseBody,
        weight: row.weight,
        responseDelay: row.responseDelay,
        enabled: true,
        isDefault: false,
        active: false
      }
    })

    if (res.code === 200) {
      ElMessage.success(t('ai.applySuccess', { index: index + 1 }))
      // 从列表中移除已应用的项
      aiGenResults.value.splice(index, 1)
      // 刷新响应列表
      fetchResponses()
    } else {
      ElMessage.error(res.message || t('ai.applyFailed'))
    }
  } catch (error) {
    console.error('应用失败:', error)
    ElMessage.error(t('ai.applyFailed'))
  } finally {
    row._applying = false
  }
}

// 编辑响应
const handleEditResponse = (row) => {
  responseFormTitle.value = t('api.edit')
  responseForm.id = row.id
  responseForm.statusCode = row.statusCode
  responseForm.contentType = row.contentType
  responseForm.headers = row.headers || ''
  responseForm.responseBody = row.responseBody
  responseForm.weight = row.weight || 50
  responseForm.enabled = row.enabled
  responseForm.active = row.active || false
  responseForm.isDefault = row.isDefault || false
  responseForm.responseDelay = row.responseDelay || 0
  responseFormDialogVisible.value = true
}

// 删除响应
const handleDeleteResponse = async (row) => {
  try {
    await ElMessageBox.confirm(t('api.confirmDeleteResponse'), t('common.warning'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'error',
      confirmButtonClass: 'el-button--danger'
    })

    const response = await request({
      url: `/mock-apis/responses/${row.id}`,
      method: 'delete'
    })
    if (response.code === 200) {
      ElMessage.success(t('api.deleteSuccess'))
      fetchResponses()
    } else {
      ElMessage.error(t('api.deleteFailed'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error(t('api.deleteFailed'))
    }
  }
}

// 更新响应（内联编辑）
const handleUpdateResponse = async (row) => {
  try {
    const response = await request({
      url: '/mock-apis/responses',
      method: 'put',
      data: {
        id: row.id,
        statusCode: row.statusCode,
        contentType: row.contentType,
        headers: row.headers,
        responseBody: row.responseBody,
        weight: row.weight,
        enabled: row.enabled,
        active: row.active,
        isDefault: row.isDefault,
        mockApi: { id: currentApi.value.id }
      }
    })
    if (response.code === 200) {
      ElMessage.success(t('api.updateSuccess'))
    } else {
      ElMessage.error(t('api.updateFailed'))
      fetchResponses()
    }
  } catch (error) {
    console.error('更新失败:', error)
    ElMessage.error(t('api.updateFailed'))
    fetchResponses()
  }
}

// 设置激活响应
const handleSetActiveResponse = async (responseId) => {
  try {
    const response = await request({
      url: `/mock-apis/${currentApi.value.id}/responses/${responseId}/active`,
      method: 'post'
    })
    if (response.code === 200) {
      ElMessage.success(t('api.setActiveSuccess'))
      fetchResponses()
    } else {
      ElMessage.error(t('api.setActiveFailed'))
      fetchResponses()
    }
  } catch (error) {
    console.error('设置失败:', error)
    ElMessage.error(t('api.setActiveFailed'))
    fetchResponses()
  }
}

// 切换响应激活状态
const handleToggleActive = async (row) => {
  // 判断接口是否启用了随机返回（确保转换为布尔值）
  const isRandomEnabled = currentApi.value?.enableRandom === true

  // 计算当前已激活的响应数量（不包括当前正在切换的响应）
  const currentActiveCount = responseList.value.filter(r => r.active === true && r.id !== row.id).length

  console.log('切换激活状态:', {
    isRandomEnabled,
    rowActive: row.active,
    currentActiveCount,
    isActivating: !row.active
  })

  if (!isRandomEnabled) {
    // 未启用随机返回时，不允许激活多个响应
    if (!row.active && currentActiveCount >= 1) {
      ElMessage({
        type: 'warning',
        message: t('api.randomNotEnabled'),
        duration: 5000,
        showClose: true
      })
      // 取消本次切换，恢复原状态
      await fetchResponses()
      return
    }
  }

  // 设置该响应的激活状态
  await handleSetActiveResponse(row.id)
}

// 提交响应表单
const handleResponseSubmit = async () => {
  try {
    await responseFormRef.value.validate()
    responseSubmitLoading.value = true

    const submitData = {
      ...responseForm,
      mockApi: { id: currentApi.value.id }
    }

    const response = responseForm.id
      ? await request({
          url: '/mock-apis/responses',
          method: 'put',
          data: submitData
        })
      : await request({
          url: `/mock-apis/${currentApi.value.id}/responses`,
          method: 'post',
          data: submitData
        })

    if (response.code === 200) {
      ElMessage.success(responseForm.id ? t('api.editResponseSuccess') : t('api.addResponseSuccess'))
      responseFormDialogVisible.value = false
      fetchResponses()
    } else {
      ElMessage.error(response.message || (responseForm.id ? t('api.editResponseFailed') : t('api.addResponseFailed')))
    }
  } catch (error) {
    console.error('提交失败:', error)
    ElMessage.error(responseForm.id ? t('api.editResponseFailed') : t('api.addResponseFailed'))
  } finally {
    responseSubmitLoading.value = false
  }
}

// 关闭响应对话框
const handleResponseDialogClose = () => {
  // 如果编辑对话框仍打开，重新检查激活响应数量（用户可能在响应管理中修改了激活状态）
  if (dialogVisible.value && isEdit.value && form.id) {
    checkMultipleActiveResponses(form.id)
  }
  currentApi.value = null
  responseList.value = []
  activeResponseId.value = null
}

// 关闭响应表单对话框
const handleResponseFormDialogClose = () => {
  responseFormRef.value?.resetFields()
  showTemplateHelper.value = false
  templatePreviewResult.value = ''
}

// 打开请求参数管理
const handleRequestParams = async (row) => {
  currentResponse.value = row
  requestParamDialogVisible.value = true
  await fetchRequestParams(row.id)
}

// 获取请求参数列表
const fetchRequestParams = async (responseId) => {
  requestParamLoading.value = true
  try {
    const response = await request({
      url: `/responses/${responseId}/params`,
      method: 'get'
    })
    if (response.code === 200) {
      requestParamList.value = response.data || []
    }
  } catch (error) {
    console.error('获取请求参数失败:', error)
    ElMessage.error(t('api.fetchFailed'))
  } finally {
    requestParamLoading.value = false
  }
}

// 添加请求参数
const handleAddRequestParam = () => {
  requestParamFormTitle.value = t('api.addParam')

  const apiPath = form.path || currentApi.value?.path || ''
  const apiMethod = form.method || currentApi.value?.method || 'GET'

  // 提取路径中的RESTful参数名称
  const pathParamNames = extractPathParamNames(apiPath)

  // 提取查询字符串中的占位符参数名称（仅针对 GET 请求）
  const queryParamNames = apiMethod === 'GET' ? extractQueryParamNames(apiPath) : []

  // 合并所有占位符参数名称（优先使用查询参数）
  const allParamNames = [...queryParamNames, ...pathParamNames]

  // 如果有参数，设置第一个作为参数名称
  const defaultParamName = allParamNames.length > 0 ? allParamNames[0] : ''
  const isPathParam = pathParamNames.includes(defaultParamName)
  const isQueryParam = queryParamNames.includes(defaultParamName)

  Object.assign(requestParamForm, {
    id: null,
    paramName: defaultParamName,
    paramType: isPathParam ? 'PATH' : (isQueryParam ? 'QUERY' : 'REQUEST_BODY'),
    paramValue: '',
    required: true,
    isPathParam: isPathParam,
    isQueryParam: isQueryParam
  })
  requestParamFormDialogVisible.value = true
}

// 参数名称变化时判断是否为路径参数或查询参数
const handleParamNameBlur = () => {
  const apiPath = form.path || currentApi.value?.path || ''
  const apiMethod = form.method || currentApi.value?.method || 'GET'

  const pathParamNames = extractPathParamNames(apiPath)
  const queryParamNames = apiMethod === 'GET' ? extractQueryParamNames(apiPath) : []

  const isPathParam = pathParamNames.includes(requestParamForm.paramName)
  const isQueryParam = queryParamNames.includes(requestParamForm.paramName)

  if (isPathParam) {
    requestParamForm.paramType = 'PATH'
    requestParamForm.isPathParam = true
    requestParamForm.isQueryParam = false
  } else if (isQueryParam) {
    requestParamForm.paramType = 'QUERY'
    requestParamForm.isPathParam = false
    requestParamForm.isQueryParam = true
  } else if (requestParamForm.paramType === 'PATH' || requestParamForm.paramType === 'QUERY') {
    requestParamForm.paramType = 'REQUEST_BODY'
    requestParamForm.isPathParam = false
    requestParamForm.isQueryParam = false
  } else {
    requestParamForm.isPathParam = false
    requestParamForm.isQueryParam = false
  }
}

// 删除请求参数
const handleDeleteRequestParam = async (row) => {
  try {
    await ElMessageBox.confirm(t('api.confirmDeleteParam'), t('common.warning'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'error',
      confirmButtonClass: 'el-button--danger'
    })

    const response = await request({
      url: `/responses/${currentResponse.value.id}/params/${row.id}`,
      method: 'delete'
    })
    if (response.code === 200) {
      ElMessage.success(t('api.paramDeleted'))
      fetchRequestParams(currentResponse.value.id)
    } else {
      ElMessage.error(t('api.deleteFailed'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error(t('api.deleteFailed'))
    }
  }
}

// 提交请求参数表单
const handleRequestParamSubmit = async () => {
  if (!requestParamFormRef.value) return

  try {
    await requestParamFormRef.value.validate()

    requestParamSubmitLoading.value = true
    const response = await request({
      url: `/responses/${currentResponse.value.id}/params`,
      method: 'post',
      data: requestParamForm
    })

    if (response.code === 200) {
      ElMessage.success(t('api.paramAdded'))
      requestParamFormDialogVisible.value = false
      fetchRequestParams(currentResponse.value.id)
    } else {
      ElMessage.error(response.message || t('api.addResponseFailed'))
    }
  } catch (error) {
    console.error('添加失败:', error)
    ElMessage.error(t('api.addResponseFailed'))
  } finally {
    requestParamSubmitLoading.value = false
  }
}

// 关闭请求参数表单对话框
const handleRequestParamFormDialogClose = () => {
  requestParamFormRef.value?.resetFields()
}

// 关闭请求参数管理对话框
const handleRequestParamDialogClose = () => {
  requestParamList.value = []
  currentResponse.value = null
}

// 删除接口
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(t('api.confirmDeleteApi', { name: row.name }), t('common.warning'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'error',
      confirmButtonClass: 'el-button--danger'
    })

    const response = await request({
      url: `/mock-apis/${row.id}`,
      method: 'delete'
    })
    if (response.code === 200) {
      ElMessage.success(t('api.deleteSuccess'))
      fetchApis()
    } else {
      ElMessage.error(t('api.deleteFailed'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error(t('api.deleteFailed'))
    }
  }
}

// 复制接口路径（兼容 Chrome 140+ 及非 HTTPS 环境）
const handleCopyPath = async (row) => {
  const path = `${baseUrl}/api/api-server/${row.project?.code}${row.path}`
  try {
    // 优先使用现代 Clipboard API（HTTPS 环境）
    await navigator.clipboard.writeText(path)
    ElMessage.success(t('api.copySuccess'))
  } catch (err) {
    // 降级到传统 document.execCommand 方式（兼容 Chrome 140+ 限流/非HTTPS）
    console.warn('Clipboard API 不可用，使用降级方案:', err.message || err)
    try {
      const textarea = document.createElement('textarea')
      textarea.value = path
      textarea.setAttribute('readonly', '')
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      textarea.style.left = '-9999px'
      textarea.style.top = (window.scrollY + window.innerHeight) + 'px'
      document.body.appendChild(textarea)
      textarea.focus()
      textarea.select()
      textarea.setSelectionRange(0, 99999)
      const successful = document.execCommand('copy')
      document.body.removeChild(textarea)
      if (successful) {
        ElMessage.success(t('api.copySuccess'))
      } else {
        ElMessage.error(t('api.copyFailed'))
      }
    } catch (fallbackErr) {
      console.error('降级复制也失败:', fallbackErr)
      ElMessage.error(t('api.copyFailed'))
    }
  }
}

// 提交表单
const handleSubmit = async () => {
  try {
    await formRef.value.validate()

    // 如果有自定义代码，必须先编译验证通过
    if (form.customResponseSource && form.customResponseSource.trim()) {
      const tempId = form.id || Date.now()
      const validateResp = await request({
        url: `/mock-apis/${tempId}/custom-response-source/validate`,
        method: 'post',
        data: { sourceCode: form.customResponseSource }
      })
      if (validateResp.code !== 200) {
        ElMessage.error('代码编译未通过，请修正后重试: ' + (validateResp.message || ''))
        return
      }
    }

    submitLoading.value = true

    // 创建时需要包含project对象
    const submitData = isEdit.value ? { ...form } : {
      ...form,
      project: { id: form.projectId }
    }

    const response = isEdit.value
      ? await request({
          url: '/mock-apis',
          method: 'put',
          data: submitData
        })
      : await request({
          url: '/mock-apis',
          method: 'post',
          data: submitData
        })

    if (response.code === 200) {
      ElMessage.success(isEdit.value ? t('api.editSuccess') : t('api.createSuccess'))
      dialogVisible.value = false
      fetchApis()
    } else {
      ElMessage.error(response.message || (isEdit.value ? t('api.editFailed') : t('api.createFailed')))
    }
  } catch (error) {
    console.error('提交失败:', error)
    ElMessage.error(isEdit.value ? t('api.editFailed') : t('api.createFailed'))
  } finally {
    submitLoading.value = false
  }
}

// 关闭对话框
const handleDialogClose = () => {
  formRef.value?.resetFields()
  validationResult.value = null
}

// 编译验证自定义代码
const validateCustomCode = async () => {
  if (!form.customResponseSource || !form.customResponseSource.trim()) {
    validationResult.value = { success: false, message: t('api.enterCodeFirst') }
    return
  }
  validatingCode.value = true
  validationResult.value = null
  try {
    // 使用一个临时ID进行编译验证
    const tempId = form.id || Date.now()
    const response = await request({
      url: `/mock-apis/${tempId}/custom-response-source/validate`,
      method: 'post',
      data: { sourceCode: form.customResponseSource }
    })
    if (response.code === 200) {
      validationResult.value = { success: true, message: response.data || t('api.validatePassed') }
      ElMessage.success({ message: t('api.validateSuccess'), duration: 5000 })
    } else {
      validationResult.value = { success: false, message: response.message || t('api.validateFailed') }
      ElMessage.error({ message: response.message || t('api.validateFailed'), duration: 5000 })
    }
  } catch (error) {
    validationResult.value = { success: false, message: error.message || t('api.validateFailed') }
    ElMessage.error({ message: t('api.validateFailed') + (error.message || ''), duration: 5000 })
  } finally {
    validatingCode.value = false
  }
}

// 清空自定义代码
const clearCustomCode = () => {
  selectedTemplateId.value = null
  form.customResponseSource = ''
  validationResult.value = null
  ElMessage.success(t('api.codeCleared'))
}

// 监听项目ID变化（创建模式），重新加载模板列表
watch(() => form.projectId, (newProjectId) => {
  if (!isEdit.value && newProjectId) {
    selectedTemplateId.value = null
    fetchProjectTemplates(newProjectId)
  }
})

// 页面加载时获取数据
onMounted(() => {
  fetchProjects()
  fetchAccessibleProjects()
  fetchApis()
  loadTemplateFunctions()
})
</script>

<style scoped>
.apis {
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

/* 导出 Markdown 弹窗样式 */
.export-dialog-body {
  padding: 4px 0;
}

.export-dialog-body .el-form-item {
  margin-bottom: 8px;
}

.ai-enhance-tip {
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
  background: #f5f7fa;
  padding: 10px 12px;
  border-radius: 4px;
  border: 1px solid #ebeef5;
  width: 100%;
}

/* 状态码行背景色 */
:deep(.status-code-color-1) {
  background-color: #e8f5e9 !important;
}

:deep(.status-code-color-2) {
  background-color: #e3f2fd !important;
}

:deep(.status-code-color-3) {
  background-color: #fff3e0 !important;
}

:deep(.status-code-color-4) {
  background-color: #f3e5f5 !important;
}

:deep(.status-code-color-5) {
  background-color: #fce4ec !important;
}

/* 复制路径按钮布局 */
.path-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.copy-btn {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 1px solid #dcdfe6;
  background: #fff;
  opacity: 0;
  transition: opacity 0.2s;
}

::deep(.path-cell:hover .copy-btn),
::deep(.copy-btn:focus-visible) {
  opacity: 1;
}

::deep(.copy-btn:hover) {
  border-color: #409eff;
  color: #409eff;
}

/* 自定义代码编辑器全屏 */
.api-code-fullscreen {
  position: fixed !important;
  inset: 0 !important;
  z-index: 9999 !important;
  background: #f0f2f5 !important;
  margin: 0 !important;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
}

.api-code-fullscreen :deep(.el-form-item__content) {
  margin-left: 0 !important;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.api-fullscreen-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  flex-shrink: 0;
}

.api-fullscreen-toolbar + .monaco-editor-wrapper {
  flex: 1;
}

/* AI 生成响应弹窗样式 */
.ai-generate-form {
  background: #fafafa;
  padding: 16px 20px 4px;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  margin-bottom: 4px;
}

.ai-generate-form .el-form-item {
  margin-bottom: 12px;
}

.ai-result-table {
  margin-top: 16px;
}

.ai-result-table .el-table {
  border-radius: 6px;
  overflow: hidden;
}

.ai-result-table .el-table th {
  font-size: 13px;
}

.response-body-cell {
  font-family: 'SF Mono', 'Monaco', 'Menlo', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.5;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
  padding: 2px 0;
}

/* AI 弹窗调整宽度 */
.ai-result-table .el-table__body-wrapper {
  max-height: 55vh;
  overflow-y: auto;
}
</style>

