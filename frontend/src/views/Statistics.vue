<!--
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
-->

<template>
  <div class="statistics-page">
    <!-- IOPS 实时概览卡片 -->
    <el-row :gutter="20" style="margin-bottom: 20px;">
      <el-col :span="8">
        <el-card class="iops-card" shadow="hover">
          <div class="iops-card-inner">
            <div class="iops-label">{{ $t('statistics.currentIops') }}</div>
            <div class="iops-value">{{ currentIops }}</div>
            <div class="iops-unit">{{ iopsUnit }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="iops-card" shadow="hover">
          <div class="iops-card-inner">
            <div class="iops-label">{{ $t('statistics.avgIops') }}</div>
            <div class="iops-value">{{ avgIops }}</div>
            <div class="iops-unit">{{ iopsUnit }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="iops-card" shadow="hover">
          <div class="iops-card-inner">
            <div class="iops-label">{{ $t('statistics.peakIops') }}</div>
            <div class="iops-value">{{ peakIops }}</div>
            <div class="iops-unit">{{ iopsUnit }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- IOPS 趋势图 -->
    <el-card class="chart-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span>{{ $t('statistics.iopsTrend') }}</span>
          <el-radio-group v-model="iopsMinutes" size="small" @change="fetchIops">
            <el-radio-button :value="30">30min</el-radio-button>
            <el-radio-button :value="60">1h</el-radio-button>
            <el-radio-button :value="180">3h</el-radio-button>
            <el-radio-button :value="360">6h</el-radio-button>
            <el-radio-button :value="720">12h</el-radio-button>
            <el-radio-button :value="1440">24h</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div ref="iopsChart" class="chart-container"></div>
    </el-card>

    <!-- 请求频率统计 -->
    <el-card class="chart-card" shadow="hover" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span>{{ $t('statistics.requestFrequency') }}</span>
          <el-radio-group v-model="freqGranularity" size="small" @change="fetchRequestFrequency">
            <el-radio-button value="yearly">{{ $t('statistics.yearly') }}</el-radio-button>
            <el-radio-button value="monthly">{{ $t('statistics.monthly') }}</el-radio-button>
            <el-radio-button value="daily">{{ $t('statistics.daily') }}</el-radio-button>
            <el-radio-button value="hourly">{{ $t('statistics.hourly') }}</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div ref="requestFreqChart" class="chart-container"></div>
    </el-card>

    <!-- 来源IP统计 -->
    <el-card class="chart-card" shadow="hover" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span>{{ $t('statistics.sourceIps') }}</span>
          <el-radio-group v-model="ipGranularity" size="small" @change="fetchSourceIps">
            <el-radio-button value="yearly">{{ $t('statistics.yearly') }}</el-radio-button>
            <el-radio-button value="monthly">{{ $t('statistics.monthly') }}</el-radio-button>
            <el-radio-button value="daily">{{ $t('statistics.daily') }}</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div ref="sourceIpChart" class="chart-container"></div>
    </el-card>

    <!-- AI 调用统计 -->
    <el-card class="chart-card" shadow="hover" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span>{{ $t('statistics.aiCalls') }}</span>
          <el-radio-group v-model="aiCallsGranularity" size="small" @change="fetchAiCalls">
            <el-radio-button value="yearly">{{ $t('statistics.yearly') }}</el-radio-button>
            <el-radio-button value="monthly">{{ $t('statistics.monthly') }}</el-radio-button>
            <el-radio-button value="daily">{{ $t('statistics.daily') }}</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div ref="aiCallsChart" class="chart-container"></div>
    </el-card>

    <!-- 新增趋势统计 -->
    <el-card class="chart-card" shadow="hover" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span>{{ $t('statistics.creationTrend') }}</span>
          <el-radio-group v-model="trendGranularity" size="small" @change="fetchCreationTrend">
            <el-radio-button value="yearly">{{ $t('statistics.yearly') }}</el-radio-button>
            <el-radio-button value="monthly">{{ $t('statistics.monthly') }}</el-radio-button>
            <el-radio-button value="daily">{{ $t('statistics.daily') }}</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div ref="creationTrendChart" class="chart-container"></div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import request from '@/utils/request'
import * as echarts from 'echarts'

const { t } = useI18n()

const freqGranularity = ref('daily')
const iopsMinutes = ref(60)
const currentIops = ref('--')
const avgIops = ref('--')
const peakIops = ref('--')
const iopsUnit = ref('req/s')

const ipGranularity = ref('daily')
const aiCallsGranularity = ref('monthly')
const trendGranularity = ref('daily')
const iopsChart = ref(null)
const requestFreqChart = ref(null)
const sourceIpChart = ref(null)
const aiCallsChart = ref(null)
const creationTrendChart = ref(null)

let iopsChartInstance = null
let freqChartInstance = null
let ipChartInstance = null
let aiCallsChartInstance = null
let trendChartInstance = null

// ========== IOPS 统计 ==========

const fetchIops = async () => {
  try {
    const response = await request.get('/statistics/iops', {
      params: { minutes: iopsMinutes.value }
    })
    if (response.code === 200 && response.data) {
      const vals = response.data.values || []
      iopsUnit.value = response.data.unit || 'req/s'
      if (vals.length > 0) {
        currentIops.value = vals[vals.length - 1].toFixed(2)
        avgIops.value = (vals.reduce((a, b) => a + b, 0) / vals.length).toFixed(2)
        peakIops.value = Math.max(...vals).toFixed(2)
      } else {
        currentIops.value = '0.00'
        avgIops.value = '0.00'
        peakIops.value = '0.00'
      }
      renderIopsChart(response.data)
    } else {
      // API 返回异常时也显示 0.00
      currentIops.value = '0.00'
      avgIops.value = '0.00'
      peakIops.value = '0.00'
      renderIopsChart({ labels: [], values: [], unit: 'req/s' })
    }
  } catch (error) {
    console.error('获取IOPS统计失败:', error)
    currentIops.value = '0.00'
    avgIops.value = '0.00'
    peakIops.value = '0.00'
  }
}

// 将时间序列数据补全缺失时间段，填充 0
const padTimeSeries = (rawLabels, rawValues, generateAllSlotsFn) => {
  const map = {}
  rawLabels.forEach((l, i) => {
    if (l != null && l !== '' && l !== 'null') {
      map[l] = rawValues[i] || 0
    }
  })
  const allSlots = generateAllSlotsFn(rawLabels.filter(l => l != null && l !== '' && l !== 'null'))
  const labels = allSlots.map(s => s.label)
  const values = allSlots.map(s => (map[s.label] !== undefined ? map[s.label] : 0))
  return { labels, values }
}

const renderIopsChart = (data) => {
  if (!iopsChart.value) return
  if (!iopsChartInstance) {
    iopsChartInstance = echarts.init(iopsChart.value)
  }

  const rawLabels = data.labels || []
  const rawValues = data.values || []
  const isPerSec = iopsUnit.value === 'req/s'

  // 生成完整时间段并补 0
  const generateSlots = (existingLabels) => {
    if (existingLabels.length === 0) return []
    const fmt = isPerSec ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD HH:mm'
    const sorted = [...existingLabels].sort()
    const first = sorted[0], last = sorted[sorted.length - 1]
    const slots = []
    const step = isPerSec ? 1000 : 60000
    let current = new Date(first.replace(' ', 'T') + (isPerSec ? '' : ':00'))
    const end = new Date(last.replace(' ', 'T') + (isPerSec ? '' : ':00'))
    while (current <= end) {
      const pad = (n) => String(n).padStart(2, '0')
      const label = isPerSec
        ? `${current.getFullYear()}-${pad(current.getMonth()+1)}-${pad(current.getDate())} ${pad(current.getHours())}:${pad(current.getMinutes())}:${pad(current.getSeconds())}`
        : `${current.getFullYear()}-${pad(current.getMonth()+1)}-${pad(current.getDate())} ${pad(current.getHours())}:${pad(current.getMinutes())}`
      slots.push({ label, time: new Date(current) })
      current = new Date(current.getTime() + step)
    }
    return slots
  }
  const { labels, values: chartValues } = padTimeSeries(rawLabels, rawValues, generateSlots)

  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const p = params[0]
        return `${p.axisValue}<br/>${t('statistics.iops')}: ${p.value} ${iopsUnit.value}`
      }
    },
    grid: { left: '3%', right: '8%', bottom: '15%', top: '10px', containLabel: true },
    xAxis: {
      type: 'category',
      data: labels,
      axisLabel: {
        rotate: 45,
        fontSize: 10,
        formatter: (val) => val.substring(11)
      }
    },
    yAxis: {
      type: 'value',
      name: iopsUnit.value,
      minInterval: iopsUnit.value === 'req/s' ? 0.01 : 1
    },
    dataZoom: labels.length > 10 ? [
      { type: 'slider', start: 0, end: 100, height: 20, bottom: 0 },
      { type: 'inside', start: 0, end: 100 }
    ] : [],
    series: [{
      name: 'IOPS',
      type: 'line',
      data: chartValues,
      smooth: true,
      symbol: 'none',
      lineStyle: { color: '#30cfd0', width: 2 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(48,207,208,0.4)' },
          { offset: 1, color: 'rgba(48,207,208,0.02)' }
        ])
      },
      markLine: {
        silent: true,
        data: chartValues.length > 0 ? [{
          type: 'average',
          name: 'Avg',
          lineStyle: { color: '#f5576c', type: 'dashed' }
        }] : []
      }
    }],
    title: labels.length === 0 ? {
      text: t('statistics.noData') || '暂无数据',
      left: 'center',
      top: 'center',
      textStyle: { color: '#999', fontSize: 14, fontWeight: 'normal' }
    } : undefined
  }
  iopsChartInstance.setOption(option, true)
}

// ========== 请求频率统计 ==========

const fetchRequestFrequency = async () => {
  try {
    const days = freqGranularity.value === 'hourly' ? 1 : freqGranularity.value === 'yearly' ? 5 : 7
    const response = await request.get('/statistics/request-frequency', {
      params: { days, granularity: freqGranularity.value }
    })
    if (response.code === 200 && response.data) {
      renderRequestFreqChart(response.data)
    }
  } catch (error) {
    console.error('获取请求频率统计失败:', error)
  }
}

const renderRequestFreqChart = (data) => {
  if (!requestFreqChart.value) return
  if (!freqChartInstance) {
    freqChartInstance = echarts.init(requestFreqChart.value)
  }

  const rawLabels = data.labels || []
  const rawValues = data.values || []
  const g = freqGranularity.value

  // 生成完整时间段并补 0
  const generateSlots = () => {
    const pad = (n) => String(n).padStart(2, '0')
    const now = new Date()
    const slots = []
    if (g === 'hourly') {
      for (let i = 23; i >= 0; i--) {
        const d = new Date(now)
        d.setHours(now.getHours() - i, 0, 0, 0)
        slots.push({ label: `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:00` })
      }
    } else if (g === 'daily') {
      for (let i = 6; i >= 0; i--) {
        const d = new Date(now)
        d.setDate(now.getDate() - i)
        slots.push({ label: `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}` })
      }
    } else if (g === 'monthly') {
      for (let i = 11; i >= 0; i--) {
        const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
        slots.push({ label: `${d.getFullYear()}-${pad(d.getMonth()+1)}` })
      }
    } else if (g === 'yearly') {
      for (let i = 4; i >= 0; i--) {
        slots.push({ label: `${now.getFullYear() - i}` })
      }
    }
    return slots
  }
  const { labels, values: chartValues } = padTimeSeries(rawLabels, rawValues, generateSlots)
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter: (params) => {
        const p = Array.isArray(params) ? params[0] : params
        return `<strong>${p.axisValue}</strong><br/>${p.marker} ${t('statistics.requestCount')}: <strong>${p.value}</strong>`
      }
    },
    legend: { data: [t('statistics.requestCount')], top: 0 },
    grid: { left: '3%', right: '4%', bottom: '12%', top: '45px', containLabel: true },
    xAxis: {
      type: 'category',
      data: labels,
      axisLabel: { rotate: labels.length > 12 ? 45 : 0, fontSize: 11 },
      boundaryGap: false
    },
    yAxis: { type: 'value', minInterval: 1, name: t('statistics.requestCount') },
    dataZoom: labels.length > 12 ? [
      { type: 'slider', start: Math.max(0, ((labels.length - 12) / labels.length) * 100), end: 100, height: 18, bottom: 2 },
      { type: 'inside' }
    ] : [],
    series: [{
      name: t('statistics.requestCount'),
      type: 'line',
      data: chartValues,
      smooth: true,
      symbol: 'circle',
      symbolSize: 5,
      lineStyle: { width: 2, color: '#667eea' },
      itemStyle: { color: '#667eea' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(102,126,234,0.25)' },
          { offset: 1, color: 'rgba(102,126,234,0.02)' }
        ])
      },
      emphasis: { focus: 'series' }
    }],
    title: labels.length === 0 ? {
      text: t('statistics.noData') || '暂无数据',
      left: 'center',
      top: 'center',
      textStyle: { color: '#999', fontSize: 14, fontWeight: 'normal' }
    } : undefined
  }
  freqChartInstance.setOption(option, true)
}

// ========== 来源IP统计 ==========

const fetchSourceIps = async () => {
  try {
    const response = await request.get('/statistics/source-ips', {
      params: { days: 7, granularity: ipGranularity.value }
    })
    if (response.code === 200 && response.data) {
      renderSourceIpChart(response.data)
    }
  } catch (error) {
    console.error('获取来源IP统计失败:', error)
  }
}

const renderSourceIpChart = (data) => {
  if (!sourceIpChart.value) return
  if (!ipChartInstance) {
    ipChartInstance = echarts.init(sourceIpChart.value)
  }

  const timeLabels = data.timeLabels || []
  const ipSeries = data.ipSeries || []
  const totalData = data.totalData || []

  const colorPalette = [
    '#667eea', '#f5576c', '#30cfd0', '#f093fb', '#4facfe',
    '#fa709a', '#fee140', '#43e97b', '#fa6400', '#6c5ce7',
    '#00cec9', '#fd79a8', '#636e72', '#e17055', '#0984e3'
  ]

  const series = ipSeries.map((item, index) => ({
    name: item.ip,
    type: 'line',
    data: item.data || [],
    smooth: true,
    symbol: 'circle',
    symbolSize: 5,
    lineStyle: { width: 2 },
    areaStyle: { opacity: 0.05 },
    emphasis: { focus: 'series' },
    itemStyle: {
      color: colorPalette[index % colorPalette.length]
    }
  }))

  // 汇总线
  series.push({
    name: t('statistics.totalCalls'),
    type: 'line',
    data: totalData,
    smooth: true,
    symbol: 'diamond',
    symbolSize: 8,
    lineStyle: { color: '#303133', width: 3, type: 'dashed' },
    itemStyle: { color: '#303133' },
    emphasis: { focus: 'self' }
  })

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter: (params) => {
        let result = `<strong>${params[0].axisValue}</strong><br/>`
        params.forEach(p => {
          result += `${p.marker} ${p.seriesName}: <strong>${p.value}</strong><br/>`
        })
        return result
      }
    },
    legend: {
      type: 'scroll',
      top: 0,
      data: [...ipSeries.map(item => item.ip), t('statistics.totalCalls')]
    },
    grid: { left: '3%', right: '4%', bottom: '12%', top: '45px', containLabel: true },
    xAxis: {
      type: 'category',
      data: timeLabels,
      axisLabel: { rotate: timeLabels.length > 7 ? 45 : 0, fontSize: 11 },
      boundaryGap: false
    },
    yAxis: { type: 'value', minInterval: 1, name: t('statistics.requestCount') },
    dataZoom: timeLabels.length > 12 ? [
      { type: 'slider', start: Math.max(0, ((timeLabels.length - 12) / timeLabels.length) * 100), end: 100, height: 18, bottom: 2 },
      { type: 'inside' }
    ] : [],
    series,
    title: timeLabels.length === 0 ? {
      text: t('statistics.noData') || '暂无数据',
      left: 'center',
      top: 'center',
      textStyle: { color: '#999', fontSize: 14, fontWeight: 'normal' }
    } : undefined
  }
  ipChartInstance.setOption(option, true)
}

// ========== AI 调用统计 ==========

const fetchAiCalls = async () => {
  try {
    const response = await request.get('/statistics/ai-calls', {
      params: { granularity: aiCallsGranularity.value }
    })
    if (response.code === 200 && response.data) {
      renderAiCallsChart(response.data)
    }
  } catch (error) {
    console.error('获取AI调用统计失败:', error)
  }
}

const renderAiCallsChart = (data) => {
  if (!aiCallsChart.value) return
  if (!aiCallsChartInstance) {
    aiCallsChartInstance = echarts.init(aiCallsChart.value)
  }

  const timeLabels = data.timeLabels || []
  const userSeries = data.userSeries || []
  const totalData = data.totalData || []

  // 为每位用户生成一条独立折线
  const colorPalette = [
    '#667eea', '#f5576c', '#30cfd0', '#f093fb', '#4facfe',
    '#fa709a', '#fee140', '#43e97b', '#fa6400', '#6c5ce7',
    '#00cec9', '#fd79a8', '#636e72', '#e17055', '#0984e3'
  ]

  const series = userSeries.map((user, index) => ({
    name: user.username,
    type: 'line',
    data: user.data || [],
    smooth: true,
    symbol: 'circle',
    symbolSize: 5,
    lineStyle: { width: 2 },
    areaStyle: { opacity: 0.05 },
    emphasis: { focus: 'series' },
    itemStyle: {
      color: colorPalette[index % colorPalette.length]
    }
  }))

  // 汇总线：更粗更深，菱形标记突出总览
  series.push({
    name: t('statistics.totalCalls'),
    type: 'line',
    data: totalData,
    smooth: true,
    symbol: 'diamond',
    symbolSize: 8,
    lineStyle: { color: '#303133', width: 3, type: 'dashed' },
    itemStyle: { color: '#303133' },
    emphasis: { focus: 'self' }
  })

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter: (params) => {
        let result = `<strong>${params[0].axisValue}</strong><br/>`
        params.forEach(p => {
          result += `${p.marker} ${p.seriesName}: <strong>${p.value}</strong><br/>`
        })
        return result
      }
    },
    legend: {
      type: 'scroll',
      top: 0,
      data: [...userSeries.map(u => u.username), t('statistics.totalCalls')]
    },
    grid: { left: '3%', right: '4%', bottom: '12%', top: '45px', containLabel: true },
    xAxis: {
      type: 'category',
      data: timeLabels,
      axisLabel: { rotate: timeLabels.length > 7 ? 45 : 0, fontSize: 11 },
      boundaryGap: false
    },
    yAxis: { type: 'value', minInterval: 1, name: t('statistics.callCount') },
    dataZoom: timeLabels.length > 12 ? [
      { type: 'slider', start: Math.max(0, ((timeLabels.length - 12) / timeLabels.length) * 100), end: 100, height: 18, bottom: 2 },
      { type: 'inside' }
    ] : [],
    series,
    title: timeLabels.length === 0 ? {
      text: t('statistics.noData') || '暂无数据',
      left: 'center',
      top: 'center',
      textStyle: { color: '#999', fontSize: 14, fontWeight: 'normal' }
    } : undefined
  }
  aiCallsChartInstance.setOption(option, true)
}

// ========== 新增趋势统计 ==========

const fetchCreationTrend = async () => {
  try {
    const response = await request.get('/statistics/creation-trend', {
      params: { days: 30, granularity: trendGranularity.value }
    })
    if (response.code === 200 && response.data) {
      renderCreationTrendChart(response.data)
    }
  } catch (error) {
    console.error('获取新增趋势统计失败:', error)
  }
}

const renderCreationTrendChart = (data) => {
  if (!creationTrendChart.value) return
  if (!trendChartInstance) {
    trendChartInstance = echarts.init(creationTrendChart.value)
  }

  const labels = data.labels || []
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter: (params) => {
        let result = `<strong>${params[0].axisValue}</strong><br/>`
        params.forEach(p => {
          result += `${p.marker} ${p.seriesName}: <strong>${p.value}</strong><br/>`
        })
        return result
      }
    },
    legend: { data: [t('statistics.newProjects'), t('statistics.newApis')], top: 0 },
    grid: { left: '3%', right: '4%', bottom: '12%', top: '45px', containLabel: true },
    xAxis: {
      type: 'category',
      data: labels,
      axisLabel: { rotate: labels.length > 12 ? 45 : 0, fontSize: 10, formatter: (val) => val.length > 7 ? val.substring(val.length - 7) : val },
      boundaryGap: false
    },
    yAxis: { type: 'value', minInterval: 1 },
    dataZoom: labels.length > 14 ? [
      { type: 'slider', start: Math.max(0, ((labels.length - 14) / labels.length) * 100), end: 100, height: 18, bottom: 2 },
      { type: 'inside' }
    ] : labels.length > 0 ? [{ type: 'inside' }] : [],
    series: [
      {
        name: t('statistics.newProjects'),
        type: 'line',
        data: data.projectValues || [],
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { color: '#667eea', width: 2 },
        itemStyle: { color: '#667eea' },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(102,126,234,0.25)' }, { offset: 1, color: 'rgba(102,126,234,0.02)' }]) }
      },
      {
        name: t('statistics.newApis'),
        type: 'line',
        data: data.apiValues || [],
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { color: '#f5576c', width: 2 },
        itemStyle: { color: '#f5576c' },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(245,87,108,0.25)' }, { offset: 1, color: 'rgba(245,87,108,0.02)' }]) }
      }
    ],
    title: labels.length === 0 ? {
      text: t('statistics.noData') || '暂无数据',
      left: 'center',
      top: 'center',
      textStyle: { color: '#999', fontSize: 14, fontWeight: 'normal' }
    } : undefined
  }
  trendChartInstance.setOption(option, true)
}

// ========== 生命周期 ==========

const handleResize = () => {
  iopsChartInstance?.resize()
  freqChartInstance?.resize()
  ipChartInstance?.resize()
  aiCallsChartInstance?.resize()
  trendChartInstance?.resize()
}

const loadAllStats = async () => {
  await Promise.all([fetchIops(), fetchRequestFrequency(), fetchSourceIps(), fetchAiCalls(), fetchCreationTrend()])
}

// IOPS 自动刷新定时器
let iopsAutoRefreshTimer = null
const IOPS_AUTO_REFRESH_INTERVAL = 5000 // 5秒

const startIopsAutoRefresh = () => {
  stopIopsAutoRefresh()
  iopsAutoRefreshTimer = setInterval(() => {
    fetchIops()
  }, IOPS_AUTO_REFRESH_INTERVAL)
}

const stopIopsAutoRefresh = () => {
  if (iopsAutoRefreshTimer) {
    clearInterval(iopsAutoRefreshTimer)
    iopsAutoRefreshTimer = null
  }
}

onMounted(async () => {
  await nextTick()
  loadAllStats()
  startIopsAutoRefresh()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  stopIopsAutoRefresh()
  iopsChartInstance?.dispose()
  freqChartInstance?.dispose()
  ipChartInstance?.dispose()
  aiCallsChartInstance?.dispose()
  trendChartInstance?.dispose()
})
</script>

<style scoped>
.statistics-page {
  padding: 20px;
}

.chart-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chart-container {
  width: 100%;
  height: 360px;
}

/* IOPS 概览卡片 */
.iops-card {
  text-align: center;
}
.iops-card-inner {
  padding: 8px 0;
}
.iops-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}
.iops-value {
  font-size: 32px;
  font-weight: 700;
  color: #303133;
  line-height: 1;
}
.iops-unit {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 4px;
}

@media (max-width: 768px) {
  .statistics-page {
    padding: 8px;
  }
  .chart-container {
    height: 240px;
  }
  .iops-value {
    font-size: 20px;
  }
  .iops-label {
    font-size: 11px;
  }
  .card-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}
</style>
