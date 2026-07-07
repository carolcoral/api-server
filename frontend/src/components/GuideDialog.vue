<!--
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
-->

<template>
  <el-dialog
    v-model="visible"
    title=" "
    width="90%"
    :close-on-click-modal="false"
    :show-close="true"
    :destroy-on-close="false"
    class="guide-dialog"
    top="3vh"
    @close="handleClose"
  >
    <div class="guide-container">
      <!-- 左侧步骤导航 -->
      <div class="guide-sidebar">
        <div class="sidebar-header">
          <svg class="sidebar-logo" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
            <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
            <line x1="12" y1="22.08" x2="12" y2="12"/>
          </svg>
          <span>{{ $t('guide.title') }}</span>
        </div>
        <div class="step-nav">
          <div
            v-for="(step, idx) in steps"
            :key="idx"
            class="step-nav-item"
            :class="{ active: currentStep === idx }"
            @click="currentStep = idx"
          >
            <div class="step-nav-number" :class="{ completed: idx < currentStep, active: idx === currentStep }">
              <el-icon v-if="idx < currentStep" :size="14"><CircleCheck /></el-icon>
              <span v-else>{{ idx + 1 }}</span>
            </div>
            <div class="step-nav-text">
              <span class="step-nav-title">{{ step.title }}</span>
              <span class="step-nav-subtitle">{{ step.subtitle }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧内容区 -->
      <div class="guide-content">
        <div class="content-header">
          <div class="step-indicator">
            <span class="step-badge">{{ $t('guide.stepIndicator', { current: currentStep + 1, total: steps.length }) }}</span>
            <h2>{{ steps[currentStep]?.title }}</h2>
          </div>
          <p class="step-summary">{{ steps[currentStep]?.summary }}</p>
        </div>

        <div class="content-body" ref="contentBodyRef">
          <!-- 步骤内容插槽 -->
          <div class="step-detail">
            <!-- 步骤1: 创建项目 -->
            <div v-if="currentStep === 0" :key="'step0'" class="step-section">
              <div class="illustration-card">
                <div class="illus-icon" style="background: linear-gradient(135deg, #667eea, #764ba2);">
                  <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
                  </svg>
                </div>
                <div class="illus-body">
                  <h4>{{ $t('guide.step1Heading') }}</h4>
                  <p>{{ $t('guide.step1Desc') }}</p>
                </div>
              </div>
              <div class="info-cards">
                <div class="info-card">
                  <div class="info-card-icon" style="background: #e8f4fd; color: #409eff;">
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/>
                    </svg>
                  </div>
                  <div class="info-card-text">
                    <strong>{{ $t('guide.step1NameTitle') }}</strong>
                    <p>{{ $t('guide.step1NameDesc') }}</p>
                  </div>
                </div>
                <div class="info-card">
                  <div class="info-card-icon" style="background: #fef0e6; color: #e6a23c;">
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/>
                    </svg>
                  </div>
                  <div class="info-card-text">
                    <strong>{{ $t('guide.step1CodeTitle') }}</strong>
                    <p>{{ $t('guide.step1CodeDesc') }}</p>
                  </div>
                </div>
                <div class="info-card">
                  <div class="info-card-icon" style="background: #e8f5e9; color: #67c23a;">
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>
                    </svg>
                  </div>
                  <div class="info-card-text">
                    <strong>{{ $t('guide.step1MemberTitle') }}</strong>
                    <p>{{ $t('guide.step1MemberDesc') }}</p>
                  </div>
                </div>
              </div>
            </div>

            <!-- 步骤2: 导入/新增接口 -->
            <div v-else-if="currentStep === 1" :key="'step1'" class="step-section">
              <div class="two-col">
                <div class="col-card import-card">
                  <div class="col-card-header">
                    <div class="col-card-icon" style="background: linear-gradient(135deg, #4facfe, #00f2fe);">
                      <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/>
                      </svg>
                    </div>
                    <h4>{{ $t('guide.step2ImportTitle') }}</h4>
                  </div>
                  <div class="col-card-body">
                    <p>{{ $t('guide.step2ImportDesc') }}</p>
                    <ul>
                      <li>{{ $t('guide.step2ImportItem1') }}</li>
                      <li>{{ $t('guide.step2ImportItem2') }}</li>
                      <li>{{ $t('guide.step2ImportItem3') }}</li>
                      <li>{{ $t('guide.step2ImportItem4') }}</li>
                    </ul>
                    <div class="col-card-tip">
                      <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/>
                      </svg>
                      {{ $t('guide.step2ImportTip') }}
                    </div>
                  </div>
                </div>
                <div class="col-card create-card">
                  <div class="col-card-header">
                    <div class="col-card-icon" style="background: linear-gradient(135deg, #667eea, #764ba2);">
                      <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                        <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
                      </svg>
                    </div>
                    <h4>{{ $t('guide.step2CreateTitle') }}</h4>
                  </div>
                  <div class="col-card-body">
                    <p>{{ $t('guide.step2CreateDesc') }}</p>
                    <ul>
                      <li>{{ $t('guide.step2CreateItem1') }}</li>
                      <li>{{ $t('guide.step2CreateItem2') }}</li>
                      <li>{{ $t('guide.step2CreateItem3') }}</li>
                      <li>{{ $t('guide.step2CreateItem4') }}</li>
                      <li>{{ $t('guide.step2CreateItem5') }}</li>
                    </ul>
                    <div class="col-card-tip">
                      <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/>
                      </svg>
                      {{ $t('guide.step2CreateTip') }}
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 步骤3: 配置响应数据 -->
            <div v-else-if="currentStep === 2" :key="'step2'" class="step-section">
              <div class="illustration-card">
                <div class="illus-icon" style="background: linear-gradient(135deg, #f093fb, #f5576c);">
                  <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/>
                  </svg>
                </div>
                <div class="illus-body">
                  <h4>{{ $t('guide.step3Heading') }}</h4>
                  <p>{{ $t('guide.step3Desc') }}</p>
                </div>
              </div>
              <div class="info-cards">
                <div class="info-card">
                  <div class="info-card-icon" style="background: #e8f4fd; color: #409eff;">
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <rect x="2" y="3" width="20" height="14" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/>
                    </svg>
                  </div>
                  <div class="info-card-text">
                    <strong>{{ $t('guide.step3StatusTitle') }}</strong>
                    <p>{{ $t('guide.step3StatusDesc') }}</p>
                  </div>
                </div>
                <div class="info-card">
                  <div class="info-card-icon" style="background: #fef0e6; color: #e6a23c;">
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/>
                    </svg>
                  </div>
                  <div class="info-card-text">
                    <strong>{{ $t('guide.step3BodyTitle') }}</strong>
                    <p>{{ $t('guide.step3BodyDesc') }}</p>
                  </div>
                </div>
                <div class="info-card">
                  <div class="info-card-icon" style="background: #e8f5e9; color: #67c23a;">
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
                    </svg>
                  </div>
                  <div class="info-card-text">
                    <strong>{{ $t('guide.step3WeightTitle') }}</strong>
                    <p>{{ $t('guide.step3WeightDesc') }}</p>
                  </div>
                </div>
                <div class="info-card">
                  <div class="info-card-icon" style="background: #fde2e2; color: #f56c6c;">
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
                    </svg>
                  </div>
                  <div class="info-card-text">
                    <strong>{{ $t('guide.step3DelayTitle') }}</strong>
                    <p>{{ $t('guide.step3DelayDesc') }}</p>
                  </div>
                </div>
              </div>
            </div>

            <!-- 步骤4: 请求参数匹配 -->
            <div v-else-if="currentStep === 3" :key="'step3'" class="step-section">
              <div class="illustration-card">
                <div class="illus-icon" style="background: linear-gradient(135deg, #43e97b, #38f9d7);">
                  <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
                  </svg>
                </div>
                <div class="illus-body">
                  <h4>{{ $t('guide.step4Heading') }}</h4>
                  <p>{{ $t('guide.step4Desc') }}</p>
                </div>
              </div>
              <div class="info-cards two-cols">
                <div class="info-card">
                  <div class="info-card-icon" style="background: #e8f4fd; color: #409eff;">
                    <el-tag size="small" type="success">PATH</el-tag>
                  </div>
                  <div class="info-card-text">
                    <strong>{{ $t('guide.step4PathTitle') }}</strong>
                    <p>{{ $t('guide.step4PathDesc') }}</p>
                  </div>
                </div>
                <div class="info-card">
                  <div class="info-card-icon" style="background: #fef0e6; color: #e6a23c;">
                    <el-tag size="small" type="primary">QUERY</el-tag>
                  </div>
                  <div class="info-card-text">
                    <strong>{{ $t('guide.step4QueryTitle') }}</strong>
                    <p>{{ $t('guide.step4QueryDesc') }}</p>
                  </div>
                </div>
                <div class="info-card">
                  <div class="info-card-icon" style="background: #e8f5e9; color: #67c23a;">
                    <el-tag size="small" type="warning">BODY</el-tag>
                  </div>
                  <div class="info-card-text">
                    <strong>{{ $t('guide.step4BodyTitle') }}</strong>
                    <p>{{ $t('guide.step4BodyDesc') }}</p>
                  </div>
                </div>
                <div class="info-card">
                  <div class="info-card-icon" style="background: #fde2e2; color: #f56c6c;">
                    <el-tag size="small" type="info">HEADER</el-tag>
                  </div>
                  <div class="info-card-text">
                    <strong>{{ $t('guide.step4HeaderTitle') }}</strong>
                    <p>{{ $t('guide.step4HeaderDesc') }}</p>
                  </div>
                </div>
              </div>
              <div class="match-example">
                <h4>{{ $t('guide.step4ExampleHeading') }}</h4>
                <div class="example-block">
                  <div class="example-row">
                    <span class="example-label">{{ $t('guide.step4ExampleScenario') }}</span>
                    <span class="example-value">{{ $t('guide.step4ExampleScenarioValue') }}</span>
                  </div>
                  <div class="example-row">
                    <span class="example-label">{{ $t('guide.step4ExamplePath') }}</span>
                    <code>/api/user/{userId}</code>
                  </div>
                  <div class="example-row">
                    <span class="example-label">{{ $t('guide.step4ExampleConfig') }}</span>
                    <div class="param-configs">
                      <span class="param-tag">{{ $t('guide.step4ExampleConfig1') }}</span>
                      <span class="param-tag">{{ $t('guide.step4ExampleConfig2') }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 步骤5: AI 智能生成 -->
            <div v-else-if="currentStep === 4" :key="'step4'" class="step-section">
              <div class="illustration-card">
                <div class="illus-icon" style="background: linear-gradient(135deg, #f6d365, #fda085);">
                  <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M12 2l1.5 5.5L19 9l-5.5 1.5L12 16l-1.5-5.5L5 9l5.5-1.5z"/>
                    <path d="M18 16l.75 2.25L21 19l-2.25.75L18 22l-.75-2.25L15 19l2.25-.75z"/>
                  </svg>
                </div>
                <div class="illus-body">
                  <h4>{{ $t('guide.step5Heading') }}</h4>
                  <p>{{ $t('guide.step5Desc') }}</p>
                </div>
              </div>
              <div class="ai-features">
                <div class="ai-feature-item">
                  <div class="ai-f-icon" style="background: linear-gradient(135deg, #f6d365, #fda085);">
                    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                    </svg>
                  </div>
                  <div class="ai-f-text">
                    <strong>{{ $t('guide.step5ChatTitle') }}</strong>
                    <p>{{ $t('guide.step5ChatDesc') }}</p>
                  </div>
                </div>
                <div class="ai-feature-item">
                  <div class="ai-f-icon" style="background: linear-gradient(135deg, #667eea, #764ba2);">
                    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                      <polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/>
                    </svg>
                  </div>
                  <div class="ai-f-text">
                    <strong>{{ $t('guide.step5ResponseTitle') }}</strong>
                    <p>{{ $t('guide.step5ResponseDesc') }}</p>
                  </div>
                </div>
                <div class="ai-feature-item">
                  <div class="ai-f-icon" style="background: linear-gradient(135deg, #f093fb, #f5576c);">
                    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                      <polyline points="14.5 17.5 3 6 3 3 6 3 17.5 14.5"/><line x1="13" y1="19" x2="19" y2="13"/><line x1="16" y1="16" x2="20" y2="20"/><line x1="19" y1="21" x2="21" y2="19"/><polyline points="14.5 6.5 18 3 21 6 17.5 9.5"/><line x1="5" y1="14" x2="9" y2="18"/>
                    </svg>
                  </div>
                  <div class="ai-f-text">
                    <strong>{{ $t('guide.step5CodeTitle') }}</strong>
                    <p>{{ $t('guide.step5CodeDesc') }}</p>
                  </div>
                </div>
                <div class="ai-feature-item">
                  <div class="ai-f-icon" style="background: linear-gradient(135deg, #4facfe, #00f2fe);">
                    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/>
                    </svg>
                  </div>
                  <div class="ai-f-text">
                    <strong>{{ $t('guide.step5EmailTitle') }}</strong>
                    <p>{{ $t('guide.step5EmailDesc') }}</p>
                  </div>
                </div>
                <div class="ai-feature-item">
                  <div class="ai-f-icon" style="background: linear-gradient(135deg, #43e97b, #38f9d7);">
                    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                    </svg>
                  </div>
                  <div class="ai-f-text">
                    <strong>{{ $t('guide.step5ApiTitle') }}</strong>
                    <p>{{ $t('guide.step5ApiDesc') }}</p>
                  </div>
                </div>
                <div class="ai-feature-item">
                  <div class="ai-f-icon" style="background: linear-gradient(135deg, #a18cd1, #fbc2eb);">
                    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                      <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
                    </svg>
                  </div>
                  <div class="ai-f-text">
                    <strong>{{ $t('guide.step5TimeoutTitle') }}</strong>
                    <p>{{ $t('guide.step5TimeoutDesc') }}</p>
                  </div>
                </div>
              </div>
            </div>

            <!-- 步骤6: 调用与监控 -->
            <div v-else-if="currentStep === 5" :key="'step6'" class="step-section">
              <div class="illustration-card">
                <div class="illus-icon" style="background: linear-gradient(135deg, #667eea, #764ba2);">
                  <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                    <rect x="2" y="3" width="20" height="14" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/>
                  </svg>
                </div>
                <div class="illus-body">
                  <h4>{{ $t('guide.step6Heading') }}</h4>
                  <p>{{ $t('guide.step6Desc') }}</p>
                </div>
              </div>
              <div class="info-cards">
                <div class="info-card">
                  <div class="info-card-icon" style="background: #e8f4fd; color: #409eff;">
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                    </svg>
                  </div>
                  <div class="info-card-text">
                    <strong>{{ $t('guide.step6UrlTitle') }}</strong>
                    <p>{{ $t('guide.step6UrlDesc', { open: '{', close: '}' }) }}</p>
                  </div>
                </div>
                <div class="info-card">
                  <div class="info-card-icon" style="background: #e8f5e9; color: #67c23a;">
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/>
                    </svg>
                  </div>
                  <div class="info-card-text">
                    <strong>{{ $t('guide.step6StatsTitle') }}</strong>
                    <p>{{ $t('guide.step6StatsDesc') }}</p>
                  </div>
                </div>
                <div class="info-card">
                  <div class="info-card-icon" style="background: #fef0e6; color: #e6a23c;">
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                    </svg>
                  </div>
                  <div class="info-card-text">
                    <strong>{{ $t('guide.step6SecurityTitle') }}</strong>
                    <p>{{ $t('guide.step6SecurityDesc') }}</p>
                  </div>
                </div>
              </div>
              <div class="quick-start-box">
                <h4>{{ $t('guide.step6ChecklistHeading') }}</h4>
                <div class="checklist">
                  <div class="checklist-item"><span class="check-icon">✓</span> {{ $t('guide.step6CheckItem1') }}</div>
                  <div class="checklist-item"><span class="check-icon">✓</span> {{ $t('guide.step6CheckItem2') }}</div>
                  <div class="checklist-item"><span class="check-icon">✓</span> {{ $t('guide.step6CheckItem3') }}</div>
                  <div class="checklist-item"><span class="check-icon">✓</span> {{ $t('guide.step6CheckItem4') }}</div>
                  <div class="checklist-item"><span class="check-icon">✓</span> {{ $t('guide.step6CheckItem5') }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 底部导航 -->
        <div class="content-footer">
          <el-button @click="prevStep" :disabled="currentStep === 0">
            <el-icon><ArrowLeft /></el-icon> {{ $t('guide.prev') }}
          </el-button>
          <div class="step-dots">
            <span
              v-for="(step, idx) in steps"
              :key="idx"
              class="dot"
              :class="{ active: idx === currentStep, completed: idx < currentStep }"
              @click="currentStep = idx"
            ></span>
          </div>
          <el-button v-if="currentStep < steps.length - 1" type="primary" @click="nextStep">
            {{ $t('guide.next') }} <el-icon><ArrowRight /></el-icon>
          </el-button>
          <el-button v-else type="success" @click="handleClose">
            <el-icon><CircleCheck /></el-icon> {{ $t('guide.start') }}
          </el-button>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, watch, computed, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, ArrowRight, CircleCheck } from '@element-plus/icons-vue'

const { t } = useI18n()

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const visible = ref(props.modelValue)
const currentStep = ref(0)
const contentBodyRef = ref(null)

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val) {
    currentStep.value = 0
  }
})

// 切换步骤时滚动到顶部
watch(currentStep, () => {
  nextTick(() => {
    if (contentBodyRef.value) {
      contentBodyRef.value.scrollTop = 0
    }
  })
})

const steps = computed(() => {
  try {
    return [
      { title: t('guide.step1Title'), subtitle: t('guide.step1Subtitle'), summary: t('guide.step1Summary') },
      { title: t('guide.step2Title'), subtitle: t('guide.step2Subtitle'), summary: t('guide.step2Summary') },
      { title: t('guide.step3Title'), subtitle: t('guide.step3Subtitle'), summary: t('guide.step3Summary') },
      { title: t('guide.step4Title'), subtitle: t('guide.step4Subtitle'), summary: t('guide.step4Summary') },
      { title: t('guide.step5Title'), subtitle: t('guide.step5Subtitle'), summary: t('guide.step5Summary') },
      { title: t('guide.step6Title'), subtitle: t('guide.step6Subtitle'), summary: t('guide.step6Summary') }
    ]
  } catch (e) {
    console.error('GuideDialog steps computed error:', e)
    return []
  }
})

const nextStep = () => {
  const maxStep = (steps.value?.length || 6) - 1
  if (currentStep.value < maxStep) {
    currentStep.value++
  }
}

const prevStep = () => {
  if (currentStep.value > 0) {
    currentStep.value--
  }
}

const handleClose = () => {
  visible.value = false
  currentStep.value = 0
  emit('update:modelValue', false)
}
</script>

<style scoped>
/* 弹窗整体 */
.guide-dialog :deep(.el-dialog__header) {
  padding: 0;
  margin: 0;
  height: 0;
  overflow: hidden;
  border: none;
}

.guide-dialog :deep(.el-dialog__headerbtn) {
  top: 12px;
  right: 16px;
  z-index: 10;
}

.guide-dialog :deep(.el-dialog__body) {
  padding: 0;
  height: 82vh;
  overflow: hidden;
}

.guide-container {
  display: flex;
  height: 100%;
  border-radius: 8px;
  overflow: hidden;
}

/* 左侧导航 */
.guide-sidebar {
  width: 240px;
  flex-shrink: 0;
  background: linear-gradient(180deg, #1a1a3e 0%, #16213e 50%, #0f3460 100%);
  display: flex;
  flex-direction: column;
  padding: 0;
}

.sidebar-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 24px 20px 20px;
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.sidebar-logo {
  color: #667eea;
}

.step-nav {
  flex: 1;
  padding: 12px 0;
  overflow-y: auto;
}

.step-nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  cursor: pointer;
  transition: all 0.25s ease;
  border-left: 3px solid transparent;
}

.step-nav-item:hover {
  background: rgba(255, 255, 255, 0.05);
}

.step-nav-item.active {
  background: rgba(102, 126, 234, 0.12);
  border-left-color: #667eea;
}

.step-nav-number {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.5);
  transition: all 0.3s ease;
}

.step-nav-number.active {
  background: #667eea;
  color: #fff;
}

.step-nav-number.completed {
  background: #67c23a;
  color: #fff;
}

.step-nav-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.step-nav-title {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.85);
  font-weight: 500;
}

.step-nav-subtitle {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
}

/* 右侧内容 */
.guide-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #f8f9fc;
  overflow: hidden;
}

.content-header {
  padding: 28px 32px 20px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}

.step-indicator {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.step-badge {
  display: inline-block;
  padding: 3px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  background: #ecf5ff;
  color: #409eff;
}

.content-header h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #303133;
}

.step-summary {
  margin: 0;
  font-size: 14px;
  color: #909399;
  line-height: 1.6;
}

.content-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
}

/* 步骤内容通用 */
.step-section {
  animation: fadeIn 0.35s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 插图卡片 */
.illustration-card {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
  border: 1px solid #ebeef5;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

.illus-icon {
  width: 60px;
  height: 60px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.illus-body h4 {
  margin: 0 0 8px;
  font-size: 17px;
  font-weight: 600;
  color: #303133;
}

.illus-body p {
  margin: 0;
  font-size: 14px;
  color: #606266;
  line-height: 1.7;
}

/* 信息卡片网格 */
.info-cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}

.info-cards.two-cols {
  grid-template-columns: repeat(2, 1fr);
}

.info-card {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  background: #fff;
  border-radius: 10px;
  padding: 18px;
  border: 1px solid #ebeef5;
  transition: box-shadow 0.3s ease;
}

.info-card:hover {
  box-shadow: 0 4px 16px rgba(0,0,0,0.06);
}

.info-card-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.info-card-text strong {
  display: block;
  font-size: 14px;
  color: #303133;
  margin-bottom: 4px;
}

.info-card-text p {
  margin: 0;
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
}

.info-card-text code {
  background: #f5f5f5;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 12px;
  color: #e83e8c;
}

/* 双列卡片 */
.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 24px;
}

.col-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #ebeef5;
  overflow: hidden;
  transition: box-shadow 0.3s ease;
}

.col-card:hover {
  box-shadow: 0 6px 24px rgba(0,0,0,0.08);
}

.col-card-header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 20px 0;
}

.col-card-header h4 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.col-card-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.col-card-body {
  padding: 14px 20px 20px;
}

.col-card-body p {
  margin: 0 0 12px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}

.col-card-body ul {
  margin: 0 0 14px;
  padding-left: 18px;
}

.col-card-body li {
  font-size: 13px;
  color: #606266;
  line-height: 2;
}

.col-card-body li::marker {
  color: #667eea;
}

.col-card-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  background: #ecf5ff;
  border-radius: 8px;
  font-size: 12px;
  color: #409eff;
  line-height: 1.5;
}

.col-card-tip svg {
  flex-shrink: 0;
  color: #409eff;
}

/* 匹配示例 */
.match-example {
  margin-top: 24px;
}

.match-example h4 {
  margin: 0 0 14px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.example-block {
  background: #fff;
  border-radius: 10px;
  border: 1px solid #ebeef5;
  padding: 20px;
}

.example-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.example-row:last-child {
  margin-bottom: 0;
}

.example-label {
  font-size: 13px;
  font-weight: 500;
  color: #909399;
  min-width: 70px;
  flex-shrink: 0;
  padding-top: 1px;
}

.example-value {
  font-size: 14px;
  color: #303133;
}

.example-row code {
  background: #f5f5f5;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 13px;
  color: #e83e8c;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
}

.param-configs {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.param-tag {
  display: inline-block;
  padding: 4px 12px;
  background: #f0f9ff;
  border: 1px solid #b3d8ff;
  border-radius: 6px;
  font-size: 13px;
  color: #409eff;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
}

/* AI 功能列表 */
.ai-features {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}

.ai-feature-item {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  background: #fff;
  border-radius: 10px;
  padding: 18px;
  border: 1px solid #ebeef5;
  transition: all 0.3s ease;
}

.ai-feature-item:hover {
  box-shadow: 0 4px 16px rgba(0,0,0,0.06);
  border-color: #c4b5fd;
}

.ai-f-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.ai-f-text strong {
  display: block;
  font-size: 14px;
  color: #303133;
  margin-bottom: 4px;
}

.ai-f-text p {
  margin: 0;
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
}

/* 快速开始清单 */
.quick-start-box {
  margin-top: 24px;
  background: linear-gradient(135deg, #ecf5ff, #f0f9ff);
  border: 1px solid #b3d8ff;
  border-radius: 12px;
  padding: 20px 24px;
}

.quick-start-box h4 {
  margin: 0 0 14px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.checklist {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.checklist-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #303133;
}

.check-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  font-size: 12px;
  font-weight: 700;
  color: #67c23a;
  flex-shrink: 0;
}

/* 底部导航 */
.content-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 32px;
  background: #fff;
  border-top: 1px solid #ebeef5;
}

.step-dots {
  display: flex;
  gap: 8px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #dcdfe6;
  cursor: pointer;
  transition: all 0.3s ease;
}

.dot.active {
  background: #667eea;
  width: 24px;
  border-radius: 4px;
}

.dot.completed {
  background: #67c23a;
}

/* 响应式 */
@media (max-width: 768px) {
  .guide-dialog :deep(.el-dialog__body) {
    height: 85vh;
  }

  .guide-sidebar {
    width: 72px;
  }

  .sidebar-header span {
    display: none;
  }

  .step-nav-text {
    display: none;
  }

  .step-nav-item {
    justify-content: center;
    padding: 14px;
  }

  .content-header {
    padding: 20px;
  }

  .content-body {
    padding: 16px;
  }

  .info-cards,
  .ai-features,
  .two-col {
    grid-template-columns: 1fr;
  }

  .content-footer {
    padding: 12px 20px;
  }
}
</style>
