<template>
  <div class="credit-page">
    <div class="page-header">
      <h1 class="page-title">信用分管理</h1>
      <p class="page-desc">管理平台老板与零工的信用分，支持调整与查看信用分明细</p>
    </div>

    <div class="card filter-card">
      <div class="filter-bar">
        <el-input v-model="filters.keyword" clearable placeholder="搜索 ID / 姓名 / 手机号" prefix-icon="Search" style="width:220px" @keyup.enter="search" />
        <el-select v-model="filters.role" clearable placeholder="全部角色" style="width:125px" @change="search">
          <el-option label="全部角色" value="" />
          <el-option label="零工" value="worker" /><el-option label="老板" value="boss" />
        </el-select>
        <el-select v-model="filters.level" clearable placeholder="全部等级" style="width:135px" @change="search">
          <el-option label="优秀（≥90）" value="excellent" /><el-option label="良好（75-89）" value="good" />
          <el-option label="一般（60-74）" value="medium" /><el-option label="较差（<60）" value="low" />
        </el-select>
        <el-button type="primary" @click="search"><i class="fas fa-search"></i> 查询</el-button>
        <el-button @click="reset"><i class="fas fa-rotate-left"></i> 重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe class="credit-table">
        <el-table-column prop="id" label="ID" min-width="110" />
        <el-table-column label="角色" width="90"><template #default="{ row }"><span :class="['role-tag', row.role]">{{ row.role === 'worker' ? '零工' : '老板' }}</span></template></el-table-column>
        <el-table-column label="姓名 / 账号" min-width="180"><template #default="{ row }"><div class="name-cell"><span :class="['name-avatar', row.role]">{{ avatarText(row.name) }}</span><div class="name-info"><strong>{{ row.name }}</strong><small>{{ row.id }}</small></div></div></template></el-table-column>
        <el-table-column label="手机号" min-width="135"><template #default="{ row }"><span class="masked-phone">{{ maskPhone(row.phone) }}</span></template></el-table-column>
        <el-table-column label="当前信用分" min-width="130"><template #default="{ row }"><div class="score-cell"><b :class="`credit-${row.level}`">{{ row.score }}</b><span class="credit-bar"><i :class="`bar-${row.level}`" :style="{ width: `${Math.max(0, Math.min(100, row.score))}%` }"></i></span></div></template></el-table-column>
        <el-table-column label="信用等级" width="100"><template #default="{ row }"><span :class="['level-text', `credit-${row.level}`]">{{ levelText(row.level) }}</span></template></el-table-column>
        <el-table-column label="累计加分" width="100"><template #default="{ row }"><span class="num-in">+{{ row.addTotal }}</span></template></el-table-column>
        <el-table-column label="累计扣分" width="100"><template #default="{ row }"><span class="num-out">-{{ row.subTotal }}</span></template></el-table-column>
        <el-table-column label="操作" width="190" fixed="right" align="right" header-align="right"><template #default="{ row }"><div class="action-links"><el-button v-permission="'permUser.credits.detail'" link type="primary" @click="openDetail(row)"><i class="fas fa-list"></i> 明细</el-button><el-button v-permission="'permUser.credits.adjust'" link type="primary" @click="openAdjust(row)"><i class="fas fa-scale-balanced"></i> 调整</el-button></div></template></el-table-column>
      </el-table>
      <div class="pagination"><span>共 {{ total }} 条记录</span><el-pagination v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50]" :total="total" layout="sizes, prev, pager, next, jumper" background @size-change="load" @current-change="load" /></div>
    </div>

    <el-dialog v-model="adjustVisible" width="420px" class="credit-dialog adjust-dialog" destroy-on-close>
      <template #header><div class="dialog-title"><i class="fas fa-scale-balanced"></i> 调整信用分</div></template>
      <div v-if="current" class="adjust-form">
        <div class="adjust-row"><span class="adjust-label">对象</span><div class="target-line"><b>{{ current.name }}</b><span>{{ current.id }}</span></div></div>
        <div class="adjust-row"><span class="adjust-label">当前信用分</span><strong class="current-score">{{ current.score }}</strong></div>
        <div class="adjust-row"><span class="adjust-label">调整方向</span><div class="score-toggle"><button type="button" class="minus" :disabled="Number(current.score || 0) <= 0" :class="{ active: adjust.direction === 'out' }" @click="adjust.direction = 'out'"><i class="fas fa-minus"></i> 减分</button><button type="button" class="plus" :class="{ active: adjust.direction === 'in' }" @click="adjust.direction = 'in'"><i class="fas fa-plus"></i> 加分</button></div></div>
        <div class="adjust-row"><span class="adjust-label">调整分值</span><el-input-number v-model="adjust.amount" :min="1" :max="adjust.direction === 'out' ? Number(current.score || 0) : undefined" controls-position="right" class="score-input" /><span class="after-score">调整后：<b :class="adjust.direction === 'in' ? 'num-in' : 'num-out'">{{ afterScore }}</b> 分</span></div>
        <div class="adjust-row reason-row"><span class="adjust-label">调整原因</span><el-input v-model="adjust.reason" type="textarea" :rows="3" maxlength="200" show-word-limit resize="none" placeholder="选择或填写调整原因（将记录到信用分明细）" /></div>
      </div>
      <template #footer><el-button @click="adjustVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitAdjust">确认调整</el-button></template>
    </el-dialog>

    <el-dialog v-model="detailVisible" width="720px" class="credit-dialog detail-dialog">
      <template #header><div class="dialog-title"><i class="fas fa-list"></i> 信用分明细 · <span>{{ current?.name || '' }}</span></div></template>
      <div class="detail-stats"><span>当前信用分：<b>{{ current?.score ?? 0 }}</b></span><span>累计加分：<b class="num-in">+{{ current?.addTotal ?? detailStats.add }}</b></span><span>累计扣分：<b class="num-out">-{{ current?.subTotal ?? detailStats.sub }}</b></span><span>明细记录：<em>{{ flows.length }} 条</em></span></div>
      <el-table :data="flows" size="small" class="detail-table"><el-table-column prop="id" label="明细ID" width="105"><template #default="{ row }"><span class="flow-id">{{ row.flowNo || row.no || row.id }}</span></template></el-table-column><el-table-column label="类型" width="75"><template #default="{ row }"><span :class="['flow-type', row.delta >= 0 ? 'in' : 'out']">{{ row.delta >= 0 ? '加分' : '减分' }}</span></template></el-table-column><el-table-column label="分值变动" width="90"><template #default="{ row }"><span :class="row.delta >= 0 ? 'num-in' : 'num-out'">{{ row.delta >= 0 ? '+' : '' }}{{ row.delta }}</span></template></el-table-column><el-table-column prop="afterScore" label="变动后" width="80" /><el-table-column prop="ruleCode" label="触发规则" width="115" /><el-table-column prop="timestamp" label="时间" width="145" /><el-table-column prop="reason" label="备注" min-width="150" /></el-table>
      <el-pagination v-model:current-page="detailPage" :page-size="detailSize" :total="detailTotal" layout="prev, pager, next" background @current-change="loadDetailPage" />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adjustCredit, getCreditDetail, listCreditUsers } from '@/api/credit'

const rows = ref([]); const total = ref(0); const page = ref(1); const size = ref(10); const loading = ref(false)
const filters = reactive({ keyword: '', role: '', level: '' })
const adjustVisible = ref(false); const detailVisible = ref(false); const saving = ref(false); const current = ref(null); const flows = ref([]); const detailPage = ref(1); const detailSize = 10; const detailTotal = ref(0)
const adjust = reactive({ direction: 'in', amount: 5, reason: '' })
const afterScore = computed(() => Math.max(0, Number(current.value?.score || 0) + (adjust.direction === 'in' ? 1 : -1) * Number(adjust.amount || 0)))
const detailStats = computed(() => ({ add: flows.value.filter(f => f.delta > 0).reduce((n, f) => n + f.delta, 0), sub: flows.value.filter(f => f.delta < 0).reduce((n, f) => n + Math.abs(f.delta), 0) }))
const levelText = key => ({ excellent: '优秀', good: '良好', medium: '一般', low: '较差' }[key] || '-')
const avatarText = name => String(name || '?').trim().charAt(0) || '?'
const maskPhone = phone => {
  const value = String(phone || '').trim()
  if (!value || value.includes('*')) return value || '-'
  const digits = value.replace(/\D/g, '')
  if (digits.length >= 7) return `${digits.slice(0, 3)}****${digits.slice(-4)}`
  return value
}
async function load () { loading.value = true; try { const result = await listCreditUsers({ ...filters, page: page.value - 1, size: size.value }); const data = result?.data; const content = Array.isArray(data) ? data : (data?.content || data?.records || data?.list || []); rows.value = content.map(row => ({ ...row, score: Number(row.score ?? 0), addTotal: Number(row.addTotal ?? 0), subTotal: Number(row.subTotal ?? 0) })); total.value = result?.total ?? data?.totalElements ?? data?.total ?? content.length } catch (e) { rows.value = []; total.value = 0 } finally { loading.value = false } }
function search () { page.value = 1; load() }
function reset () { Object.assign(filters, { keyword: '', role: '', level: '' }); search() }
function openAdjust (row) { current.value = row; Object.assign(adjust, { direction: 'in', amount: 5, reason: '' }); adjustVisible.value = true }
async function submitAdjust () {
  if (!current.value || !adjust.amount) return
  const score = Number(current.value.score || 0)
  const amount = Number(adjust.amount)
  if (!Number.isInteger(amount) || amount < 1) { ElMessage.warning('调整分值必须是大于0的整数'); return }
  if (adjust.direction === 'out' && (score <= 0 || amount > score)) { ElMessage.warning(score <= 0 ? '当前信用分为0，不支持扣分' : '扣分不能超过当前信用分'); return }
  const reason = adjust.reason.trim()
  if (!reason) { ElMessage.warning('请填写调整原因'); return }
  if (reason.length > 200) { ElMessage.warning('调整原因不能超过 200 字'); return }
  saving.value = true
  try {
    await adjustCredit(current.value.id, { scoreType: current.value.scoreType, delta: adjust.direction === 'in' ? amount : -amount, reason, ruleCode: 'ADMIN_MANUAL_ADJUST' })
    ElMessage.success('信用分调整成功'); adjustVisible.value = false; await load()
  } catch (e) { ElMessage.error(e?.message || '信用分调整失败') } finally { saving.value = false }
}
async function loadDetailPage () { if (!current.value) return; try { const result = await getCreditDetail(current.value.id, { page: detailPage.value - 1, size: detailSize }); const data = result.data || {}; flows.value = data.creditFlows || []; detailTotal.value = Number(data.total ?? result.total ?? flows.value.length) } catch (e) { flows.value = []; detailTotal.value = 0; ElMessage.error('信用分明细加载失败') } }
async function openDetail (row) { current.value = row; detailPage.value = 1; detailTotal.value = 0; await loadDetailPage(); detailVisible.value = true }
onMounted(load)
</script>

<style scoped>
.filter-card{padding:20px}.filter-bar{display:flex;align-items:center;gap:10px;margin-bottom:16px}.credit-table :deep(th){background:#fafafa;color:#6b7280;font-weight:600}.credit-table :deep(td){height:58px}.role-tag{padding:3px 10px;border-radius:12px;font-size:12px}.role-tag.worker{color:#ff6b35;background:#fff1eb}.role-tag.boss{color:#2563eb;background:#eff6ff}.name-cell{display:flex;align-items:center;gap:10px}.name-avatar{display:flex;align-items:center;justify-content:center;width:32px;height:32px;flex-shrink:0;border-radius:50%;font-size:13px;font-weight:600}.name-avatar.worker{color:#2e7d32;background:#e8f5e9}.name-avatar.boss{color:#2563eb;background:#eff6ff}.name-info{display:flex;min-width:0;flex-direction:column;gap:3px}.name-info strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.name-info small{color:#9ca3af;font-family:monospace}.masked-phone{color:#6b7280;font-family:monospace}.action-links{display:flex;align-items:center;justify-content:flex-end;white-space:nowrap}.action-links :deep(.el-button){gap:5px;margin-left:12px;color:var(--primary)}.action-links :deep(.el-button:first-child){margin-left:0}.score-cell{display:flex;flex-direction:column;gap:4px}.score-cell b{font-size:15px}.credit-excellent{color:#f59e0b}.credit-good{color:#059669}.credit-medium{color:#6b7280}.credit-low{color:#dc2626}.credit-bar{width:80px;height:6px;background:#e5e7eb;border-radius:3px;overflow:hidden}.credit-bar i{display:block;height:100%;border-radius:3px}.bar-excellent{background:#f59e0b}.bar-good{background:#059669}.bar-medium{background:#6b7280}.bar-low{background:#dc2626}.level-text{font-weight:600}.num-in{color:#059669;font-weight:600}.num-out{color:#dc2626;font-weight:600}.pagination{display:flex;align-items:center;justify-content:space-between;margin-top:16px;color:#6b7280;font-size:13px}.target-line{display:flex;gap:12px;align-items:baseline}.target-line span{color:#9ca3af;font-family:monospace}.current-score{color:#ff6b35;font-size:16px;font-weight:600}.after-score{margin-left:16px;color:#6b7280;font-size:13px}.detail-stats{display:flex;gap:28px;padding:14px 18px;margin-bottom:16px;border-radius:8px;background:#fafafa;color:#6b7280;font-size:13px}.detail-stats b{color:#ff6b35;font-size:15px}.detail-stats em{font-style:normal;color:#6b7280}.dialog-title{display:flex;align-items:center;gap:7px;color:#111827;font-size:16px;font-weight:600}.dialog-title i{color:var(--primary)}.adjust-dialog :deep(.el-dialog__header),.detail-dialog :deep(.el-dialog__header){padding:16px 20px;margin-right:0;border-bottom:1px solid #f3f4f6}.credit-dialog :deep(.el-dialog__body){padding:20px}.credit-dialog :deep(.el-dialog__footer){padding:14px 20px;border-top:1px solid #f3f4f6}.adjust-row{display:flex;align-items:center;min-height:40px;margin-bottom:16px}.adjust-row.reason-row{align-items:flex-start}.adjust-label{width:90px;flex-shrink:0;color:#374151;font-size:13px}.target-line{display:flex;align-items:baseline;gap:12px;font-size:14px}.target-line span{color:#9ca3af;font-family:monospace}.score-toggle{display:flex;width:220px;gap:0;padding:3px;border-radius:8px;background:#f3f4f6}.score-toggle button{display:flex;align-items:center;justify-content:center;flex:1;gap:5px;padding:8px 0;border:0;border-radius:6px;background:transparent;color:#6b7280;font-size:13px;cursor:pointer}.score-toggle button.minus.active{color:#dc2626;background:#fef2f2;box-shadow:inset 0 0 0 1px #fecaca;font-weight:600}.score-toggle button.plus.active{color:#059669;background:#ecfdf5;box-shadow:inset 0 0 0 1px #a7f3d0;font-weight:600}.score-input{width:100px}.reason-row :deep(.el-textarea){flex:1}.detail-table :deep(th){color:#6b7280;background:#fafafa;font-weight:500}.detail-table :deep(td),.detail-table :deep(th){padding:10px 12px}.flow-id{color:var(--primary);font-family:monospace}.flow-type{display:inline-block;padding:3px 8px;border-radius:6px;font-size:12px}.flow-type.in{color:#059669;background:#ecfdf5}.flow-type.out{color:#dc2626;background:#fef2f2}
</style>
