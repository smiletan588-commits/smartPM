<template>
  <AppShell>
    <main class="analytics-main" v-loading="loading">
      <PageHeader eyebrow="数据分析" title="数据大屏">
        <template #actions><el-button :loading="loading" @click="fetchOverview">刷新数据</el-button></template>
      </PageHeader>

      <section class="analytics-filters">
        <el-select v-model="filters.projectId" clearable placeholder="全部项目" @change="fetchOverview">
          <el-option v-for="item in projects" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-date-picker v-model="filters.dates" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" :clearable="false" @change="fetchOverview" />
        <span>最多查看 366 天，当前样本 {{ overview.completionSampleSize || 0 }} 项</span>
      </section>

      <section class="metrics-band" aria-label="项目核心指标">
        <button v-for="metric in metrics" :key="metric.label" class="metric-item" @click="drillDown(metric.scope)">
          <span>{{ metric.label }}</span><strong>{{ metric.value }}</strong><small>{{ metric.note }}</small>
        </button>
      </section>

      <section class="ai-metrics" aria-label="AI 效果指标">
        <header><span>AI 效果评估</span><small>数据来自真实调用与用户评价，不保存项目正文</small></header>
        <div><article><strong>{{ aiOverview.totalCalls ? `${aiOverview.successRate}%` : '暂无数据' }}</strong><span>调用成功率 · {{ aiOverview.totalCalls || 0 }} 次</span></article><article><strong>{{ aiOverview.durationSampleCount ? formatDuration(aiOverview.averageDurationMs) : '暂无数据' }}</strong><span>平均响应 · {{ aiOverview.durationSampleCount || 0 }} 次</span></article><article><strong>{{ aiOverview.adoptionSampleCount ? `${aiOverview.adoptionRate}%` : '暂无数据' }}</strong><span>结果采纳率 · {{ aiOverview.adoptionSampleCount || 0 }} 次</span></article><article><strong>{{ aiOverview.ratingSampleCount ? `${aiOverview.averageRating}/5` : '暂无数据' }}</strong><span>平均评分 · {{ aiOverview.ratingSampleCount || 0 }} 次</span></article><article><strong>{{ aiOverview.aiGeneratedTaskRate || 0 }}%</strong><span>AI 任务占比</span></article></div>
      </section>

      <section class="chart-grid">
        <article class="chart-panel chart-panel-main">
          <header><div><span>交付趋势</span><h2>{{ filters.dates[0] }} 至 {{ filters.dates[1] }}</h2></div><small>按完成日期统计</small></header>
          <div ref="lineChartRef" class="chart-box chart-box-main" />
        </article>
        <article class="chart-panel">
          <header><div><span>任务结构</span><h2>任务状态占比</h2></div></header>
          <div ref="pieChartRef" class="chart-box" />
        </article>
        <article class="chart-panel">
          <header><div><span>项目对比</span><h2>项目任务量排行</h2></div></header>
          <div ref="barChartRef" class="chart-box" />
        </article>
      </section>
    </main>
  </AppShell>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getAiOverview } from '@/api/ai'
import { listProjects } from '@/api/project'
import request from '@/utils/request'
import AppShell from '@/components/AppShell.vue'
import PageHeader from '@/components/PageHeader.vue'

const loading = ref(false)
const router = useRouter()
const projects = ref([])
const endDate = new Date()
const startDate = new Date(); startDate.setDate(endDate.getDate() - 29)
const localDate = value => `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`
const filters = ref({ projectId: null, dates: [localDate(startDate), localDate(endDate)] })
const overview = ref({ totalProjects: 0, totalTasks: 0, completedTasks: 0, inProgressTasks: 0, statusDistribution: [], projectTaskRanking: [], dailyCompletedTrend: [] })
const aiOverview = ref({ totalCalls: 0, successRate: 0, averageDurationMs: 0, adoptionRate: 0, averageRating: 0, aiGeneratedTaskRate: 0 })
echarts.use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])
const pieChartRef = ref(null)
const barChartRef = ref(null)
const lineChartRef = ref(null)
let pieChart
let barChart
let lineChart

const todayCompleted = computed(() => {
  const trend = overview.value.dailyCompletedTrend
  return trend.length ? trend[trend.length - 1].count : 0
})
const todoCount = computed(() => overview.value.statusDistribution.find(item => item.status === 'TODO')?.count || 0)
const metrics = computed(() => [
  { label: '项目总数', value: overview.value.totalProjects, note: '当前参与项目', scope: 'ALL' },
  { label: '任务总数', value: overview.value.totalTasks, note: '所选范围任务', scope: 'ALL' },
  { label: '按时完成率', value: formatMetric(overview.value.onTimeCompletionRate, '%'), note: `${overview.value.completionSampleSize || 0} 项有效样本`, scope: 'ALL' },
  { label: '平均延期', value: formatMetric(overview.value.averageDelayDays, ' 天'), note: '只统计有截止日的完成任务', scope: 'OVERDUE' },
  { label: '平均周期', value: formatMetric(overview.value.averageCycleHours, ' 小时'), note: '创建至完成', scope: 'DONE' },
  { label: '今日完成', value: todayCompleted.value, note: '本日交付', scope: 'DONE' },
  { label: '待办任务', value: todoCount.value, note: '等待处理', scope: 'MY' }
])

async function fetchOverview() {
  loading.value = true
  try {
    const params = { projectId: filters.value.projectId || undefined, from: filters.value.dates?.[0], to: filters.value.dates?.[1] }
    const [res, aiRes] = await Promise.all([request.get('/analytics/overview', { params }), getAiOverview(params).catch(() => null)])
    overview.value = res.data?.data || overview.value
    aiOverview.value = aiRes?.data?.data || aiOverview.value
    await nextTick()
    updateCharts()
  } finally { loading.value = false }
}

const axisText = { color: '#778296', fontSize: 11, fontFamily: 'system-ui, sans-serif' }
function updateCharts() {
  if (!pieChartRef.value || !barChartRef.value || !lineChartRef.value) return
  pieChart ||= echarts.init(pieChartRef.value)
  barChart ||= echarts.init(barChartRef.value)
  lineChart ||= echarts.init(lineChartRef.value)

  pieChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: axisText },
    color: ['#b76e14', '#315ee7', '#287a58'],
    series: [{ type: 'pie', radius: ['56%', '76%'], center: ['50%', '45%'], itemStyle: { borderRadius: 3, borderColor: '#fbfcfe', borderWidth: 3 }, label: { show: false }, emphasis: { scaleSize: 4 }, data: (overview.value.statusDistribution || []).map(item => ({ name: statusLabel(item.status), value: item.count })) }]
  })

  const ranking = overview.value.projectTaskRanking || []
  barChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 16, top: 8, bottom: 4, containLabel: true },
    xAxis: { type: 'value', axisLine: { show: false }, axisTick: { show: false }, axisLabel: axisText, splitLine: { lineStyle: { color: '#e4e8ef' } } },
    yAxis: { type: 'category', inverse: true, axisLine: { show: false }, axisTick: { show: false }, axisLabel: { ...axisText, formatter: value => value.length > 8 ? `${value.slice(0, 8)}...` : value }, data: ranking.map(item => item.projectName) },
    series: [{ type: 'bar', barWidth: 13, itemStyle: { borderRadius: [0, 4, 4, 0], color: '#315ee7' }, data: ranking.map(item => item.taskCount) }]
  })

  const trend = overview.value.dailyCompletedTrend || []
  lineChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 10, right: 18, top: 20, bottom: 4, containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, axisLine: { lineStyle: { color: '#dbe1ea' } }, axisTick: { show: false }, axisLabel: { ...axisText, formatter: value => value.slice(5) }, data: trend.map(item => item.date) },
    yAxis: { type: 'value', minInterval: 1, axisLine: { show: false }, axisTick: { show: false }, axisLabel: axisText, splitLine: { lineStyle: { color: '#e4e8ef' } } },
    series: [{ type: 'line', smooth: true, symbol: 'circle', symbolSize: 7, lineStyle: { color: '#315ee7', width: 2 }, itemStyle: { color: '#315ee7', borderColor: '#fff', borderWidth: 2 }, areaStyle: { color: 'rgba(49,94,231,.08)' }, data: trend.map(item => item.count) }]
  })
}

function statusLabel(status) { return { TODO: '待办', IN_PROGRESS: '进行中', DONE: '已完成' }[status] || status }
function formatDuration(value) { return value >= 1000 ? `${(value / 1000).toFixed(1)}s` : `${value || 0}ms` }
function formatMetric(value, suffix) { return value === null || value === undefined ? '暂无数据' : `${Number(value).toFixed(1)}${suffix}` }
function drillDown(scope) {
  if (filters.value.projectId) router.push({ path: `/project/${filters.value.projectId}`, query: scope === 'ALL' ? {} : { scope } })
  else router.push({ path: '/dashboard', query: scope === 'ALL' ? {} : { scope } })
}
function handleResize() { pieChart?.resize(); barChart?.resize(); lineChart?.resize() }
onMounted(async () => {
  try { projects.value = (await listProjects()).data?.data || [] } catch { projects.value = [] }
  await fetchOverview(); window.addEventListener('resize', handleResize)
})
onUnmounted(() => { window.removeEventListener('resize', handleResize); pieChart?.dispose(); barChart?.dispose(); lineChart?.dispose() })
</script>

<style scoped>
.analytics-main { max-width: 1320px; margin: 0 auto; padding: 34px 36px 52px; }
.analytics-filters { display:flex; align-items:center; flex-wrap:wrap; gap:10px; margin:22px 0 14px; padding:14px; border:1px solid var(--border-light); border-radius:12px; background:var(--surface); }.analytics-filters>.el-select { width:200px; }.analytics-filters>span { margin-left:auto; color:var(--text-tertiary); font-size:11px; }
.metrics-band { display: grid; grid-template-columns: repeat(7, 1fr); margin: 14px 0 24px; border: 1px solid var(--border); border-radius: 12px; background: var(--surface); box-shadow: var(--shadow-xs); }
.metric-item { min-width: 0; padding: 18px 16px; border:0; color:inherit; background:transparent; text-align:left; cursor:pointer; }
.metric-item:hover { background:var(--brand-soft); }
.metric-item + .metric-item { border-left: 1px solid var(--border-light); }
.metric-item span,.metric-item small { display: block; color: var(--text-tertiary); }
.metric-item span { font-size: 12px; font-weight: 650; }
.metric-item strong { display: block; margin: 7px 0 3px; color: var(--text-primary); font-size: 30px; line-height: 1; font-variant-numeric: tabular-nums; }
.metric-item small { font-size: 11px; }
.ai-metrics { margin:0 0 18px; padding:18px 20px; border:1px solid #b9c8f5; border-radius:12px; background:linear-gradient(115deg,var(--brand-light),var(--surface) 62%); }.ai-metrics header { display:flex; justify-content:space-between; gap:12px; margin-bottom:14px; }.ai-metrics header span { color:var(--brand-deep); font-size:12px; font-weight:700; }.ai-metrics header small { color:var(--text-tertiary); }.ai-metrics>div { display:grid; grid-template-columns:repeat(5,1fr); }.ai-metrics article { padding:2px 16px; border-left:1px solid rgba(49,94,231,.14); }.ai-metrics article:first-child { padding-left:0; border-left:0; }.ai-metrics strong,.ai-metrics span { display:block; }.ai-metrics strong { color:var(--text-primary); font:700 21px/1.2 var(--font-mono); }.ai-metrics article span { margin-top:4px; color:var(--text-tertiary); font-size:10px; }
.chart-grid { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(300px, .65fr); gap: 18px; }
.chart-panel { min-width: 0; padding: 20px; border: 1px solid var(--border); border-radius: 12px; background: var(--surface); box-shadow: var(--shadow-xs); }
.chart-panel-main { grid-row: span 2; }
.chart-panel header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.chart-panel header span,.chart-panel header small { color: var(--text-tertiary); font-size: 11px; font-weight: 650; }
.chart-panel h2 { margin: 4px 0 0; color: var(--text-primary); font-size: 16px; }
.chart-box { width: 100%; height: 240px; }
.chart-box-main { height: 534px; }
@media (max-width: 1180px) { .metrics-band { grid-template-columns:repeat(4,1fr); }.metric-item:nth-child(5) { border-left:0; border-top:1px solid var(--border-light); } }
@media (max-width: 1024px) { .analytics-main { padding: 28px 24px 44px; }.chart-grid { grid-template-columns: 1fr 1fr; }.chart-panel-main { grid-column: 1 / -1; grid-row: auto; }.chart-box-main { height: 340px; } }
@media (max-width: 767px) { .analytics-main { padding: 22px 16px 36px; }.analytics-filters>* { width:100%!important; }.analytics-filters>span { margin-left:0; }.metrics-band { grid-template-columns: 1fr 1fr; }.metric-item { border-top: 1px solid var(--border-light); }.metric-item:nth-child(odd) { border-left:0; }.metric-item:nth-child(-n+2) { border-top:0; }.metric-item { padding: 16px; }.metric-item strong { font-size: 25px; }.ai-metrics header { flex-direction:column; }.ai-metrics>div { grid-template-columns:repeat(2,1fr); gap:14px 0; }.ai-metrics article:nth-child(odd) { padding-left:0; border-left:0; }.chart-grid { grid-template-columns: 1fr; }.chart-panel-main { grid-column: auto; }.chart-box,.chart-box-main { height: 280px; } }
</style>
