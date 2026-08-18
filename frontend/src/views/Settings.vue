<!--
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
-->

<template>
  <div class="settings">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1>{{ $t('settings.title') }}</h1>
    </div>

    <el-row :gutter="20">
      <!-- 左侧菜单 -->
      <el-col :span="6">
        <el-card class="menu-card">
          <el-menu :default-active="activeMenu" @select="handleMenuSelect">
            <el-menu-item index="basic" v-if="canBasic || canSystem">
              <Setting :width="'1em'" :height="'1em'" />
              <span>{{ $t('settings.basic') }}</span>
            </el-menu-item>
            <el-menu-item index="security" v-if="canSecurity">
              <Lock :width="'1em'" :height="'1em'" />
              <span>{{ $t('settings.security') }}</span>
            </el-menu-item>
            <el-menu-item index="jwt" v-if="canJwt">
              <Key :width="'1em'" :height="'1em'" />
              <span>{{ $t('settings.jwt') }}</span>
            </el-menu-item>
            <el-menu-item index="mock" v-if="canMock">
              <Connection :width="'1em'" :height="'1em'" />
              <span>{{ $t('settings.mock') }}</span>
            </el-menu-item>
            <el-menu-item index="announcement" v-if="canAnnouncement">
              <Bell :width="'1em'" :height="'1em'" />
              <span>{{ $t('settings.announcement') }}</span>
            </el-menu-item>
            <el-menu-item index="system" v-if="canSystem">
              <InfoFilled :width="'1em'" :height="'1em'" />
              <span>{{ $t('settings.system') }}</span>
            </el-menu-item>
            <el-menu-item index="footer" v-if="canFooter">
              <Connection :width="'1em'" :height="'1em'" />
              <span>{{ $t('settings.footer') }}</span>
            </el-menu-item>
            <el-menu-item index="registration" v-if="canRegistration">
              <UserFilled :width="'1em'" :height="'1em'" />
              <span>{{ $t('settings.registration') }}</span>
            </el-menu-item>
          </el-menu>
        </el-card>
      </el-col>

      <!-- 右侧内容 -->
      <el-col :span="18">
        <el-card class="content-card">
          <!-- 基础设置 -->
          <div v-if="activeMenu === 'basic'">
            <h2>{{ $t('settings.basic') }}</h2>
            <el-divider />
            <el-form :model="basicSettings" label-width="150px">
              <el-form-item :label="$t('settings.appName')">
                <el-input v-model="basicSettings.appName" disabled />
              </el-form-item>
              <el-form-item :label="$t('settings.version')">
                <el-input v-model="basicSettings.version" disabled />
              </el-form-item>
              <el-form-item :label="$t('settings.language')">
                <el-select v-model="basicSettings.language" style="width: 100%">
                  <el-option :label="$t('settings.langZhCN')" value="zh-CN" />
                  <el-option :label="$t('settings.langEnUS')" value="en-US" />
                  <el-option :label="$t('settings.langJaJP')" value="ja-JP" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$t('settings.dateFormat')">
                <el-select v-model="basicSettings.dateFormat" style="width: 100%">
                  <el-option label="YYYY-MM-DD" value="YYYY-MM-DD" />
                  <el-option label="DD/MM/YYYY" value="DD/MM/YYYY" />
                  <el-option label="MM/DD/YYYY" value="MM/DD/YYYY" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="saveBasicSettings" :loading="saving">{{ $t('settings.saveSettings') }}</el-button>
                <el-button @click="resetBasicSettings">{{ $t('settings.resetSettings') }}</el-button>
              </el-form-item>
            </el-form>
          </div>

          <!-- 安全配置 -->
          <div v-if="activeMenu === 'security'">
            <h2>{{ $t('settings.security') }}</h2>
            <el-divider />
            <el-alert :title="$t('settings.requireRestart')" type="warning" :closable="false" show-icon />
            <br />
            <el-form :model="securitySettings" label-width="180px">
              <el-form-item :label="$t('settings.passwordStrength')">
                <el-checkbox v-model="securitySettings.requireUppercase">{{ $t('settings.requireUppercase') }}</el-checkbox>
                <el-checkbox v-model="securitySettings.requireLowercase">{{ $t('settings.requireLowercase') }}</el-checkbox>
                <el-checkbox v-model="securitySettings.requireDigit">{{ $t('settings.requireDigit') }}</el-checkbox>
                <el-checkbox v-model="securitySettings.requireSpecial">{{ $t('settings.requireSpecial') }}</el-checkbox>
              </el-form-item>
              <el-form-item :label="$t('settings.minPasswordLength')">
                <el-input-number v-model="securitySettings.minPasswordLength" :min="8" :max="32" />
              </el-form-item>
              <el-form-item :label="$t('settings.maxLoginAttempts')">
                <el-input-number v-model="securitySettings.maxLoginAttempts" :min="3" :max="10" />
              </el-form-item>
              <el-form-item :label="$t('settings.lockoutDuration')">
                <el-input-number v-model="securitySettings.lockoutDuration" :min="5" :max="60" />
              </el-form-item>
              <el-form-item :label="$t('settings.enableIpWhitelist')">
                <el-switch v-model="securitySettings.enableIpWhitelist" />
              </el-form-item>
              <el-form-item :label="$t('settings.ipWhitelist')" v-if="securitySettings.enableIpWhitelist">
                <el-input
                  v-model="securitySettings.ipWhitelist"
                  type="textarea"
                  :rows="4"
                  :placeholder="$t('settings.ipWhitelistPlaceholder')"
                />
              </el-form-item>

              <!-- iframe 嵌入控制（保存后即时生效，无需重启） -->
              <el-divider content-position="left">
                <el-tag size="small" type="info">iframe</el-tag>
                {{ $t('settings.iframeEmbedding') }}
              </el-divider>
              <el-form-item :label="$t('settings.iframeEmbedding')">
                <el-radio-group v-model="securitySettings.iframeMode">
                  <el-radio value="disable">{{ $t('settings.iframeModeDisable') }}</el-radio>
                  <el-radio value="whitelist">{{ $t('settings.iframeModeWhitelist') }}</el-radio>
                  <el-radio value="allowAll">{{ $t('settings.iframeModeAllowAll') }}</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item v-if="securitySettings.iframeMode === 'whitelist'" :label="$t('settings.iframeAllowedOrigins')">
                <el-input
                  v-model="securitySettings.iframeAllowedOrigins"
                  type="textarea"
                  :rows="4"
                  :placeholder="$t('settings.iframeAllowedOriginsPlaceholder')"
                />
                <div class="iframe-hint">{{ $t('settings.iframeHint') }}</div>
              </el-form-item>

              <el-form-item>
                <el-button type="primary" @click="saveSecuritySettings" :loading="saving">{{ $t('settings.saveSettings') }}</el-button>
                <el-button @click="resetSecuritySettings">{{ $t('settings.resetSettings') }}</el-button>
              </el-form-item>
            </el-form>
          </div>

          <!-- JWT配置 -->
          <div v-if="activeMenu === 'jwt'">
            <h2>{{ $t('settings.jwt') }}</h2>
            <el-divider />
            <el-alert :title="$t('settings.jwtWarning')" type="warning" :closable="false" show-icon />
            <br />
            <el-form :model="jwtSettings" label-width="180px">
              <el-form-item :label="$t('settings.tokenExpiration')">
                <el-input-number v-model="jwtSettings.tokenExpiration" :min="900" :max="86400" step="300" />
                <span style="margin-left: 10px; color: #909399;">{{ $t('settings.tokenExpirationUnit') }}</span>
              </el-form-item>
              <el-form-item :label="$t('settings.refreshTokenExpiration')">
                <el-input-number v-model="jwtSettings.refreshTokenExpiration" :min="3600" :max="604800" step="3600" />
                <span style="margin-left: 10px; color: #909399;">{{ $t('settings.refreshTokenExpirationUnit') }}</span>
              </el-form-item>
              <el-form-item :label="$t('settings.issuer')">
                <el-input v-model="jwtSettings.issuer" :placeholder="$t('settings.issuerPlaceholder')" />
              </el-form-item>
              <el-form-item :label="$t('settings.audience')">
                <el-input v-model="jwtSettings.audience" :placeholder="$t('settings.audiencePlaceholder')" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="saveJwtSettings" :loading="saving">{{ $t('settings.saveSettings') }}</el-button>
                <el-button @click="resetJwtSettings">{{ $t('settings.resetSettings') }}</el-button>
              </el-form-item>
            </el-form>
          </div>

          <!-- Mock配置 -->
          <div v-if="activeMenu === 'mock'">
            <h2>{{ $t('settings.mock') }}</h2>
            <el-divider />
            <el-form :model="mockSettings" label-width="180px">
              <el-form-item :label="$t('settings.defaultResponseDelay')">
                <el-input-number v-model="mockSettings.defaultResponseDelay" :min="0" :max="5000" step="100" />
                <span style="margin-left: 10px; color: #909399;">{{ $t('settings.responseDelayUnit') }}</span>
              </el-form-item>
              <el-form-item :label="$t('settings.maxResponseDelay')">
                <el-input-number v-model="mockSettings.maxResponseDelay" :min="1000" :max="10000" step="500" />
                <span style="margin-left: 10px; color: #909399;">{{ $t('settings.maxResponseDelayUnit') }}</span>
              </el-form-item>
              <el-form-item :label="$t('settings.enableRequestLog')">
                <el-switch v-model="mockSettings.enableRequestLog" />
              </el-form-item>
              <el-form-item :label="$t('settings.logRetentionDays')">
                <el-input-number v-model="mockSettings.logRetentionDays" :min="1" :max="90" />
                <span style="margin-left: 10px; color: #909399;">{{ $t('settings.logRetentionUnit') }}</span>
              </el-form-item>
              <el-form-item :label="$t('settings.maxRequestBodySize')">
                <el-input-number v-model="mockSettings.maxRequestBodySize" :min="1" :max="100" />
                <span style="margin-left: 10px; color: #909399;">{{ $t('settings.maxRequestBodySizeUnit') }}</span>
              </el-form-item>
              <el-form-item :label="$t('settings.axiosTimeout')">
                <el-input-number v-model="mockSettings.axiosTimeout" :min="5000" :max="120000" step="1000" />
                <span style="margin-left: 10px; color: #909399;">{{ $t('settings.axiosTimeoutUnit') }}</span>
              </el-form-item>
              <el-form-item :label="$t('settings.customResponseCacheSeconds')">
                <el-input-number v-model="mockSettings.customResponseCacheSeconds" :min="0" :max="86400" step="60" />
                <span style="margin-left: 10px; color: #909399;">{{ $t('settings.customResponseCacheUnit') }}</span>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="saveMockSettings" :loading="saving">{{ $t('settings.saveSettings') }}</el-button>
                <el-button @click="resetMockSettings">{{ $t('settings.resetSettings') }}</el-button>
              </el-form-item>
            </el-form>
          </div>

          <!-- 系统信息 -->
          <div v-if="activeMenu === 'system'">
            <h2>{{ $t('settings.system') }}</h2>
            <el-divider />
            <el-alert v-if="systemInfoLoading" :title="$t('settings.loading')" type="info" :closable="false" show-icon />
            <el-descriptions :column="2" border v-loading="systemInfoLoading">
              <el-descriptions-item :label="$t('settings.appName')">{{ systemInfo.appName }}</el-descriptions-item>
              <el-descriptions-item :label="$t('settings.systemVersion')">{{ systemInfo.version }}</el-descriptions-item>
              <el-descriptions-item :label="$t('settings.springBootVersion')">{{ systemInfo.springBootVersion }}</el-descriptions-item>
              <el-descriptions-item :label="$t('settings.environment')">{{ systemInfo.environment }}</el-descriptions-item>
              <el-descriptions-item :label="$t('settings.buildTime')">{{ systemInfo.startTime }}</el-descriptions-item>
              <el-descriptions-item :label="$t('settings.uptime')">{{ systemInfo.uptime }}</el-descriptions-item>
              <el-descriptions-item :label="$t('settings.javaVersion')">{{ systemInfo.javaVersion }}</el-descriptions-item>
              <el-descriptions-item :label="$t('settings.javaVendor')">{{ systemInfo.javaVendor }}</el-descriptions-item>
              <el-descriptions-item :label="$t('settings.databaseType')">{{ systemInfo.databaseType }}</el-descriptions-item>
              <el-descriptions-item :label="$t('settings.databaseVersion')">{{ systemInfo.databaseVersion }}</el-descriptions-item>
              <el-descriptions-item :label="$t('settings.osName')">{{ systemInfo.osName }} {{ systemInfo.osVersion }}</el-descriptions-item>
              <el-descriptions-item :label="$t('settings.osArch')">{{ systemInfo.osArch }}</el-descriptions-item>
              <el-descriptions-item :label="$t('settings.availableProcessors')">{{ systemInfo.availableProcessors }} {{ $t('settings.cores') }}</el-descriptions-item>
              <el-descriptions-item :label="$t('settings.userDir')">{{ systemInfo.userDir }}</el-descriptions-item>
            </el-descriptions>

            <div style="margin-top: 20px; display: flex; align-items: center; gap: 12px;">
              <h3 style="margin: 0; font-size: 18px; font-weight: 600;">{{ $t('settings.performanceMonitoring') }}</h3>
              <el-tag size="small" type="success" effect="plain">
                {{ $t('settings.autoRefresh5s') }}
              </el-tag>
            </div>
            <el-divider />
            <el-row :gutter="20">
              <el-col :span="8">
                <el-card shadow="hover">
                  <el-statistic :title="$t('settings.cpuUsage')" :value="systemInfo.cpuUsage >= 0 ? systemInfo.cpuUsage : 'N/A'" :suffix="systemInfo.cpuUsage >= 0 ? '%' : ''">
                    <template #prefix>
                      <span :style="{ color: cpuUsageColor }">●</span>
                    </template>
                  </el-statistic>
                </el-card>
              </el-col>
              <el-col :span="8">
                <el-card shadow="hover">
                  <el-statistic :title="$t('settings.memoryUsage')" :value="systemInfo.memoryUsage" suffix="%">
                    <template #prefix>
                      <span :style="{ color: memoryUsageColor }">●</span>
                    </template>
                  </el-statistic>
                </el-card>
              </el-col>
              <el-col :span="8">
                <el-card shadow="hover">
                  <el-statistic :title="$t('settings.diskUsage')" :value="systemInfo.diskUsage" suffix="%">
                    <template #prefix>
                      <span :style="{ color: diskUsageColor }">●</span>
                    </template>
                  </el-statistic>
                </el-card>
              </el-col>
            </el-row>

            <div style="margin-top: 20px; display: flex; align-items: center; gap: 12px;">
              <h3 style="margin: 0; font-size: 18px; font-weight: 600;">{{ $t('settings.jvmMemoryDetail') }}</h3>
              <el-tag size="small" type="success" effect="plain">
                {{ $t('settings.autoRefresh5s') }}
              </el-tag>
            </div>
            <el-divider />
            <el-descriptions :column="3" border>
              <el-descriptions-item :label="$t('settings.heapUsed')">{{ systemInfo.heapUsedMB }} MB</el-descriptions-item>
              <el-descriptions-item :label="$t('settings.heapMax')">{{ systemInfo.heapMaxMB }} MB</el-descriptions-item>
              <el-descriptions-item :label="$t('settings.usage')">{{ systemInfo.memoryUsage }}%</el-descriptions-item>
              <el-descriptions-item :label="$t('settings.diskTotal')">{{ systemInfo.diskTotalGB }} GB</el-descriptions-item>
              <el-descriptions-item :label="$t('settings.diskFree')">{{ systemInfo.diskFreeGB }} GB</el-descriptions-item>
              <el-descriptions-item :label="$t('settings.diskUsage')">{{ systemInfo.diskUsage }}%</el-descriptions-item>
            </el-descriptions>

            <div style="margin-top: 15px; display: flex; justify-content: flex-end; align-items: center; gap: 12px;">
              <span style="color: #909399; font-size: 13px;">
                <el-icon style="vertical-align: -2px;"><Timer /></el-icon>
                {{ $t('settings.autoRefreshing') }}
              </span>
              <el-button @click="refreshSystemInfo" :loading="systemInfoLoading" type="primary">
                {{ $t('settings.manualRefresh') }}
              </el-button>
            </div>

            <h3 style="margin-top: 30px;">{{ $t('settings.environmentVars') }}</h3>
            <el-divider />
            <el-table :data="envVars" border style="width: 100%" v-loading="systemInfoLoading" :empty-text="$t('settings.noEnvVars')">
              <el-table-column prop="key" :label="$t('settings.variableName')" width="220" />
              <el-table-column prop="value" :label="$t('settings.variableValue')" />
            </el-table>
          </div>

          <!-- 系统公告 -->
          <div v-if="activeMenu === 'announcement'">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
              <h2 style="margin: 0;">{{ $t('settings.announcement') }}</h2>
              <el-button type="primary" @click="openAnnouncementDialog()">
                <Edit :width="'1em'" :height="'1em'" style="margin-right: 5px;" />
                {{ $t('settings.createAnnouncement') }}
              </el-button>
            </div>
            <el-divider />
            <el-table :data="announcements" border style="width: 100%">
              <el-table-column prop="title" :label="$t('settings.announcementTitle')" width="200" />
              <el-table-column prop="content" :label="$t('settings.announcementContent')" show-overflow-tooltip />
              <el-table-column prop="enabled" :label="$t('settings.status')" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.enabled ? 'success' : 'info'">
                    {{ row.enabled ? $t('settings.enabledStatus') : $t('settings.disabledStatus') }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="priority" :label="$t('settings.priority')" width="100">
                <template #default="{ row }">
                  <el-tag :type="getPriorityType(row.priority)">{{ row.priority }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createBy" :label="$t('settings.createdBy')" width="120" />
              <el-table-column prop="createTime" :label="$t('settings.createTime')" width="180" sortable>
                <template #default="{ row }">
                  {{ formatTime(row.createTime) }}
                </template>
              </el-table-column>
              <el-table-column :label="$t('common.edit')" width="200" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openAnnouncementDialog(row)">
                    <Edit :width="'1em'" :height="'1em'" />
                    {{ $t('common.edit') }}
                  </el-button>
                  <el-button link type="primary" @click="toggleAnnouncementStatus(row)">
                    {{ row.enabled ? $t('settings.disable') : $t('settings.enable') }}
                  </el-button>
                  <el-button link type="danger" @click="deleteAnnouncement(row.id)">
                    <Delete :width="'1em'" :height="'1em'" />
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <!-- 分页 -->
            <div class="pagination-wrapper">
              <el-pagination
                v-model:current-page="pagination.page"
                v-model:page-size="pagination.size"
                :page-sizes="[10, 15, 20, 50, 100]"
                :total="pagination.total"
                layout="total, sizes, prev, pager, next, jumper"
                @size-change="handleSizeChange"
                @current-change="handlePageChange"
              />
            </div>
          </div>

          <!-- 页脚设置 -->
          <div v-if="activeMenu === 'footer'">
            <h2>{{ $t('settings.footer') }}</h2>
            <el-divider />
            <el-form :model="footerSettings" label-width="160px">
              <!-- 版权信息 -->
              <el-divider content-position="left">
                <el-switch v-model="footerSettings.enableCopyright" size="small" style="margin-right: 8px;" />
                {{ $t('settings.footerCopyright') }}
              </el-divider>
              <el-form-item v-if="footerSettings.enableCopyright" :label="$t('settings.footerCopyright')">
                <el-input v-model="footerSettings.copyright" :placeholder="$t('settings.footerCopyrightPlaceholder')" />
              </el-form-item>
              <!-- 友情链接 -->
              <el-divider content-position="left">
                <el-switch v-model="footerSettings.enableFriendLink" size="small" style="margin-right: 8px;" />
                {{ $t('settings.footerFriendLink') }}
              </el-divider>
              <template v-if="footerSettings.enableFriendLink">
                <el-form-item :label="$t('settings.footerLinkUrl')">
                  <el-input v-model="footerSettings.friendLinkUrl" :placeholder="$t('settings.footerLinkUrlPlaceholder')" />
                </el-form-item>
                <el-form-item :label="$t('settings.footerLinkTitle')">
                  <el-input v-model="footerSettings.friendLinkTitle" :placeholder="$t('settings.footerLinkTitlePlaceholder')" />
                </el-form-item>
              </template>
              <!-- 博客链接 -->
              <el-divider content-position="left">
                <el-switch v-model="footerSettings.enableBlog" size="small" style="margin-right: 8px;" />
                {{ $t('settings.footerBlogLink') }}
              </el-divider>
              <template v-if="footerSettings.enableBlog">
                <el-form-item :label="$t('settings.footerLinkUrl')">
                  <el-input v-model="footerSettings.blogUrl" :placeholder="$t('settings.footerLinkUrlPlaceholder')" />
                </el-form-item>
                <el-form-item :label="$t('settings.footerLinkTitle')">
                  <el-input v-model="footerSettings.blogTitle" :placeholder="$t('settings.footerLinkTitlePlaceholder')" />
                </el-form-item>
              </template>
              <!-- GitHub链接 -->
              <el-divider content-position="left">
                <el-switch v-model="footerSettings.enableGithub" size="small" style="margin-right: 8px;" />
                {{ $t('settings.footerGithubLink') }}
              </el-divider>
              <template v-if="footerSettings.enableGithub">
                <el-form-item :label="$t('settings.footerLinkUrl')">
                  <el-input v-model="footerSettings.githubUrl" :placeholder="$t('settings.footerLinkUrlPlaceholder')" />
                </el-form-item>
                <el-form-item :label="$t('settings.footerLinkTitle')">
                  <el-input v-model="footerSettings.githubTitle" :placeholder="$t('settings.footerLinkTitlePlaceholder')" />
                </el-form-item>
              </template>
              <!-- 邮箱链接 -->
              <el-divider content-position="left">
                <el-switch v-model="footerSettings.enableEmail" size="small" style="margin-right: 8px;" />
                {{ $t('settings.footerEmailLink') }}
              </el-divider>
              <template v-if="footerSettings.enableEmail">
                <el-form-item :label="$t('settings.footerEmailAddress')">
                  <el-input v-model="footerSettings.emailAddress" :placeholder="$t('settings.footerEmailPlaceholder')" />
                </el-form-item>
                <el-form-item :label="$t('settings.footerLinkTitle')">
                  <el-input v-model="footerSettings.emailTitle" :placeholder="$t('settings.footerLinkTitlePlaceholder')" />
                </el-form-item>
              </template>
              <!-- 自定义链接 -->
              <el-divider content-position="left">
                <el-switch v-model="footerSettings.enableCustomLinks" size="small" style="margin-right: 8px;" />
                {{ $t('settings.footerCustomLinks') }}
              </el-divider>
              <template v-if="footerSettings.enableCustomLinks">
                <div v-for="(link, idx) in footerSettings.customLinks" :key="idx" class="custom-link-item">
                  <el-row :gutter="12">
                    <el-col :span="10">
                      <el-input v-model="link.url" :placeholder="$t('settings.footerLinkUrlPlaceholder')" size="small" />
                    </el-col>
                    <el-col :span="10">
                      <el-input v-model="link.title" :placeholder="$t('settings.footerLinkTitlePlaceholder')" size="small" />
                    </el-col>
                    <el-col :span="4">
                      <el-button type="danger" size="small" @click="removeCustomLink(idx)" :icon="Delete">
                        {{ $t('common.delete') }}
                      </el-button>
                    </el-col>
                  </el-row>
                  <el-row :gutter="12" style="margin-top: 8px;">
                    <el-col :span="20">
                      <el-input v-model="link.svgIcon" type="textarea" :rows="3" size="small"
                        placeholder="SVG 图标代码（可选，粘贴完整的 SVG 标签）" />
                    </el-col>
                    <el-col :span="4" style="display: flex; align-items: flex-start; padding-top: 4px;">
                      <div v-if="link.svgIcon" class="svg-preview" v-html="link.svgIcon"></div>
                    </el-col>
                  </el-row>
                </div>
                <el-form-item>
                  <el-button type="success" @click="addCustomLink" :icon="Edit">
                    {{ $t('settings.footerAddCustomLink') }}
                  </el-button>
                </el-form-item>
              </template>
              <el-form-item>
                <el-button type="primary" @click="saveFooterSettings" :loading="saving">{{ $t('settings.saveSettings') }}</el-button>
                <el-button @click="resetFooterSettings">{{ $t('settings.resetSettings') }}</el-button>
              </el-form-item>
            </el-form>
          </div>
        <!-- 注册设置 -->
          <div v-if="activeMenu === 'registration'">
            <h2>{{ $t('settings.registration') }}</h2>
            <el-divider />
            <el-form :model="registrationSettings" label-width="200px">
              <el-form-item :label="$t('settings.enableRegistration')">
                <el-switch v-model="registrationSettings.enableRegistration" />
              </el-form-item>
              <el-form-item :label="$t('settings.allowedEmailDomains')">
                <el-input
                  v-model="registrationSettings.allowedEmailDomains"
                  type="textarea"
                  :rows="3"
                  :placeholder="$t('settings.allowedEmailDomainsPlaceholder')"
                />
              </el-form-item>

              <!-- 邮箱验证设置 -->
              <el-divider content-position="left">
                <span>{{ $t('settings.emailVerification') }}</span>
              </el-divider>
              <el-form-item>
                <template #label>
                  <span>{{ $t('settings.enableEmailVerification') }}</span>
                  <el-tooltip :content="$t('settings.enableEmailVerificationTip')" placement="top">
                    <el-icon class="tip-icon"><InfoFilled /></el-icon>
                  </el-tooltip>
                </template>
                <el-switch v-model="registrationSettings.enableEmailVerification" @change="handleEmailVerificationToggle" />
              </el-form-item>

              <template v-if="registrationSettings.enableEmailVerification">
                <!-- SMTP 配置 -->
                <el-divider content-position="left">
                  <el-tag size="small" type="info">SMTP</el-tag>
                </el-divider>
                <el-form-item :label="$t('settings.smtpHost')" prop="smtpHost">
                  <el-input v-model="emailConfig.smtpHost" :placeholder="$t('settings.smtpHostPlaceholder')" />
                </el-form-item>
                <el-form-item :label="$t('settings.smtpPort')">
                  <el-input-number v-model="emailConfig.smtpPort" :min="1" :max="65535" />
                </el-form-item>
                <el-form-item :label="$t('settings.useSsl')">
                  <el-switch v-model="emailConfig.useSsl" />
                </el-form-item>
                <el-form-item :label="$t('settings.smtpUsername')" prop="username">
                  <el-input v-model="emailConfig.username" :placeholder="$t('settings.smtpUsernamePlaceholder')" />
                </el-form-item>
                <el-form-item :label="$t('settings.displayName')">
                  <el-input v-model="emailConfig.displayName" :placeholder="$t('settings.displayNamePlaceholder')" />
                </el-form-item>
                <el-form-item :label="$t('settings.smtpPassword')" prop="password">
                  <el-input v-model="emailConfig.password" type="password" show-password :placeholder="$t('settings.smtpPasswordPlaceholder')" />
                </el-form-item>

                <!-- 测试邮件发送 -->
                <el-divider content-position="left">
                  <el-tag size="small" type="success">{{ $t('settings.testEmail') }}</el-tag>
                </el-divider>
                <el-form-item :label="$t('settings.testEmailAddress')">
                  <div style="display: flex; align-items: center; gap: 12px;">
                    <el-input v-model="testEmailAddress" :placeholder="$t('settings.testEmailPlaceholder')" style="width: 300px;" />
                    <el-button type="primary" @click="sendTestEmail" :loading="testEmailLoading">
                      {{ testEmailLoading ? $t('settings.testEmailSending') : $t('settings.testEmail') }}
                    </el-button>
                  </div>
                </el-form-item>

                <!-- 验证码邮件模板选择 -->
                <el-divider content-position="left">
                  <el-tag size="small" type="warning">{{ $t('settings.verificationEmailTemplate') }}</el-tag>
                </el-divider>
                <el-form-item :label="$t('settings.selectVerificationTemplate')">
                  <el-select
                    v-model="emailConfig.verificationTemplateId"
                    :placeholder="$t('settings.selectTemplatePlaceholder')"
                    style="width: 100%;"
                    clearable
                    filterable
                    remote
                    :remote-method="searchEmailTemplates"
                    :loading="emailTemplateLoading"
                  >
                    <el-option
                      v-for="tpl in emailTemplates"
                      :key="tpl.id"
                      :label="tpl.name + ' (' + (tpl.type === 'REGISTER' ? $t('emailTemplate.typeRegister') : tpl.type === 'RESET_PASSWORD' ? $t('emailTemplate.typeResetPassword') : tpl.type === 'PASSWORD_CHANGED' ? $t('emailTemplate.typePasswordChanged') : tpl.type) + ')'"
                      :value="tpl.id"
                    />
                  </el-select>
                  <div style="margin-top: 6px; color: #909399; font-size: 12px;">
                    {{ $t('settings.verificationTemplateHint') }}
                  </div>
                </el-form-item>

                <!-- 邮件模板管理链接 -->
                <el-divider />
                <el-form-item>
                  <el-button type="success" @click="goToEmailTemplates">
                    <Edit :width="'1em'" :height="'1em'" style="margin-right: 6px;" />
                    {{ $t('settings.emailTemplatesLink') }}
                  </el-button>
                </el-form-item>
              </template>

              <el-form-item>
                <el-button type="primary" @click="saveRegistrationSettings" :loading="saving">{{ $t('settings.saveSettings') }}</el-button>
                <el-button @click="resetRegistrationSettings">{{ $t('settings.resetSettings') }}</el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 公告编辑对话框 -->
    <el-dialog v-model="announcementDialogVisible" :title="dialogTitle" width="60%">
      <el-form :model="announcementForm" label-width="100px">
        <el-form-item :label="$t('settings.announcementTitle')">
          <el-input v-model="announcementForm.title" :placeholder="$t('settings.announcementPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('settings.priority')">
          <el-select v-model="announcementForm.priority" style="width: 100%">
            <el-option :label="$t('settings.low')" value="LOW" />
            <el-option :label="$t('settings.normal')" value="NORMAL" />
            <el-option :label="$t('settings.high')" value="HIGH" />
            <el-option :label="$t('settings.urgent')" value="URGENT" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('settings.enabled')">
          <el-switch v-model="announcementForm.enabled" />
        </el-form-item>
        <el-form-item :label="$t('settings.announcementContent')">
          <el-input
            v-model="announcementForm.content"
            type="textarea"
            :rows="15"
            :placeholder="$t('settings.announcementPlaceholder2')"
          />
          <div style="margin-top: 10px; color: #909399; font-size: 12px;">
            {{ $t('settings.markdownHint') }}
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="announcementDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveAnnouncement" :loading="saving">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, watch, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Setting, Lock, Key, Connection, InfoFilled, Bell, Edit, Delete, Refresh, Timer, UserFilled } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'
import { setDateFormat } from '@/utils/dateFormat'
import axios from 'axios'

const { t, locale } = useI18n()
const router = useRouter()
const userStore = useUserStore()

// 系统设置各子模块权限
const canBasic = computed(() => userStore.hasPermission('settings:basic'))
const canSecurity = computed(() => userStore.hasPermission('settings:security'))
const canJwt = computed(() => userStore.hasPermission('settings:jwt'))
const canMock = computed(() => userStore.hasPermission('settings:mock'))
const canAnnouncement = computed(() => userStore.hasPermission('settings:announcement'))
const canSystem = computed(() => userStore.hasPermission('settings:system') || userStore.hasPermission('settings:view'))
const canFooter = computed(() => userStore.hasPermission('settings:footer'))
const canRegistration = computed(() => userStore.hasPermission('settings:registration'))

// 监听语言变化，保存到 localStorage
// 注意：不在此处调用后端 API，避免与 saveBasicSettings 重复请求
watch(locale, (newLocale) => {
  localStorage.setItem('locale', newLocale)
  console.log('Language changed to:', newLocale)
})

// 当前激活的菜单
const activeMenu = ref('basic')

// 保存加载状态
const saving = ref(false)

// 基础设置
const basicSettings = reactive({
  appName: 'API Server',
  version: 'v-',
  language: localStorage.getItem('locale') || 'zh-CN',
  dateFormat: 'YYYY-MM-DD'
})

// 保存语言到服务器
const saveLanguageToServer = async (language) => {
  try {
    await request.post('/system-config/language', { language })
    console.log('Language saved to server:', language)
  } catch (error) {
    console.error('Failed to save language to server:', error)
    throw error
  }
}

// 保存日期格式到服务器
const saveDateFormatToServer = async (dateFormat) => {
  try {
    await request.post('/system-config/date-format', { dateFormat })
    console.log('Date format saved to server:', dateFormat)
  } catch (error) {
    console.error('Failed to save date format to server:', error)
    throw error
  }
}

// 加载基础配置（从服务器获取语言、版本号等）
const loadBasicConfig = async () => {
  console.log('[DEBUG] loadBasicConfig called')
  try {
    const response = await request.get('/system-config')
    console.log('[DEBUG] /system-config response:', response)
    if (response.code === 200 && response.data) {
      const data = response.data
      if (data.defaultLanguage) {
        basicSettings.language = data.defaultLanguage
      }
    }
  } catch (error) {
    console.error('加载基础配置失败:', error)
  }

  // 从后端获取版本号
  try {
    const verResponse = await request.get('/system/version')
    if (verResponse.code === 200 && verResponse.data) {
      basicSettings.version = 'v' + (verResponse.data.version || '-')
      basicSettings.appName = verResponse.data.appName || 'API Server'
    }
  } catch (error) {
    console.error('获取版本号失败:', error)
  }
}

// 安全配置
const securitySettings = reactive({
  requireUppercase: true,
  requireLowercase: true,
  requireDigit: true,
  requireSpecial: true,
  minPasswordLength: 8,
  maxLoginAttempts: 5,
  lockoutDuration: 15,
  enableIpWhitelist: false,
  ipWhitelist: '',
  // iframe 嵌入控制：disable 禁止 / whitelist 仅放行指定来源 / allowAll 全部放行
  iframeMode: 'disable',
  iframeAllowedOrigins: ''
})

// JWT配置
const jwtSettings = reactive({
  tokenExpiration: 1800, // 30分钟
  refreshTokenExpiration: 604800, // 7天
  issuer: 'api-server',
  audience: 'api-server-users'
})

// Mock配置
const mockSettings = reactive({
  defaultResponseDelay: 0,
  maxResponseDelay: 5000,
  enableRequestLog: true,
  logRetentionDays: 30,
  enableRandomResponse: false,
  maxRequestBodySize: 10,
  axiosTimeout: 30000,
  customResponseCacheSeconds: 600
})

// 页脚设置
const footerSettings = reactive({
  enableCopyright: true,
  copyright: '',
  enableFriendLink: true,
  friendLinkUrl: '',
  friendLinkTitle: '',
  enableBlog: true,
  blogUrl: '',
  blogTitle: '',
  enableGithub: true,
  githubUrl: '',
  githubTitle: '',
  enableEmail: true,
  emailAddress: '',
  emailTitle: '',
  enableCustomLinks: true,
  customLinks: []
})

// 注册设置
const registrationSettings = reactive({
  enableRegistration: false,
  allowedEmailDomains: '',
  enableEmailVerification: false
})

// 邮箱配置
const emailConfig = reactive({
  smtpHost: '',
  smtpPort: 465,
  useSsl: true,
  fromAddress: '',
  username: '',
  password: '',
  displayName: '',
  enabled: false,
  verificationTemplateId: null
})

// 邮件模板列表
const emailTemplates = ref([])
const emailTemplateLoading = ref(false)

// 测试邮件
const testEmailAddress = ref('')
const testEmailLoading = ref(false)

// 系统信息
const systemInfo = reactive({
  version: '-',
  buildTime: '-',
  environment: '-',
  uptime: '-',
  javaVersion: '-',
  springBootVersion: '-',
  databaseType: '-',
  databaseVersion: '-',
  osName: '-',
  osArch: '-',
  cpuUsage: -1,
  memoryUsage: 0,
  diskUsage: 0,
  startTime: '-',
  heapMaxMB: 0,
  heapUsedMB: 0,
  availableProcessors: 0
})

// 统一的使用率颜色：灰色(不可用) -> 绿色(正常) -> 橙色(>50%) -> 红色(>80%)
const cpuUsageColor = computed(() => {
  const v = systemInfo.cpuUsage
  if (v < 0) return '#909399'
  if (v > 80) return '#f56c6c'
  if (v > 50) return '#e6a23c'
  return '#67c23a'
})
const memoryUsageColor = computed(() => {
  const v = systemInfo.memoryUsage
  if (v < 0) return '#909399'
  if (v > 80) return '#f56c6c'
  if (v > 50) return '#e6a23c'
  return '#67c23a'
})
const diskUsageColor = computed(() => {
  const v = systemInfo.diskUsage
  if (v < 0) return '#909399'
  if (v > 80) return '#f56c6c'
  if (v > 50) return '#e6a23c'
  return '#67c23a'
})

// 环境变量
const envVars = ref([])

// 公告数据
const announcements = ref([])
const announcementDialogVisible = ref(false)
const announcementForm = reactive({
  id: null,
  title: '',
  content: '',
  enabled: true,
  priority: 'NORMAL'
})
const dialogTitle = ref('创建公告')

// 分页参数
const pagination = reactive({
  page: 0,
  size: 15,
  total: 0,
  totalPages: 0,
  sortBy: 'createTime',
  sortOrder: 'desc'
})

// 菜单切换
const handleMenuSelect = (index) => {
  console.log('[DEBUG] handleMenuSelect:', index)
  // 离开 system tab 时停止自动刷新
  if (activeMenu.value === 'system' && index !== 'system') {
    stopAutoRefresh()
  }
  activeMenu.value = index
  if (index === 'basic') {
    loadBasicConfig()
  } else if (index === 'security') {
    loadSecuritySettings()
  } else if (index === 'system') {
    fetchSystemInfo()
    startAutoRefresh()
  } else if (index === 'announcement') {
    fetchAnnouncements()
  } else if (index === 'footer') {
    loadFooterConfig()
  } else if (index === 'registration') {
    loadRegistrationConfig()
  }
}

// 保存基础设置
const saveBasicSettings = async () => {
  saving.value = true
  try {
    // 先切换语言（即时生效，不依赖后端响应）
    if (locale.value !== basicSettings.language) {
      locale.value = basicSettings.language
    }

    // 保存语言设置到服务器
    await saveLanguageToServer(basicSettings.language)
    // 保存日期格式到服务器，并同步缓存
    await saveDateFormatToServer(basicSettings.dateFormat)
    setDateFormat(basicSettings.dateFormat)

    ElMessage.success(t('settings.settingsSaved'))
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error(t('common.error'))
  } finally {
    saving.value = false
  }
}

// 重置基础设置
const resetBasicSettings = () => {
  basicSettings.language = 'zh-CN'
  basicSettings.dateFormat = 'YYYY-MM-DD'
  ElMessage.info('已重置为默认值')
}

// 加载安全配置（iframe 嵌入白名单等）
const loadSecuritySettings = async () => {
  try {
    const response = await request.get('/system-config/iframe')
    if (response.code === 200 && response.data) {
      const origins = (response.data.iframeAllowedOrigins || '').trim()
      if (origins === '') {
        securitySettings.iframeMode = 'disable'
        securitySettings.iframeAllowedOrigins = ''
      } else if (origins === '*') {
        securitySettings.iframeMode = 'allowAll'
        securitySettings.iframeAllowedOrigins = ''
      } else {
        securitySettings.iframeMode = 'whitelist'
        securitySettings.iframeAllowedOrigins = origins
      }
    }
  } catch (error) {
    console.error('加载 iframe 白名单配置失败:', error)
  }
}

// 保存安全配置
const saveSecuritySettings = async () => {
  saving.value = true
  try {
    // 保存 iframe 嵌入白名单（保存后即时生效，无需重启）
    const iframeValue = securitySettings.iframeMode === 'allowAll'
      ? '*'
      : (securitySettings.iframeMode === 'whitelist'
          ? (securitySettings.iframeAllowedOrigins || '').trim()
          : '')
    await request.post('/system-config/iframe', { iframeAllowedOrigins: iframeValue })
    // TODO: 调用API保存其他安全设置（密码强度、登录锁定等）
    await new Promise(resolve => setTimeout(resolve, 500))
    ElMessage.success(t('settings.settingsSaved'))
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error(t('common.error'))
  } finally {
    saving.value = false
  }
}

// 重置安全配置
const resetSecuritySettings = () => {
  securitySettings.requireUppercase = true
  securitySettings.requireLowercase = true
  securitySettings.requireDigit = true
  securitySettings.requireSpecial = true
  securitySettings.minPasswordLength = 8
  securitySettings.maxLoginAttempts = 5
  securitySettings.lockoutDuration = 15
  securitySettings.enableIpWhitelist = false
  securitySettings.ipWhitelist = ''
  securitySettings.iframeMode = 'disable'
  securitySettings.iframeAllowedOrigins = ''
  ElMessage.info(t('settings.settingsReset'))
}

// 保存JWT配置
const saveJwtSettings = async () => {
  saving.value = true
  try {
    // TODO: 调用API保存设置
    await new Promise(resolve => setTimeout(resolve, 500))
    ElMessage.success(t('settings.settingsSaved'))
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error(t('common.error'))
  } finally {
    saving.value = false
  }
}

// 重置JWT配置
const resetJwtSettings = () => {
  jwtSettings.tokenExpiration = 1800
  jwtSettings.refreshTokenExpiration = 604800
  jwtSettings.issuer = 'api-server'
  jwtSettings.audience = 'api-server-users'
  ElMessage.info(t('settings.settingsReset'))
}

// 保存Mock配置
const saveMockSettings = async () => {
  saving.value = true
  try {
    await request.post('/system-config/mock', {
      defaultResponseDelay: mockSettings.defaultResponseDelay,
      maxResponseDelay: mockSettings.maxResponseDelay,
      enableRequestLog: mockSettings.enableRequestLog,
      logRetentionDays: mockSettings.logRetentionDays,
      maxRequestBodySize: mockSettings.maxRequestBodySize,
      axiosTimeout: mockSettings.axiosTimeout,
      customResponseCacheSeconds: mockSettings.customResponseCacheSeconds
    })
    // 同步更新 axios 全局超时时间
    updateAxiosTimeout(mockSettings.axiosTimeout)
    ElMessage.success(t('settings.settingsSaved'))
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error(t('common.error'))
  } finally {
    saving.value = false
  }
}

// 重置Mock配置
const resetMockSettings = () => {
  mockSettings.defaultResponseDelay = 0
  mockSettings.maxResponseDelay = 5000
  mockSettings.enableRequestLog = true
  mockSettings.logRetentionDays = 30
  mockSettings.maxRequestBodySize = 10
  mockSettings.axiosTimeout = 30000
  mockSettings.customResponseCacheSeconds = 600
  ElMessage.info(t('settings.settingsReset'))
}

// 系统信息加载状态
const systemInfoLoading = ref(false)

// 获取系统信息（默认静默，不显示提示）
// silent: 内部用，外部调用时不传参即为静默模式
const fetchSystemInfo = async () => {
  try {
    const response = await request.get('/system/info')
    if (response.code === 200) {
      const data = response.data
      // 更新系统信息
      Object.assign(systemInfo, {
        version: data.appVersion || '-',
        appName: data.appName || '-',
        buildTime: data.buildTime || '-',
        environment: data.environment || '-',
        uptime: data.uptime || '-',
        startTime: data.startTime || '-',
        javaVersion: data.javaVersion || '-',
        javaVendor: data.javaVendor || '-',
        springBootVersion: data.springBootVersion || '-',
        databaseType: data.databaseType || '-',
        databaseVersion: data.databaseVersion || '-',
        osName: data.osName || '-',
        osVersion: data.osVersion || '-',
        osArch: data.osArch || '-',
        cpuUsage: data.cpuUsage != null ? data.cpuUsage : -1,
        memoryUsage: data.memoryUsage || 0,
        diskUsage: data.diskUsage || 0,
        heapMaxMB: data.heapMaxMB || 0,
        heapUsedMB: data.heapUsedMB || 0,
        availableProcessors: data.availableProcessors || 0,
        userDir: data.userDir || '-',
        diskTotalGB: data.diskTotalGB || 0,
        diskFreeGB: data.diskFreeGB || 0
      })
    }
  } catch (error) {
    console.error('获取系统信息失败:', error)
  }
}

// 手动刷新系统信息（显示 loading 和提示）
const refreshSystemInfo = async () => {
  systemInfoLoading.value = true
  try {
    const response = await request.get('/system/info')
    if (response.code === 200) {
      const data = response.data
      Object.assign(systemInfo, {
        version: data.appVersion || '-',
        appName: data.appName || '-',
        buildTime: data.buildTime || '-',
        environment: data.environment || '-',
        uptime: data.uptime || '-',
        startTime: data.startTime || '-',
        javaVersion: data.javaVersion || '-',
        javaVendor: data.javaVendor || '-',
        springBootVersion: data.springBootVersion || '-',
        databaseType: data.databaseType || '-',
        databaseVersion: data.databaseVersion || '-',
        osName: data.osName || '-',
        osVersion: data.osVersion || '-',
        osArch: data.osArch || '-',
        cpuUsage: data.cpuUsage != null ? data.cpuUsage : -1,
        memoryUsage: data.memoryUsage || 0,
        diskUsage: data.diskUsage || 0,
        heapMaxMB: data.heapMaxMB || 0,
        heapUsedMB: data.heapUsedMB || 0,
        availableProcessors: data.availableProcessors || 0,
        userDir: data.userDir || '-',
        diskTotalGB: data.diskTotalGB || 0,
        diskFreeGB: data.diskFreeGB || 0
      })
      // 更新环境变量
      const envMap = data.envVars || {}
      const envArray = Object.entries(envMap).map(([key, value]) => ({
        key,
        value
      }))
      envVars.value = envArray

      ElMessage.success('系统信息已刷新')
    } else {
      ElMessage.error(response.message || '获取系统信息失败')
    }
  } catch (error) {
    console.error('获取系统信息失败:', error)
    ElMessage.error('获取系统信息失败，请检查网络连接')
  } finally {
    systemInfoLoading.value = false
  }
}

// 自动刷新定时器
let autoRefreshTimer = null
const AUTO_REFRESH_INTERVAL = 5000 // 5秒

// 启动自动刷新
const startAutoRefresh = () => {
  stopAutoRefresh()
  autoRefreshTimer = setInterval(() => {
    fetchSystemInfo() // 静默刷新
  }, AUTO_REFRESH_INTERVAL)
}

// 停止自动刷新
const stopAutoRefresh = () => {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
}

// 获取公告列表
const fetchAnnouncements = async () => {
  try {
    const response = await request.get('/system-announcement', {
      params: {
        page: pagination.page,
        size: pagination.size,
        sortBy: pagination.sortBy,
        sortOrder: pagination.sortOrder
      }
    })
    if (response.code === 200) {
      announcements.value = response.data.content || []
      pagination.total = response.data.totalElements || 0
      pagination.totalPages = response.data.totalPages || 0
    }
  } catch (error) {
    console.error('获取公告列表失败:', error)
  }
}

// 处理页码变化
const handlePageChange = (page) => {
  pagination.page = page
  fetchAnnouncements()
}

// 处理每页大小变化
const handleSizeChange = (size) => {
  pagination.size = size
  pagination.page = 0
  fetchAnnouncements()
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

// 打开公告对话框
const openAnnouncementDialog = (row = null) => {
  if (row) {
    dialogTitle.value = '编辑公告'
    announcementForm.id = row.id
    announcementForm.title = row.title
    announcementForm.content = row.content
    announcementForm.enabled = row.enabled
    announcementForm.priority = row.priority
  } else {
    dialogTitle.value = '创建公告'
    announcementForm.id = null
    announcementForm.title = ''
    announcementForm.content = ''
    announcementForm.enabled = true
    announcementForm.priority = 'NORMAL'
  }
  announcementDialogVisible.value = true
}

// 保存公告
const saveAnnouncement = async () => {
  if (!announcementForm.title || !announcementForm.content) {
    ElMessage.warning('请填写完整信息')
    return
  }

  saving.value = true
  try {
    let response
    if (announcementForm.id) {
      // 更新
      response = await request.put(`/system-announcement/${announcementForm.id}`, announcementForm)
    } else {
      // 创建
      response = await request.post('/system-announcement', announcementForm)
    }

    if (response.code === 200) {
      ElMessage.success(announcementForm.id ? '更新成功' : '创建成功')
      announcementDialogVisible.value = false
      fetchAnnouncements()
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (error) {
    console.error('保存公告失败:', error)
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

// 删除公告
const deleteAnnouncement = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除此公告吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const response = await request.delete(`/system-announcement/${id}`)
    if (response.code === 200) {
      ElMessage.success('删除成功')
      fetchAnnouncements()
    } else {
      ElMessage.error(response.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除公告失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

// 切换公告状态
const toggleAnnouncementStatus = async (row) => {
  try {
    const response = await request.put(`/system-announcement/${row.id}/toggle`, { enabled: !row.enabled })
    if (response.code === 200) {
      ElMessage.success('操作成功')
      fetchAnnouncements()
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (error) {
    console.error('切换公告状态失败:', error)
    ElMessage.error('操作失败')
  }
}

// 获取优先级标签类型
const getPriorityType = (priority) => {
  const typeMap = {
    'LOW': 'info',
    'NORMAL': '',
    'HIGH': 'warning',
    'URGENT': 'danger'
  }
  return typeMap[priority] || ''
}

// 保存页脚设置
const saveFooterSettings = async () => {
  saving.value = true
  try {
    await request.post('/system-config/footer', {
      enableCopyright: footerSettings.enableCopyright,
      copyright: footerSettings.copyright,
      enableFriendLink: footerSettings.enableFriendLink,
      friendLinkUrl: footerSettings.friendLinkUrl,
      friendLinkTitle: footerSettings.friendLinkTitle,
      enableBlog: footerSettings.enableBlog,
      blogUrl: footerSettings.blogUrl,
      blogTitle: footerSettings.blogTitle,
      enableGithub: footerSettings.enableGithub,
      githubUrl: footerSettings.githubUrl,
      githubTitle: footerSettings.githubTitle,
      enableEmail: footerSettings.enableEmail,
      emailAddress: footerSettings.emailAddress,
      emailTitle: footerSettings.emailTitle,
      enableCustomLinks: footerSettings.enableCustomLinks,
      customLinks: footerSettings.customLinks
    })
    ElMessage.success(t('settings.settingsSaved'))
    // 通知 DashboardLayout 刷新页脚配置
    window.dispatchEvent(new CustomEvent('footer-config-updated'))
  } catch (error) {
    console.error('保存页脚配置失败:', error)
    ElMessage.error(t('common.error'))
  } finally {
    saving.value = false
  }
}

// 重置页脚设置
const resetFooterSettings = () => {
  footerSettings.enableCopyright = true
  footerSettings.copyright = '© 2026 carolcoral，Author: LXW'
  footerSettings.enableFriendLink = true
  footerSettings.friendLinkUrl = 'https://xindu.site'
  footerSettings.friendLinkTitle = ''
  footerSettings.enableBlog = true
  footerSettings.blogUrl = 'https://blog.xindu.site'
  footerSettings.blogTitle = ''
  footerSettings.enableGithub = true
  footerSettings.githubUrl = 'https://github.com/carolcoral'
  footerSettings.githubTitle = ''
  footerSettings.enableEmail = true
  footerSettings.emailAddress = 'lxw@cnkj.site'
  footerSettings.emailTitle = ''
  footerSettings.enableCustomLinks = true
  footerSettings.customLinks = []
  ElMessage.info(t('settings.settingsReset'))
}

// 添加自定义链接
const addCustomLink = () => {
  footerSettings.customLinks.push({ url: '', title: '', svgIcon: '' })
}

// 删除自定义链接
const removeCustomLink = (index) => {
  footerSettings.customLinks.splice(index, 1)
}

// 加载注册设置
const loadRegistrationConfig = async () => {
  try {
    const response = await request.get('/system-config')
    if (response.code === 200 && response.data) {
      const data = response.data
      if (data.enableRegistration !== undefined) registrationSettings.enableRegistration = data.enableRegistration
      if (data.allowedEmailDomains !== undefined) registrationSettings.allowedEmailDomains = data.allowedEmailDomains || ''
      if (data.enableEmailVerification !== undefined) registrationSettings.enableEmailVerification = data.enableEmailVerification

      // 如果开启了邮箱验证，加载邮箱配置
      if (data.enableEmailVerification) {
        loadEmailConfig()
        fetchEmailTemplates()
      }
    }
  } catch (error) {
    console.error('加载注册配置失败:', error)
  }
}

// 保存注册设置
const saveRegistrationSettings = async () => {
  // 开启邮箱验证时校验SMTP必填字段（手动校验，因为字段在emailConfig对象上）
  if (registrationSettings.enableEmailVerification) {
    if (!emailConfig.smtpHost || !emailConfig.smtpHost.trim()) {
      ElMessage.warning(t('settings.smtpHostRequired'))
      return
    }
    if (!emailConfig.username || !emailConfig.username.trim()) {
      ElMessage.warning(t('settings.smtpUsernameRequired'))
      return
    }
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    if (!emailRegex.test(emailConfig.username)) {
      ElMessage.warning(t('settings.fromAddressInvalid'))
      return
    }
    if (!emailConfig.password || !emailConfig.password.trim()) {
      ElMessage.warning(t('settings.smtpPasswordRequired'))
      return
    }
  }

  saving.value = true
  try {
    // 保存注册配置
    await request.post('/system-config/registration', {
      enableRegistration: registrationSettings.enableRegistration,
      allowedEmailDomains: registrationSettings.allowedEmailDomains,
      enableEmailVerification: registrationSettings.enableEmailVerification
    })

    // 如果开启了邮箱验证，保存邮箱配置
    if (registrationSettings.enableEmailVerification) {
      await request.post('/email-config', {
        smtpHost: emailConfig.smtpHost,
        smtpPort: emailConfig.smtpPort,
        useSsl: emailConfig.useSsl,
        fromAddress: emailConfig.username,
        username: emailConfig.username,
        password: emailConfig.password || undefined,
        displayName: emailConfig.displayName || undefined,
        enabled: emailConfig.enabled,
        verificationTemplateId: emailConfig.verificationTemplateId || null
      })
    }

    ElMessage.success(t('settings.settingsSaved'))
  } catch (error) {
    console.error('保存注册设置失败:', error)
    ElMessage.error(t('common.error'))
  } finally {
    saving.value = false
  }
}

// 重置注册设置
const resetRegistrationSettings = () => {
  registrationSettings.enableRegistration = false
  registrationSettings.allowedEmailDomains = ''
  registrationSettings.enableEmailVerification = false
  emailConfig.smtpHost = ''
  emailConfig.smtpPort = 465
  emailConfig.useSsl = true
  emailConfig.fromAddress = ''
  emailConfig.username = ''
  emailConfig.password = ''
  emailConfig.displayName = ''
  emailConfig.enabled = false
  emailConfig.verificationTemplateId = null
  ElMessage.info(t('settings.settingsReset'))
}

// 邮箱验证开关切换
const handleEmailVerificationToggle = (val) => {
  if (val) {
    emailConfig.enabled = true
    loadEmailConfig()
    fetchEmailTemplates()
  }
}

// 加载邮箱配置
const loadEmailConfig = async () => {
  try {
    const response = await request.get('/email-config')
    if (response.code === 200 && response.data) {
      const data = response.data
      if (data.smtpHost) emailConfig.smtpHost = data.smtpHost
      if (data.smtpPort) emailConfig.smtpPort = data.smtpPort
      if (data.useSsl !== undefined) emailConfig.useSsl = data.useSsl
      if (data.fromAddress) emailConfig.fromAddress = data.fromAddress
      if (data.username) {
        emailConfig.username = data.username
        emailConfig.fromAddress = data.username
      }
      if (data.hasPassword) emailConfig.password = '••••••'
      if (data.displayName) emailConfig.displayName = data.displayName
      if (data.enabled !== undefined) emailConfig.enabled = data.enabled
      if (data.verificationTemplateId !== undefined) emailConfig.verificationTemplateId = data.verificationTemplateId
    }
  } catch (error) {
    console.error('加载邮箱配置失败:', error)
  }
}

// 加载邮件模板列表（初始加载，带分页）
const fetchEmailTemplates = async () => {
  try {
    const response = await request.get('/email-templates', { params: { size: 50 } })
    if (response.code === 200 && response.data) {
      emailTemplates.value = response.data.content || response.data
    }
  } catch (error) {
    console.error('加载邮件模板列表失败:', error)
  }
}

// 远程搜索邮件模板（支持分页）
const searchEmailTemplates = async (query) => {
  if (!query || query.trim() === '') {
    // 空查询时重新加载前50条
    fetchEmailTemplates()
    return
  }
  emailTemplateLoading.value = true
  try {
    const response = await request.get('/email-templates', {
      params: { name: query.trim(), size: 50 }
    })
    if (response.code === 200 && response.data) {
      emailTemplates.value = response.data.content || response.data
    }
  } catch (error) {
    console.error('搜索邮件模板失败:', error)
  } finally {
    emailTemplateLoading.value = false
  }
}

// 发送测试邮件
const sendTestEmail = async () => {
  if (!testEmailAddress.value) {
    ElMessage.warning(t('settings.testEmailPlaceholder'))
    return
  }
  if (!userStore.hasPermission('settings:view')) {
    ElMessage.error('权限不足，需要系统设置权限才能发送测试邮件')
    return
  }
  testEmailLoading.value = true
  try {
    const response = await request.post('/email-config/test', { toAddress: testEmailAddress.value })
    if (response.code === 200) {
      ElMessage.success(t('settings.testEmailSuccess'))
    }
  } catch (error) {
    // 详细错误信息已在请求拦截器中显示（包含后端返回的SMTP具体错误）
    console.error('测试邮件发送失败:', error)
  } finally {
    testEmailLoading.value = false
  }
}

// 跳转到邮件模板管理
const goToEmailTemplates = () => {
  router.push('/email-templates')
}

// 加载页脚配置
const loadFooterConfig = async () => {
  try {
    const response = await request.get('/system-config/footer')
    if (response.code === 200 && response.data) {
      const data = response.data
      if (data.enableCopyright !== undefined) footerSettings.enableCopyright = data.enableCopyright
      if (data.copyright) footerSettings.copyright = data.copyright
      if (data.enableFriendLink !== undefined) footerSettings.enableFriendLink = data.enableFriendLink
      if (data.friendLinkUrl) footerSettings.friendLinkUrl = data.friendLinkUrl
      if (data.friendLinkTitle) footerSettings.friendLinkTitle = data.friendLinkTitle
      if (data.enableBlog !== undefined) footerSettings.enableBlog = data.enableBlog
      if (data.blogUrl) footerSettings.blogUrl = data.blogUrl
      if (data.blogTitle) footerSettings.blogTitle = data.blogTitle
      if (data.enableGithub !== undefined) footerSettings.enableGithub = data.enableGithub
      if (data.githubUrl) footerSettings.githubUrl = data.githubUrl
      if (data.githubTitle) footerSettings.githubTitle = data.githubTitle
      if (data.enableEmail !== undefined) footerSettings.enableEmail = data.enableEmail
      if (data.emailAddress) footerSettings.emailAddress = data.emailAddress
      if (data.emailTitle) footerSettings.emailTitle = data.emailTitle
      if (data.enableCustomLinks !== undefined) footerSettings.enableCustomLinks = data.enableCustomLinks
      if (data.customLinks) footerSettings.customLinks = data.customLinks
    }
  } catch (error) {
    console.error('加载页脚配置失败:', error)
  }
}

// 更新 axios 全局超时时间
const updateAxiosTimeout = (timeout) => {
  if (timeout && timeout > 0) {
    axios.defaults.timeout = timeout
    localStorage.setItem('axiosTimeout', String(timeout))
    console.log('Axios timeout updated to:', timeout, 'ms')
  }
}

// 从服务器加载配置
const loadConfig = async () => {
  try {
    const response = await request.get('/system-config')
    if (response.code === 200 && response.data) {
      const data = response.data
      // 基础设置
      if (data.dateFormat) basicSettings.dateFormat = data.dateFormat
      // Mock配置
      if (data.defaultResponseDelay != null) mockSettings.defaultResponseDelay = data.defaultResponseDelay
      if (data.maxResponseDelay != null) mockSettings.maxResponseDelay = data.maxResponseDelay
      if (data.enableRequestLog != null) mockSettings.enableRequestLog = data.enableRequestLog
      if (data.logRetentionDays != null) mockSettings.logRetentionDays = data.logRetentionDays
      if (data.maxRequestBodySize != null) mockSettings.maxRequestBodySize = data.maxRequestBodySize
      if (data.axiosTimeout != null) {
        mockSettings.axiosTimeout = data.axiosTimeout
        updateAxiosTimeout(data.axiosTimeout)
      }
      if (data.customResponseCacheSeconds != null) mockSettings.customResponseCacheSeconds = data.customResponseCacheSeconds
    }
  } catch (error) {
    console.error('加载配置失败:', error)
  }
}

// 页面加载时获取数据
onMounted(() => {
  fetchSystemInfo()
  fetchAnnouncements()
  loadConfig()
  // 如果初始就是 system tab，启动自动刷新
  if (activeMenu.value === 'system') {
    startAutoRefresh()
  }
})

// 组件卸载时清理定时器
onBeforeUnmount(() => {
  stopAutoRefresh()
})
</script>

<style scoped>
.settings {
  padding: 20px;
}

.tip-icon {
  margin-left: 4px;
  vertical-align: middle;
  color: #909399;
  cursor: pointer;
  font-size: 14px;
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

.menu-card {
  margin-bottom: 20px;
}

.content-card {
  margin-bottom: 20px;
  min-height: 600px;
}

h2 {
  margin: 0 0 20px 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

:deep(.el-menu) {
  border-right: none;
}

:deep(.el-menu-item) {
  display: flex;
  align-items: center;
  gap: 8px;
}

:deep(.el-descriptions-item__label) {
  font-weight: 600;
  width: 150px;
  text-align: right;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
  padding: 0 10px;
}

.custom-link-item {
  margin-bottom: 16px;
  padding: 12px;
  background: #fafafa;
  border-radius: 6px;
  border: 1px solid #ebeef5;
}

.svg-preview :deep(svg) {
  width: 28px;
  height: 28px;
  display: block;
}

.iframe-hint {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
</style>

