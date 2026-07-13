<!--
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
-->

<template>
  <div class="permissions">
    <div class="page-header">
      <h1>{{ $t('permission.title') }}</h1>
    </div>

    <el-tabs v-model="activeTab" type="border-card">
      <!-- 标签页1: 角色权限分配 -->
      <el-tab-pane :label="$t('permission.permission.rolePermissionAssign')" name="roleAssign">
        <!-- 角色选择 -->
        <el-card class="select-card" shadow="never">
          <div class="select-row">
            <span class="select-label">{{ $t('permission.permission.selectRole') }}：</span>
            <el-select
              v-model="selectedRoleId"
              :placeholder="$t('permission.permission.selectRolePlaceholder')"
              style="width: 300px"
              @change="handleRoleChange"
            >
              <el-option
                v-for="role in roleList"
                :key="role.id"
                :label="role.name + ' (' + role.code + ')'"
                :value="role.id"
              />
            </el-select>
            <span v-if="!selectedRoleId" class="select-hint">{{ $t('permission.permission.selectRoleHint') }}</span>
            <span v-if="selectedRoleId" class="perms-count">{{ $t('permission.permission.permissionsCount', { count: checkedPermissionIds.length }) }}</span>
          </div>
        </el-card>

        <!-- 权限分配 -->
        <el-card v-if="selectedRoleId" class="perms-card" shadow="never">
          <template v-if="loading">
            <el-skeleton :rows="5" animated />
          </template>
          <template v-else>
            <div v-for="group in permissionGroups" :key="group.groupName" class="perm-group">
              <div class="perm-group-header">
                <el-checkbox
                  v-model="group.checkedAll"
                  :indeterminate="group.indeterminate"
                  :disabled="!canAssignPermission"
                  @change="(val) => handleGroupCheckAll(group, val)"
                >
                  <strong>{{ group.groupName }}</strong>
                </el-checkbox>
              </div>
              <div class="perm-items">
                <div v-for="perm in group.permissions" :key="perm.id" class="perm-item">
                  <el-checkbox
                    :model-value="checkedPermissionIds.includes(perm.id)"
                    :disabled="!canAssignPermission"
                    @change="(val) => handlePermCheck(perm.id, val)"
                  >
                    <span class="perm-name">{{ perm.name }}</span>
                    <el-tag :type="perm.type === 'PAGE' ? 'primary' : 'success'" size="small" class="perm-type-tag">
                      {{ perm.type === 'PAGE' ? $t('permission.permission.pageAccess') : $t('permission.permission.buttonOperation') }}
                    </el-tag>
                  </el-checkbox>
                </div>
              </div>
            </div>

            <div class="perms-footer" v-if="canAssignPermission">
              <el-button type="primary" :loading="saving" @click="handleSave">
                {{ $t('permission.permission.savePermissions') }}
              </el-button>
            </div>
          </template>
        </el-card>
      </el-tab-pane>

      <!-- 标签页2: 权限定义管理 -->
      <el-tab-pane :label="$t('permission.permission.permissionDefManage')" name="permManage">
        <div class="perm-manage-header">
          <el-button type="primary" @click="handleCreatePerm" :disabled="!canCreatePermission">
            <el-icon><Plus /></el-icon>
            {{ $t('permission.permission.createPermission') }}
          </el-button>
          <span class="perm-total-hint">{{ $t('permission.permission.totalPermissions', { count: allPermissions.length }) }}</span>
        </div>

        <el-card shadow="never">
          <el-table
            v-loading="permTableLoading"
            :data="allPermissions"
            border
            stripe
            style="width: 100%"
            :header-cell-style="{ background: '#f5f7fa' }"
            max-height="520"
          >
            <el-table-column prop="id" label="ID" width="70" align="center" />
            <el-table-column prop="name" :label="$t('permission.permission.permName')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="code" :label="$t('permission.permission.permCode')" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">
                <el-tag type="info" size="small" class="code-tag">{{ row.code }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="groupName" :label="$t('permission.permission.permGroup')" width="140" show-overflow-tooltip />
            <el-table-column prop="type" :label="$t('permission.permission.permType')" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="row.type === 'PAGE' ? 'primary' : 'success'" size="small">
                  {{ row.type === 'PAGE' ? $t('permission.permission.pageAccess') : $t('permission.permission.buttonOperation') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$t('permission.role.actions')" width="160" fixed="right" align="center">
              <template #default="{ row }">
                <el-button type="primary" link @click="handleEditPerm(row)" :disabled="!canEditPermission">
                  {{ $t('permission.role.edit') }}
                </el-button>
                <el-button type="danger" link @click="handleDeletePerm(row)" :disabled="!canDeletePermission">
                  {{ $t('permission.role.delete') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 权限创建/编辑对话框 -->
    <el-dialog
      v-model="permDialogVisible"
      :title="permDialogTitle"
      width="540px"
      @close="handlePermDialogClose"
    >
      <el-form ref="permFormRef" :model="permForm" :rules="permRules" label-width="110px">
        <!-- 1. 先选分组，分组决定了名称和编码的可选项 -->
        <el-form-item :label="$t('permission.permission.permGroup')" prop="groupName">
          <el-select
            v-model="permForm.groupName"
            filterable
            allow-create
            :placeholder="$t('permission.permission.permGroupPlaceholder')"
            style="width: 100%"
            :disabled="isPermEdit"
            @change="handleGroupChange"
          >
            <el-option
              v-for="item in groupSuggestions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
              :disabled="isGroupFullyUsed(item)"
            />
          </el-select>
        </el-form-item>
        <!-- 2. 权限编码（选择后自动填充名称），已使用的禁用 -->
        <el-form-item :label="$t('permission.permission.permCode')" prop="code">
          <el-select
            v-model="permForm.code"
            filterable
            allow-create
            :placeholder="$t('permission.permission.permCodePlaceholder')"
            style="width: 100%"
            :disabled="isPermEdit"
            @change="handleCodeChange"
          >
            <el-option
              v-for="item in filteredCodeSuggestions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
              :disabled="item.disabled"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('permission.permission.permType')">
          <el-tag :type="permForm.type === 'PAGE' ? 'primary' : 'success'" size="default">
            {{ permForm.type === 'PAGE' ? $t('permission.permission.pageAccess') : $t('permission.permission.buttonOperation') }}
          </el-tag>
          <span class="form-hint" style="margin-left: 8px">{{ $t('permission.permission.autoDetectHint') }}</span>
        </el-form-item>

      </el-form>
      <template #footer>
        <el-button @click="permDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="permSubmitLoading" @click="handlePermSubmit">
          {{ $t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const userStore = useUserStore()

const activeTab = ref('roleAssign')

// ==================== 角色权限分配 ====================
const roleList = ref([])
const selectedRoleId = ref(null)
const permissionGroups = ref([])
const checkedPermissionIds = ref([])
const loading = ref(false)
const saving = ref(false)

const canAssignPermission = computed(() => userStore.hasPermission('permission:assign'))

const fetchRoles = async () => {
  try {
    const response = await request.get('/roles')
    if (response.code === 200) {
      roleList.value = response.data || []
    }
  } catch (error) {
    console.error('获取角色列表失败:', error)
  }
}

const fetchPermissions = async () => {
  loading.value = true
  try {
    const response = await request.get('/permissions')
    if (response.code === 200) {
      permissionGroups.value = (response.data || []).map(group => ({
        ...group,
        checkedAll: false,
        indeterminate: false
      }))
      // 同时更新权限定义管理的数据
      allPermissions.value = []
      ;(response.data || []).forEach(g => {
        if (g.permissions) allPermissions.value.push(...g.permissions)
      })
    } else {
      ElMessage.error(t('permission.permission.fetchFailed'))
    }
  } catch (error) {
    console.error('获取权限列表失败:', error)
    ElMessage.error(t('permission.permission.fetchFailed'))
  } finally {
    loading.value = false
  }
}

const fetchRolePermissions = async (roleId) => {
  try {
    const response = await request.get(`/permissions/role/${roleId}`)
    if (response.code === 200) {
      checkedPermissionIds.value = response.data || []
      updateGroupCheckStates()
    }
  } catch (error) {
    console.error('获取角色权限失败:', error)
  }
}

const updateGroupCheckStates = () => {
  permissionGroups.value.forEach(group => {
    const permIds = group.permissions.map(p => p.id)
    const checkedCount = permIds.filter(id => checkedPermissionIds.value.includes(id)).length
    group.checkedAll = checkedCount === permIds.length && permIds.length > 0
    group.indeterminate = checkedCount > 0 && checkedCount < permIds.length
  })
}

const handleRoleChange = (roleId) => {
  if (roleId) {
    fetchRolePermissions(roleId)
  } else {
    checkedPermissionIds.value = []
    updateGroupCheckStates()
  }
}

const handleGroupCheckAll = (group, checked) => {
  const permIds = group.permissions.map(p => p.id)
  if (checked) {
    const newIds = permIds.filter(id => !checkedPermissionIds.value.includes(id))
    checkedPermissionIds.value = [...checkedPermissionIds.value, ...newIds]
  } else {
    checkedPermissionIds.value = checkedPermissionIds.value.filter(id => !permIds.includes(id))
  }
  updateGroupCheckStates()
}

const handlePermCheck = (permId, checked) => {
  if (checked) {
    if (!checkedPermissionIds.value.includes(permId)) {
      checkedPermissionIds.value = [...checkedPermissionIds.value, permId]
    }
  } else {
    checkedPermissionIds.value = checkedPermissionIds.value.filter(id => id !== permId)
  }
  updateGroupCheckStates()
}

const handleSave = async () => {
  if (!selectedRoleId.value) return
  saving.value = true
  try {
    const response = await request.put(`/permissions/role/${selectedRoleId.value}`, checkedPermissionIds.value)
    if (response.code === 200) {
      ElMessage.success(t('permission.permission.saveSuccess'))
    } else {
      ElMessage.error(response.message || t('permission.permission.saveFailed'))
    }
  } catch (error) {
    console.error('保存权限失败:', error)
    ElMessage.error(t('permission.permission.saveFailed'))
  } finally {
    saving.value = false
  }
}

// ==================== 权限定义管理 ====================
const allPermissions = ref([])
const permTableLoading = ref(false)
const canCreatePermission = computed(() => userStore.hasPermission('permission:create'))
const canEditPermission = computed(() => userStore.hasPermission('permission:edit'))
const canDeletePermission = computed(() => userStore.hasPermission('permission:delete'))

// 权限建议数据（以分组为一级的级联结构）
const groupSuggestions = ref([])   // [{ value, label, codes: [...], names: [...] }]
const existingCodes = ref(new Set())

const permDialogVisible = ref(false)
const permDialogTitle = ref('')
const isPermEdit = ref(false)
const permSubmitLoading = ref(false)
const permFormRef = ref()

const permForm = reactive({
  id: null,
  name: '',
  code: '',
  groupName: '',
  type: 'PAGE'
})

const permRules = computed(() => ({
  groupName: [
    { required: true, message: t('permission.permission.permGroupRequired'), trigger: 'blur' }
  ],
  name: [
    { required: true, message: t('permission.permission.permNameRequired'), trigger: 'blur' }
  ],
  code: [
    { required: true, message: t('permission.permission.permCodeRequired'), trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9\-]*(:[a-z][a-z0-9\-]*)+$/, message: t('permission.permission.permCodeFormat'), trigger: 'blur' }
  ],
  type: [
    { required: true, message: t('permission.permission.permTypeRequired'), trigger: 'change' }
  ]
}))

// 当前选中分组下的编码和名称
const currentGroupData = computed(() => {
  if (!permForm.groupName) return null
  return groupSuggestions.value.find(g => g.value === permForm.groupName) || null
})

// 根据选中分组过滤编码
const filteredCodeSuggestions = computed(() => {
  if (currentGroupData.value) return currentGroupData.value.codes || []
  // 未选分组时展示全部编码
  const all = []
  groupSuggestions.value.forEach(g => {
    if (g.codes) all.push(...g.codes)
  })
  return all
})

// 获取权限建议数据
const fetchPermissionSuggestions = async () => {
  try {
    const response = await request.get('/permissions/suggestions')
    if (response.code === 200) {
      groupSuggestions.value = response.data?.groups || []
      existingCodes.value = new Set(response.data?.existingCodes || [])
    }
  } catch (error) {
    console.error('获取权限建议数据失败:', error)
  }
}

// 选择分组后自动清空名称和编码（等待用户从该分组下选择）
const handleGroupChange = (groupName) => {
  if (!groupName) return
  permForm.name = ''
  permForm.code = ''
  permForm.type = 'PAGE'
}

// 选择编码后自动填充名称、类型
const handleCodeChange = (code) => {
  if (!code) return
  // 在过滤后的编码列表中查找
  const suggestion = filteredCodeSuggestions.value.find(item => item.value === code)
  if (suggestion) {
    permForm.name = suggestion.name || code
    permForm.type = suggestion.type || autoDetectType(code)
  } else {
    // 手动输入时，用编码作为名称，自动检测类型
    permForm.name = code
    permForm.type = autoDetectType(code)
  }
}

// 判断分组下所有权限编码是否都已被添加
const isGroupFullyUsed = (group) => {
  if (!group.codes || group.codes.length === 0) return false
  return group.codes.every(c => c.disabled)
}

// 根据编码自动识别类型
const autoDetectType = (code) => {
  if (!code) return 'PAGE'
  if (code.endsWith(':view')) return 'PAGE'
  return 'BUTTON'
}

const handleCreatePerm = () => {
  permDialogTitle.value = t('permission.permission.createPermission')
  isPermEdit.value = false
  permForm.id = null
  permForm.groupName = ''
  permForm.name = ''
  permForm.code = ''
  permForm.type = 'PAGE'
  fetchPermissionSuggestions()
  permDialogVisible.value = true
}

const handleEditPerm = (row) => {
  permDialogTitle.value = t('permission.permission.editPermission')
  isPermEdit.value = true
  permForm.id = row.id
  permForm.groupName = row.groupName
  permForm.name = row.name
  permForm.code = row.code
  permForm.type = row.type
  permDialogVisible.value = true
}

const handleDeletePerm = async (row) => {
  try {
    await ElMessageBox.confirm(
      t('permission.permission.confirmDeletePerm', { name: row.name }),
      t('common.warning'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'error',
        confirmButtonClass: 'el-button--danger'
      }
    )
    const response = await request.delete(`/permissions/${row.id}`)
    if (response.code === 200) {
      ElMessage.success(t('permission.permission.deletePermSuccess'))
      fetchPermissions()
    } else {
      ElMessage.error(response.message || t('permission.permission.deletePermFailed'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除权限失败:', error)
      ElMessage.error(t('permission.permission.deletePermFailed'))
    }
  }
}

const handlePermSubmit = async () => {
  try {
    await permFormRef.value.validate()
    permSubmitLoading.value = true

    const submitData = {
      name: permForm.name,
      code: permForm.code,
      groupName: permForm.groupName,
      type: permForm.type
    }

    const response = isPermEdit.value
      ? await request.put(`/permissions/${permForm.id}`, submitData)
      : await request.post('/permissions', submitData)

    if (response.code === 200) {
      ElMessage.success(isPermEdit.value ? t('permission.permission.editPermSuccess') : t('permission.permission.createPermSuccess'))
      permDialogVisible.value = false
      fetchPermissions()
    } else {
      ElMessage.error(response.message || (isPermEdit.value ? t('permission.permission.editPermFailed') : t('permission.permission.createPermFailed')))
    }
  } catch (error) {
    console.error('提交权限失败:', error)
    ElMessage.error(isPermEdit.value ? t('permission.permission.editPermFailed') : t('permission.permission.createPermFailed'))
  } finally {
    permSubmitLoading.value = false
  }
}

const handlePermDialogClose = () => {
  permFormRef.value?.resetFields()
}

onMounted(() => {
  fetchRoles()
  fetchPermissions()
  fetchPermissionSuggestions()
})
</script>

<style scoped>
.permissions {
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

.select-card {
  margin-bottom: 20px;
}

.select-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.select-label {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}

.select-hint {
  font-size: 13px;
  color: #909399;
}

.perms-count {
  font-size: 13px;
  color: #409EFF;
  font-weight: 500;
}

.perms-card {
  margin-bottom: 20px;
}

.perm-group {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;
}

.perm-group:last-child {
  border-bottom: none;
}

.perm-group-header {
  margin-bottom: 12px;
}

.perm-items {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 24px;
  padding-left: 24px;
}

.perm-item {
  min-width: 220px;
}

.perm-name {
  margin-right: 6px;
}

.perm-type-tag {
  vertical-align: middle;
}

.perms-footer {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
  text-align: center;
}

/* 权限定义管理 */
.perm-manage-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.perm-total-hint {
  font-size: 13px;
  color: #909399;
}

.code-tag {
  font-family: 'Menlo', 'Consolas', monospace;
}

.form-hint {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
</style>
