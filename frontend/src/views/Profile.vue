<!--
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
-->

<template>
  <div class="profile-page">
    <div class="page-header">
      <h1>{{ $t('profile.title') }}</h1>
    </div>

    <el-row :gutter="20">
      <!-- 左侧：用户头像和信息卡片 -->
      <el-col :span="8">
        <el-card class="user-card" shadow="hover">
          <div class="avatar-section">
            <el-avatar :size="100" :src="avatarSrc" @error="handleAvatarError">
              <el-icon :size="50"><UserFilled /></el-icon>
            </el-avatar>
            <h2 class="username">{{ form.username }}</h2>
            <el-tag :type="form.role === 'ADMIN' ? 'danger' : 'info'" size="small">
              {{ form.role === 'ADMIN' ? $t('profile.admin') : $t('profile.normalUser') }}
            </el-tag>
          </div>
          <el-divider />
          <div class="info-list">
            <div class="info-item">
              <el-icon><Calendar /></el-icon>
              <span>{{ $t('profile.createdAt') }}</span>
              <span class="info-value">{{ formatTime(form.createTime) }}</span>
            </div>
            <div class="info-item">
              <el-icon><Clock /></el-icon>
              <span>{{ $t('profile.updatedAt') }}</span>
              <span class="info-value">{{ formatTime(form.updateTime) }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：编辑表单 -->
      <el-col :span="16">
        <el-card class="form-card" shadow="hover">
          <h2>{{ $t('profile.editProfile') }}</h2>
          <el-divider />
          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-width="100px"
            style="max-width: 500px;"
            v-loading="loading"
          >
            <el-form-item :label="$t('profile.username')">
              <el-input v-model="form.username" disabled>
                <template #prefix>
                  <el-icon><User /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item :label="$t('profile.email')" prop="email">
              <el-input v-model="form.email" :placeholder="$t('profile.emailPlaceholder')">
                <template #prefix>
                  <el-icon><Message /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item :label="$t('profile.language')">
              <el-select v-model="form.language" style="width: 100%">
                <el-option label="中文" value="zh-CN" />
                <el-option label="English" value="en-US" />
                <el-option label="日本語" value="ja-JP" />
              </el-select>
            </el-form-item>

            <el-form-item :label="$t('profile.role')">
              <el-tag :type="form.role === 'ADMIN' ? 'danger' : ''">
                {{ form.role === 'ADMIN' ? $t('profile.admin') : $t('profile.normalUser') }}
              </el-tag>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="handleSave" :loading="saving">
                {{ $t('profile.save') }}
              </el-button>
              <el-button @click="handleReset">{{ $t('profile.reset') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 修改密码卡片 -->
        <el-card class="form-card" shadow="hover" style="margin-top: 20px;">
          <h2>{{ $t('profile.changePassword') }}</h2>
          <el-divider />
          <el-form
            ref="passwordFormRef"
            :model="passwordForm"
            :rules="passwordRules"
            label-width="160px"
            style="max-width: 540px;"
          >
            <el-form-item :label="$t('profile.currentPassword')" prop="oldPassword">
              <el-input
                v-model="passwordForm.oldPassword"
                type="password"
                show-password
                :placeholder="$t('profile.currentPasswordPlaceholder')"
              >
                <template #prefix>
                  <el-icon><Lock /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item :label="$t('profile.newPassword')" prop="newPassword">
              <el-input
                v-model="passwordForm.newPassword"
                type="password"
                show-password
                :placeholder="$t('profile.newPasswordPlaceholder')"
              >
                <template #prefix>
                  <el-icon><Key /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item :label="$t('profile.confirmPassword')" prop="confirmPassword">
              <el-input
                v-model="passwordForm.confirmPassword"
                type="password"
                show-password
                :placeholder="$t('profile.confirmPasswordPlaceholder')"
              >
                <template #prefix>
                  <el-icon><CircleCheck /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="handleChangePassword" :loading="changingPassword">
                {{ $t('profile.savePassword') }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UserFilled, User, Message, Lock, Key, CircleCheck, Calendar, Clock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useI18n } from 'vue-i18n'
import { logout } from '@/api/auth'
import request from '@/utils/request'

const { t, locale } = useI18n()
const userStore = useUserStore()
const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const changingPassword = ref(false)
const formRef = ref(null)
const passwordFormRef = ref(null)

// 个人信息表单
const form = reactive({
  id: null,
  username: '',
  email: '',
  language: 'zh-CN',
  role: '',
  createTime: '',
  updateTime: ''
})

// 邮箱验证规则（使用 computed 确保多语言响应）
const rules = computed(() => ({
  email: [
    { required: true, message: t('profile.emailRequired'), trigger: 'blur' },
    { type: 'email', message: t('profile.emailInvalid'), trigger: ['blur', 'change'] }
  ]
}))

// 修改密码表单
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value === '') {
    callback(new Error(t('profile.confirmPasswordRequired')))
  } else if (value !== passwordForm.newPassword) {
    callback(new Error(t('profile.passwordMismatch')))
  } else {
    callback()
  }
}

// 密码验证规则（使用 computed 确保多语言响应）
const passwordRules = computed(() => ({
  oldPassword: [
    { required: true, message: t('profile.currentPasswordPlaceholder'), trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: t('profile.newPasswordPlaceholder'), trigger: 'blur' },
    { min: 8, message: t('profile.passwordMinLength'), trigger: 'blur' },
    { pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]+$/, message: t('profile.passwordStrengthHint'), trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: t('profile.confirmPasswordRequired'), trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}))

// 格式化时间（跟随当前语言）
const formatTime = (time) => {
  if (!time) return '-'
  const langMap = { 'zh-CN': 'zh-CN', 'en-US': 'en-US', 'ja-JP': 'ja-JP' }
  return new Date(time).toLocaleString(langMap[locale.value] || 'zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// 头像加载错误处理 - 回退到本地默认头像
const avatarSrc = ref(userStore.userAvatar)
const handleAvatarError = () => {
  avatarSrc.value = '/default-avatar.png'
}

// 用户信息变更时重置头像源
watch(() => userStore.userAvatar, (newVal) => {
  avatarSrc.value = newVal
})

// 获取用户信息
const fetchProfile = async () => {
  loading.value = true
  try {
    const response = await request.get('/users/profile')
    if (response.code === 200) {
      const user = response.data
      form.id = user.id
      form.username = user.username
      form.email = user.email || ''
      form.language = user.language || 'zh-CN'
      form.role = user.role || 'USER'
      form.createTime = user.createTime
      form.updateTime = user.updateTime
    } else {
      ElMessage.error(response.message || t('profile.profileLoadFailed'))
    }
  } catch (error) {
    console.error('获取用户信息失败:', error)
    ElMessage.error(t('profile.profileLoadFailed'))
  } finally {
    loading.value = false
  }
}

// 保存个人信息
const handleSave = async () => {
  if (!formRef.value) return

  // 先清除密码表单的残留校验状态，避免与个人信息表单相互干扰
  passwordFormRef.value?.clearValidate()

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  saving.value = true
  try {
    const response = await request.put('/users/update-profile', {
      id: form.id,
      email: form.email,
      language: form.language
    })
    if (response.code === 200) {
      ElMessage.success(t('profile.profileSaved'))
      // 更新 store 中的用户信息
      userStore.userInfo.email = form.email
      userStore.userInfo.language = form.language
      localStorage.setItem('userInfo', JSON.stringify(userStore.userInfo))
      // 切换语言
      if (locale.value !== form.language) {
        locale.value = form.language
        localStorage.setItem('locale', form.language)
      }
      // 刷新数据
      await fetchProfile()
      // 语言切换和表单刷新后，重新清除密码区域残留校验状态（避免重渲染时 Element Plus 自动触发空值校验）
      passwordFormRef.value?.clearValidate()
    } else {
      ElMessage.error(response.message || t('profile.profileSaveFailed'))
    }
  } catch (error) {
    console.error('更新个人信息失败:', error)
    ElMessage.error(t('profile.profileSaveFailed'))
  } finally {
    saving.value = false
  }
}

// 重置表单
const handleReset = () => {
  fetchProfile()
}

// 修改密码
const handleChangePassword = async () => {
  if (!passwordFormRef.value) return

  try {
    await passwordFormRef.value.validate()
  } catch {
    return
  }

  // 二次确认
  try {
    await ElMessageBox.confirm(
      t('profile.confirmChangePassword'),
      t('common.warning'),
      { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'warning' }
    )
  } catch {
    return
  }

  changingPassword.value = true
  try {
    const response = await request.post('/users/change-password', {
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
      confirmPassword: passwordForm.confirmPassword
    })
    if (response.code === 200) {
      ElMessage.success(t('profile.passwordChanged'))
      // 清空表单
      passwordForm.oldPassword = ''
      passwordForm.newPassword = ''
      passwordForm.confirmPassword = ''
      passwordFormRef.value?.resetFields()
      // 退出登录
      setTimeout(async () => {
        try {
          await logout()
        } catch (e) {
          console.warn('后端登出调用失败', e)
        }
        userStore.logout()
        window.location.href = '/'
      }, 1500)
    } else {
      ElMessage.error(response.message || t('profile.passwordChangeFailed'))
    }
  } catch (error) {
    console.error('修改密码失败:', error)
    ElMessage.error(t('profile.passwordChangeFailed'))
  } finally {
    changingPassword.value = false
  }
}

onMounted(() => {
  fetchProfile()
})
</script>

<style scoped>
.profile-page {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

.user-card {
  text-align: center;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.avatar-section .username {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.info-list {
  text-align: left;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  font-size: 14px;
  color: #606266;
}

.info-item .info-value {
  margin-left: auto;
  color: #909399;
  font-size: 13px;
}

.form-card h2 {
  margin: 0 0 10px 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

@media (max-width: 768px) {
  .profile-page {
    padding: 10px;
  }
}
</style>
