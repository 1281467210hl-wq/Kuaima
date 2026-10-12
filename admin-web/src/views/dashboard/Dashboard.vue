<template>
  <div>
    <!-- 页面头部 -->
    <div class="page-header">
      <h1 class="page-title">数据概览</h1>
      <p class="page-desc">实时监控平台核心运营数据，掌握业务动态趋势</p>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div v-for="card in statCards" :key="card.title" class="stat-card">
        <div class="stat-card-header">
          <span class="stat-card-title">{{ card.title }}</span>
          <div :class="['stat-card-icon', card.iconClass]">
            <i :class="['fas', card.icon]"></i>
          </div>
        </div>
        <div class="stat-card-value">{{ card.value }}</div>
        <div :class="['stat-card-change', card.up ? 'up' : 'down']">
          <i :class="['fas', card.up ? 'fa-arrow-up' : 'fa-arrow-down']"></i>
          <span>{{ card.compare }} {{ card.change }}</span>
        </div>
      </div>
    </div>

    <!-- 业务入口卡片 -->
    <div class="entry-cards">
      <router-link to="/admin/employees" class="entry-card">
        <div class="entry-card-icon">
          <i class="fas fa-users"></i>
        </div>
        <div class="entry-card-info">
          <div class="entry-card-title">员工管理</div>
          <div class="entry-card-desc">在册 {{ empStats.total }} 人 · 在职 {{ empStats.active }} 人</div>
        </div>
        <div class="entry-card-action">
          <i class="fas fa-chevron-right"></i>
        </div>
      </router-link>

      <router-link to="/admin/project-salary" class="entry-card">
        <div class="entry-card-icon payroll">
          <i class="fas fa-money-bill-wave"></i>
        </div>
        <div class="entry-card-info">
          <div class="entry-card-title">发薪管理</div>
          <div class="entry-card-desc">待审批 {{ payrollStats.pendingCount }} 笔 · 本月已发 {{ payrollStats.monthCount }} 笔</div>
        </div>
        <div class="entry-card-action">
          <i class="fas fa-chevron-right"></i>
        </div>
      </router-link>
    </div>

    <!-- 图表区域 -->
    <div class="content-grid-2col">
      <!-- 订单趋势 -->
      <div class="card">
        <div class="card-header">
          <span class="card-title">订单趋势</span>
          <el-select v-model="trendRange" size="small" style="width: 100px;">
            <el-option label="近7天" value="7d" />
            <el-option label="近30天" value="30d" />
            <el-option label="本月" value="month" />
          </el-select>
        </div>
        <div ref="trendChart" class="chart-container"></div>
      </div>

      <!-- 工种分布 -->
      <div class="card">
        <div class="card-header">
          <span class="card-title">工种分布</span>
          <router-link to="/admin/jobs" class="card-action">查看详情</router-link>
        </div>
        <div class="donut-wrapper">
          <div ref="donutChart" class="donut-chart"></div>
          <div class="chart-legend">
            <div v-for="item in jobTypeData" :key="item.name" class="chart-legend-item">
              <div class="legend-left">
                <span class="legend-dot" :style="{ background: item.color }"></span>
                <span>{{ item.name }}</span>
              </div>
              <span class="legend-value">{{ item.value }}%</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 实时订单表格 -->
    <div class="card">
      <div class="card-header">
        <span class="card-title">实时订单</span>
        <div style="display: flex; gap: 12px; align-items: center;">
          <el-button link type="primary" :underline="false" @click="loadRecentOrders">刷新</el-button>
          <router-link to="/admin/orders" class="card-action">
            查看全部 <i class="fas fa-arrow-right" style="font-size:11px; margin-left:4px;"></i>
          </router-link>
        </div>
      </div>
      
      <el-table :data="recentOrders" stripe style="width: 100%" :header-cell-style="{ background: '#F9FAFB', color: '#6B7280', fontWeight: 500 }">
        <el-table-column prop="id" label="订单号" show-overflow-tooltip>
          <template #default="{ row }">
            <span style="color: var(--primary); font-family: monospace;">{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="employer" label="雇主" show-overflow-tooltip />
        <el-table-column prop="job" label="工种" show-overflow-tooltip />
        <el-table-column prop="worker" label="零工" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="worker-cell">
              <span class="mini-avatar" :style="{ background: row.avatarColor }">{{ row.avatarLetter }}</span>
              <span>{{ row.worker }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" show-overflow-tooltip>
          <template #default="{ row }">
            <span style="font-weight: 600;">{{ row.amount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="['status-badge', row.statusClass]">{{ row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="time" label="时间" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default>
            <el-button link type="primary" size="small">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <div class="pagination-info">共 {{ recentOrdersTotal }} 条订单记录</div>
        <el-pagination
          background
          layout="prev, pager, next"
          :total="1856"
          :page-size="10"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { getStats, getTrend, getDistribution, getEmployeeStats, getPayrollStats } from '@/api/dashboard'
import { listOrders } from '@/api/order'

// statCards：后端接口填充 value，其余字段保留原展示元数据
// 后端 Dashboard 字段：workerTotal, bossTotal, orderTotal, settledTotal, pendingAudit
const statCards = reactive([
  { title: '零工总数', value: '-', compare: '较上月', change: '+12.5%', up: true, icon: 'fa-user-friends', iconClass: '' },
  { title: '雇主总数', value: '-', compare: '较上月', change: '+8.3%', up: true, icon: 'fa-building', iconClass: 'blue' },
  { title: '今日订单', value: '-', compare: '较昨日', change: '+23.1%', up: true, icon: 'fa-clipboard-check', iconClass: 'green' },
  { title: '累计营收', value: '¥0', compare: '较上月', change: '+5.2%', up: true, icon: 'fa-coins', iconClass: 'yellow' }
])

// ============ 员工管理统计（首页入口卡片） ============
const empStats = reactive({ total: 0, active: 0 })
const loadEmployeeStats = async () => {
  try {
    const result = await getEmployeeStats()
    const d = result.data || {}
    empStats.total = d.total ?? 0
    empStats.active = d.active ?? 0
  } catch (e) {
    console.warn('[Dashboard] 员工统计加载失败:', e)
  }
}

// ============ 发薪管理统计（首页入口卡片） ============
// 后端字段：pendingCount(待审批) / monthCount(本月已发) / monthAmount(本月金额) / projectTotal(项目数)
const payrollStats = reactive({ pendingCount: 0, monthCount: 0, monthAmount: 0, projectTotal: 0 })
const loadPayrollStats = async () => {
  try {
    const result = await getPayrollStats()
    const d = result.data || {}
    payrollStats.pendingCount = d.pendingCount ?? 0
    payrollStats.monthCount = d.monthCount ?? 0
    payrollStats.monthAmount = d.monthAmount ?? 0
    payrollStats.projectTotal = d.projectTotal ?? 0
  } catch (e) {
    console.warn('[Dashboard] 发薪统计加载失败:', e)
  }
}

// 趋势和工种分布数据（从后端加载）
const orderTrendData = reactive({ labels: ['周一','周二','周三','周四','周五','周六','周日'], values: [0,0,0,0,0,0,0] })
const jobTypeData = ref([
  { name: '日结', value: 0, color: '#FF6B35' },
  { name: '压薪日结', value: 0, color: '#2563EB' },
  { name: '月结', value: 0, color: '#10B981' },
  { name: '其他', value: 0, color: '#F59E0B' }
])
const recentOrders = ref([])
const recentOrdersTotal = ref(0)

const trendRange = ref('7d')
const trendChart = ref(null)
const donutChart = ref(null)

let trendChartInstance = null
let donutChartInstance = null

// 加载统计卡片真实数据
const loadStats = async () => {
  try {
    const result = await getStats()
    const d = result.data || {}
    statCards[0].value = formatNumber(d.workerTotal)      // 零工总数
    statCards[1].value = formatNumber(d.bossTotal)        // 雇主总数
    statCards[2].value = formatNumber(d.orderTotal)       // 今日订单
    statCards[3].value = '¥' + formatNumber(d.totalRevenue ?? 0)  // 累计营收
  } catch (e) {
    console.warn('[Dashboard] 加载统计数据失败:', e)
  }
}

// 加载订单趋势
const loadTrend = async () => {
  try {
    const result = await getTrend()
    const d = result.data || {}
    if (d.labels && d.values) {
      orderTrendData.labels = d.labels
      orderTrendData.values = d.values
    }
  } catch (e) {
    console.warn('[Dashboard] 加载趋势数据失败:', e)
  }
}

// 加载工种分布
const loadDistribution = async () => {
  try {
    const result = await getDistribution()
    const d = result.data || []
    if (Array.isArray(d) && d.length > 0) {
      jobTypeData.value = d
    }
  } catch (e) {
    console.warn('[Dashboard] 加载工种分布失败:', e)
  }
}

// 加载最近订单
const loadRecentOrders = async () => {
  try {
    const result = await listOrders({ page: 0, size: 8 })
    const d = result.data || []
    const list = Array.isArray(d) ? d : (d?.content || d?.list || [])
    recentOrdersTotal.value = result.total ?? d?.totalElements ?? d?.total ?? list.length
    const palette = ['#FF6B35', '#2563EB', '#10B981', '#8B5CF6', '#F59E0B', '#EC4899', '#06B6D4', '#64748B']
    recentOrders.value = list.map((o, i) => {
      const employer = o.employerName || '未知'
      const worker = o.workerName || '-'
      const job = o.orderTitle || o.postion || '-'
      const amountValue = o.amount ?? o.salary
      const amount = amountValue != null ? `¥${Number(amountValue).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : '-'
      const status = o.status || o.orderStatus || '-'
      const statusLabel = { 待结算: '已完工', 取消招工: '已取消', 取消报名: '已取消' }[status] || status
      const statusClass = { '已完成': 'success', '已报名': 'default', '已录用': 'purple', '已到岗': 'info', '待结算': 'warning', '已结算': 'teal', '招工中': 'info', '待审核': 'warning', '审核拒绝': 'danger', '取消招工': 'default', '取消报名': 'default' }[status] || 'danger'
      const time = formatDateTimeSeconds(o.timestamp || o.applyDate)
      const letter = worker.charAt(0) || '?'
      return {
        id: o.orderNumber || o.parentOrderId || o.orderId || o.id || '-',
        employer,
        job,
        worker,
        amount,
        status: statusLabel,
        statusClass,
        time,
        avatarColor: `linear-gradient(135deg, ${palette[i % palette.length]}, ${palette[(i+1) % palette.length]})`,
        avatarLetter: letter
      }
    })
  } catch (e) {
    console.warn('[Dashboard] 加载最近订单失败:', e)
  }
}

const formatNumber = (n) => {
  if (n == null || n === undefined || Number.isNaN(Number(n))) return '-'
  return Number(n).toLocaleString()
}

// 实时订单时间统一显示完整的年月日时分秒。
const formatDateTimeSeconds = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  const pad = (number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const initTrendChart = () => {
  if (!trendChart.value) return
  
  trendChartInstance = echarts.init(trendChart.value)
  const option = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(0,0,0,0.8)',
      borderWidth: 0,
      textStyle: { color: '#fff', fontSize: 12 }
    },
    grid: {
      left: '10%',
      right: '5%',
      top: '5%',
      bottom: '15%'
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: orderTrendData.labels,
      axisLine: { lineStyle: { color: '#E5E7EB' } },
      axisLabel: { color: '#9CA3AF', fontSize: 12 }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#F3F4F6' } },
      axisLabel: { color: '#9CA3AF', fontSize: 12 }
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: { color: '#FF6B35', width: 2.5 },
        itemStyle: { color: '#FF6B35', borderColor: '#fff', borderWidth: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(255,107,53,0.35)' },
            { offset: 1, color: 'rgba(255,107,53,0)' }
          ])
        },
        label: {
          show: true,
          position: 'top',
          color: '#6B7280',
          fontSize: 11,
          fontWeight: 500
        },
        data: orderTrendData.values
      }
    ]
  }
  trendChartInstance.setOption(option)
}

const initDonutChart = () => {
  if (!donutChart.value) return
  
  donutChartInstance = echarts.init(donutChart.value)
  const option = {
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(0,0,0,0.8)',
      borderWidth: 0,
      textStyle: { color: '#fff', fontSize: 12 },
      formatter: '{b}: {c}%'
    },
    series: [
      {
        type: 'pie',
        radius: ['55%', '75%'],
        center: ['50%', '50%'],
        avoidLabelOverlap: false,
        label: { show: false },
        labelLine: { show: false },
        itemStyle: {
          borderColor: '#fff',
          borderWidth: 2
        },
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0,0,0,0.2)'
          }
        },
        data: jobTypeData.value
      }
    ]
  }
  donutChartInstance.setOption(option)
}

const handleResize = () => {
  trendChartInstance?.resize()
  donutChartInstance?.resize()
}

onMounted(async () => {
  initTrendChart()
  initDonutChart()
  window.addEventListener('resize', handleResize)
  // 加载真实数据
  await Promise.all([loadStats(), loadTrend(), loadDistribution(), loadRecentOrders(), loadEmployeeStats(), loadPayrollStats()])
  // 数据加载后刷新图表
  if (trendChartInstance) {
    trendChartInstance.setOption({ xAxis: { data: orderTrendData.labels }, series: [{ data: orderTrendData.values }] })
  }
  if (donutChartInstance) {
    donutChartInstance.setOption({ series: [{ data: jobTypeData.value }] })
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChartInstance?.dispose()
  donutChartInstance?.dispose()
})
</script>

<style scoped>
.chart-container {
  height: 280px;
}

.donut-wrapper {
  display: flex;
  align-items: center;
  justify-content: space-around;
  gap: 20px;
  padding: 10px 0;
}

.donut-chart {
  width: 200px;
  height: 200px;
}

.chart-legend {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
}

.chart-legend-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
}

.legend-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
}

.legend-value {
  font-weight: 600;
  color: var(--text-primary);
}

.mini-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  margin-right: 8px;
}

.worker-cell {
  display: flex;
  align-items: center;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  
  .pagination-info {
    font-size: 13px;
    color: var(--text-secondary);
  }
}

/* ============ 业务入口卡片 ============ */
.entry-cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.entry-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 12px;
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  transition: all 0.2s ease;
}

.entry-card:hover {
  border-color: var(--primary);
  box-shadow: 0 4px 16px rgba(255, 107, 53, 0.1);
  transform: translateY(-2px);
}

.entry-card-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: linear-gradient(135deg, #FFF0EB 0%, #FFE8DC 100%);
  color: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.entry-card-icon.payroll {
  background: linear-gradient(135deg, #ECFDF5 0%, #D1FAE5 100%);
  color: var(--success);
}

.entry-card-info {
  flex: 1;
  min-width: 0;
}

.entry-card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.entry-card-desc {
  font-size: 13px;
  color: var(--text-secondary);
}

.entry-card-action {
  color: var(--text-muted);
  font-size: 14px;
}
</style>
