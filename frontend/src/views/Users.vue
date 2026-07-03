<!--
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
-->

<template>
  <div class="users">
    <div class="page-header">
      <h1>{{ $t('userManagement.title') }}</h1>
      <el-button type="primary" @click="handleCreate" v-if="canCreateUsers">
        <Plus :width="'1em'" :height="'1em'" />
        {{ $t('userManagement.createUser') }}
      </el-button>
    </div>

    <el-card class="search-card">
      <el-row :gutter="20">
        <el-col :span="5">
          <el-input v-model="searchForm.username" :placeholder="$t('userManagement.searchByUsername')" clearable @clear="handleSearch" />
        </el-col>
        <el-col :span="5">
          <el-input v-model="searchForm.email" :placeholder="$t('userManagement.searchByEmail')" clearable @clear="handleSearch" />
        </el-col>
        <el-col :span="5">
          <el-select v-model="searchForm.roleId" :placeholder="$t('userManagement.searchByRole')" clearable @change="handleSearch" style="width: 100%">
            <el-option
              v-for="r in roleOptions"
              :key="r.id"
              :label="r.name"
              :value="r.id"
            />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="searchForm.enabled" :placeholder="$t('userManagement.searchByStatus')" clearable @change="handleSearch" style="width: 100%">
            <el-option :label="$t('userManagement.enabledStatus')" :value="true" />
            <el-option :label="$t('userManagement.disabledStatus')" :value="false" />
          </el-select>
        </el-col>
        <el-col :span="5">
          <el-button type="primary" @click="handleSearch">{{ $t('userManagement.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('userManagement.reset') }}</el-button>
        </el-col>
      </el-row>
    </el-card>

    <el-card class="table-card">
      <el-table
        v-loading="loading"
        :data="userList"
        border
        style="width: 100%"
        :header-cell-style="{ background: '#f5f7fa' }"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" :label="$t('userManagement.username')" min-width="120" />
        <el-table-column prop="email" :label="$t('userManagement.email')" min-width="180" />
        <el-table-column prop="role" :label="$t('userManagement.role')" width="120" align="center">
          <template #default="{ row }">
            <el-tag effect="plain" class="role-tag" :style="getRoleTagStyle(row.roleId, row.role)">
              {{ getRoleName(row.roleId, row.role) || row.role }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" :label="$t('userManagement.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? $t('userManagement.enabledStatus') : $t('userManagement.disabledStatus') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('userManagement.createdAt')" width="200">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('userManagement.actions')" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)" :disabled="!canEditUsers">{{ $t('userManagement.edit') }}</el-button>
            <el-button type="danger" link @click="handleDelete(row)" :disabled="!canDeleteUsers">{{ $t('userManagement.delete') }}</el-button>
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
        <el-form-item :label="$t('userManagement.username')" prop="username">
          <el-input v-model="form.username" :placeholder="$t('userManagement.usernamePlaceholder')" :disabled="isEdit" />
        </el-form-item>
        <el-form-item :label="$t('userManagement.email')" prop="email">
          <el-input v-model="form.email" :placeholder="$t('userManagement.emailPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('userManagement.password')" prop="password" v-if="!isEdit">
          <el-input v-model="form.password" type="password" :placeholder="$t('userManagement.passwordPlaceholder')" show-password />
        </el-form-item>
        <el-form-item :label="$t('userManagement.password')" prop="password" v-if="isEdit">
          <el-input v-model="form.password" type="password" :placeholder="$t('userManagement.newPasswordOptional')" show-password />
        </el-form-item>
        <el-form-item :label="$t('userManagement.role')" prop="role">
          <el-select v-model="form.role" :placeholder="$t('userManagement.rolePlaceholder')" style="width: 100%">
            <el-option
              v-for="r in roleOptions"
              :key="r.code"
              :label="r.name"
              :value="r.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('userManagement.status')" prop="enabled">
          <el-switch v-model="form.enabled" :active-text="$t('userManagement.enabledStatus')" :inactive-text="$t('userManagement.disabledStatus')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Edit, Delete } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { formatTime, loadDateFormat } from '@/utils/dateFormat'
import { useUserStore } from '@/stores/user'
import { getRoleTagStyle as getRoleTagStyleByRoleName } from '@/utils/roleColors'

const { t } = useI18n()
const userStore = useUserStore()

const isAdmin = computed(() => userStore.isAdmin)
const canViewUsers = computed(() => userStore.hasPermission('user:view'))
const canEditUsers = computed(() => userStore.hasPermission('user:edit'))
const canDeleteUsers = computed(() => userStore.hasPermission('user:delete'))
const canCreateUsers = computed(() => userStore.hasPermission('user:create'))

// 从API获取的角色列表（含自定义角色，搜索和编辑共用）
const roleOptions = ref([])

const searchForm = reactive({
  username: '',
  email: '',
  roleId: null,
  enabled: null
})

const loading = ref(false)
const userList = ref([])

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
  username: '',
  email: '',
  password: '',
  role: 'USER',
  roleId: null,
  enabled: true
})

const rules = computed(() => ({
  username: [
    { required: true, message: t('userManagement.usernameRequired'), trigger: 'blur' },
    { min: 3, max: 50, message: t('userManagement.usernameLength'), trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: t('userManagement.usernameFormat'), trigger: 'blur' }
  ],
  email: [
    { required: true, message: t('userManagement.emailRequired'), trigger: 'blur' },
    { type: 'email', message: t('userManagement.emailFormat'), trigger: 'blur' }
  ],
  password: [
    { required: !isEdit.value, message: t('userManagement.passwordRequired'), trigger: 'blur' },
    { 
      validator: (rule, value, callback) => {
        // 编辑模式下如果密码为空则跳过验证
        if (isEdit.value && (!value || value.length === 0)) {
          callback()
          return
        }
        // 检查长度
        if (value && value.length < 8) {
          callback(new Error(t('userManagement.passwordMinLength')))
          return
        }
        // 检查强度
        const pattern = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]+$/
        if (value && !pattern.test(value)) {
          callback(new Error(t('userManagement.passwordStrength')))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  role: [
    { required: true, message: t('userManagement.roleRequired'), trigger: 'change' }
  ]
}))

const fetchUsers = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.current - 1,
      size: pagination.pageSize
    }
    if (searchForm.username) params.username = searchForm.username
    if (searchForm.email) params.email = searchForm.email
    if (searchForm.roleId) params.roleId = searchForm.roleId
    if (searchForm.enabled !== null && searchForm.enabled !== '') params.enabled = searchForm.enabled

    const response = await request({
      url: '/users',
      method: 'get',
      params
    })
    if (response.code === 200) {
      userList.value = response.data.content || []
      pagination.total = response.data.totalElements || 0
    } else {
      ElMessage.error(t('userManagement.fetchFailed'))
    }
  } catch (error) {
    console.error('获取用户列表失败:', error)
    ElMessage.error(t('userManagement.fetchFailed'))
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.current = 1
  fetchUsers()
}

const handleReset = () => {
  searchForm.username = ''
  searchForm.email = ''
  searchForm.roleId = null
  searchForm.enabled = null
  handleSearch()
}

const handleSizeChange = (size) => {
  pagination.pageSize = size
  fetchUsers()
}

const handleCurrentChange = (page) => {
  pagination.current = page
  fetchUsers()
}

const handleCreate = () => {
  if (!canCreateUsers.value) {
    ElMessage.error(t('userManagement.noPermission'))
    return
  }
  dialogTitle.value = t('userManagement.createUser')
  isEdit.value = false
  form.id = null
  form.username = ''
  form.email = ''
  form.password = ''
  // 使用角色编码匹配下拉选项（提交时自动转为后端枚举值）
  form.role = 'ROLE_USER'
  form.roleId = null
  form.enabled = true
  dialogVisible.value = true
}

const handleEdit = (row) => {
  if (!canEditUsers.value) {
    ElMessage.error(t('userManagement.noEditPermission'))
    return
  }

  dialogTitle.value = t('userManagement.editUser')
  isEdit.value = true
  form.id = row.id
  form.username = row.username
  form.email = row.email
  form.password = ''
  // 根据 roleId 查找对应的 Role.code，匹配下拉选项；找不到则用旧枚举值兜底
  const matchedRole = roleOptions.value.find(r => r.id === row.roleId)
  form.role = matchedRole ? matchedRole.code : ('ROLE_' + row.role)
  form.roleId = row.roleId
  form.enabled = row.enabled
  dialogVisible.value = true
}

const handleDelete = async (row) => {
  if (!canDeleteUsers.value) {
    ElMessage.error(t('userManagement.noDeletePermission'))
    return
  }

  if (row.id === userStore.userInfo.id) {
    ElMessage.error(t('userManagement.cannotDeleteSelf'))
    return
  }

  try {
    await ElMessageBox.confirm(t('userManagement.confirmDelete', { name: row.username }), t('common.warning'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'error',
      confirmButtonClass: 'el-button--danger'
    })

    const response = await request({
      url: `/users/${row.id}`,
      method: 'delete'
    })
    if (response.code === 200) {
      ElMessage.success(t('userManagement.deleteSuccess'))
      fetchUsers()
    } else {
      ElMessage.error(t('userManagement.deleteFailed'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error(t('userManagement.deleteFailed'))
    }
  }
}

const handleSubmit = async () => {
  try {
    await formRef.value.validate()
    submitLoading.value = true

    const submitData = { ...form }
    // 编辑模式下如果密码为空则移除该字段，后端不更新密码
    if (isEdit.value && !submitData.password) {
      delete submitData.password
    }
    // 将角色编码(如 ROLE_USER / ROLE_AI_CHAT)映射到后端 UserRole 枚举:
    // ROLE_ADMIN → ADMIN，其余 → USER（自定义角色通过 roleId 区分权限）
    const selectedRole = roleOptions.value.find(r => r.code === form.role)
    if (selectedRole) {
      submitData.role = (selectedRole.code === 'ROLE_ADMIN') ? 'ADMIN' : 'USER'
      submitData.roleId = selectedRole.id
    } else if (submitData.role && submitData.role.startsWith('ROLE_')) {
      submitData.role = submitData.role.substring(5)
    }

    const response = isEdit.value
      ? await request({
          url: `/users/${form.id}`,
          method: 'put',
          data: submitData
        })
      : await request({
          url: '/users',
          method: 'post',
          data: submitData
        })

    if (response.code === 200) {
      ElMessage.success(isEdit.value ? t('userManagement.editSuccess') : t('userManagement.createSuccess'))
      dialogVisible.value = false
      fetchUsers()
    } else {
      ElMessage.error(response.message || (isEdit.value ? t('userManagement.editFailed') : t('userManagement.createFailed')))
    }
  } catch (error) {
    console.error('提交失败:', error)
    ElMessage.error(isEdit.value ? t('userManagement.editFailed') : t('userManagement.createFailed'))
  } finally {
    submitLoading.value = false
  }
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
}

// 获取可用角色列表（包含自定义角色，通过 roleId 分配细粒度权限）
const fetchRoles = async () => {
  try {
    const response = await request.get('/users/roles')
    if (response.code === 200) {
      roleOptions.value = response.data || []
    }
  } catch (error) {
    console.error('获取角色列表失败:', error)
  }
}

// 根据角色ID/编码获取角色名称：优先 roleId（自定义角色），兜底 roleCode（基础角色）
const getRoleName = (roleId, roleCode) => {
  if (roleId) {
    const role = roleOptions.value.find(r => r.id === roleId)
    if (role) return role.name
  }
  const role = roleOptions.value.find(r => r.code === roleCode || r.code === 'ROLE_' + roleCode)
  return role ? role.name : null
}

// 角色标签样式：背景色取自 Issue #13 指定的 20 色色板（按角色名稳定取色），字体固定黑色
const getRoleTagStyle = (roleId, roleCode) => {
  const roleName = getRoleName(roleId, roleCode) || roleCode || ''
  return getRoleTagStyleByRoleName(roleName)
}

onMounted(async () => {
  await loadDateFormat()
  if (!canViewUsers.value) {
    ElMessage.error(t('userManagement.noPermission'))
    return
  }
  await fetchRoles()
  fetchUsers()
})
</script>

<style scoped>
.users {
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
</style>

